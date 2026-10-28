package com.xiaopiao.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.parse.ParsedEntry
import com.xiaopiao.app.domain.parse.QuickParser
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

private const val NO_AMOUNT_HINT = "没听清金额，补一个数字就行"

/**
 * 「一句话」 entry. The sentence is parsed locally by [QuickParser] and shown as confirm cards;
 * the ledger write only happens when the user taps 「确认记账」 inside [ConfirmEntries].
 */
@Composable
internal fun TextRecordPane(
    prefill: String?,
    onConfirm: (List<ParsedEntry>) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(prefill.orEmpty()) }
    var parsed by remember { mutableStateOf<List<ParsedEntry>>(emptyList()) }
    var hint by remember { mutableStateOf<String?>(null) }
    val haptics = rememberXpHaptics()

    fun submit() {
        if (input.isBlank()) return
        val result = QuickParser.parse(input, System.currentTimeMillis())
        if (result.isEmpty()) {
            parsed = emptyList()
            hint = NO_AMOUNT_HINT
        } else {
            parsed = result
            hint = null
            haptics.select()
        }
    }

    // A recognized sentence arrives through openText(prefill); parse it right away so the user
    // lands on the same confirm cards as a typed sentence.
    LaunchedEffect(prefill) {
        if (!prefill.isNullOrBlank()) {
            input = prefill
            submit()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = XpSpace.l, end = XpSpace.l, bottom = XpSpace.xl),
        verticalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = XpSpace.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("一句话记账", style = MaterialTheme.typography.headlineSmall, color = XpColors.Ink)
            Pill("一句话")
        }

        Text(
            "把一句话拆成账单，确认后才入账",
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
        )

        TextField(
            value = input,
            onValueChange = {
                input = it.take(200)
                if (parsed.isNotEmpty() || hint != null) {
                    parsed = emptyList()
                    hint = null
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("比如：午饭32 骑车2", color = XpColors.InkTertiary) },
            shape = RoundedCornerShape(XpRadius.m),
            colors = recordFieldColors(),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            trailingIcon = {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (input.isNotBlank()) XpColors.Primary else XpColors.Fill)
                        .pressableClick(enabled = input.isNotBlank()) { submit() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ArrowUpward,
                        contentDescription = "解析",
                        tint = if (input.isNotBlank()) XpColors.OnPrimary else XpColors.InkTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        }

        if (parsed.isNotEmpty()) {
            ConfirmEntries(parsed = parsed, onConfirm = onConfirm)
        }
    }
}
