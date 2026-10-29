package com.xiaopiao.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

private val FieldShape = RoundedCornerShape(XpRadius.pill)

/** Editable capsule used inside the search page; focuses itself on entry. */
@Composable
fun LedgerSearchField(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Row(
        modifier.fillMaxWidth().height(40.dp).clip(FieldShape).background(XpColors.Fill).padding(start = XpSpace.m, end = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = XpColors.InkTertiary, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = XpColors.Ink),
            cursorBrush = SolidColor(XpColors.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.weight(1f).padding(start = XpSpace.s).focusRequester(focus),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("搜索商户、备注、金额", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkTertiary)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(XpRadius.pill)).clickable { onQuery("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "清空输入", tint = XpColors.InkTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Empty-query state of the search page: the last few queries as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedgerRecentSearches(
    recent: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recent.isEmpty()) return
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = XpSpace.l, bottom = XpSpace.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("最近搜索", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            Text(
                "清除",
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.Accent,
                modifier = Modifier.clip(FieldShape).clickable(onClick = onClear).padding(horizontal = XpSpace.s, vertical = 2.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            recent.forEach { term -> LedgerChip(term, selected = false) { onPick(term) } }
        }
    }
}

/** One line when nothing matches, with the escape hatch on the same line (§3.2). */
@Composable
fun LedgerNoResults(onClearConditions: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = XpSpace.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text("没有匹配的账单", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
        Text(
            "　清空条件",
            style = MaterialTheme.typography.bodyMedium,
            color = XpColors.Accent,
            modifier = Modifier.clickable(onClick = onClearConditions),
        )
    }
}
