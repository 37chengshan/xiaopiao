package com.xiaopiao.app.ui.games.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.tr

/**
 * 结果是一刻，不是一行字：大字结果 + 小字次数，然后才是两颗按钮。
 *
 * 「去掉它再转」只对**下一次**生效（那一转里它没资格被抽中），转完自动恢复，
 * 所以盘面几何不会在结果展示时忽然变掉。
 */
@Composable
internal fun WheelResultCard(
    text: String,
    spinNumber: Int,
    excluded: String?,
    canExclude: Boolean,
    onSpinAgain: () -> Unit,
    onExclude: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.xl))
            .background(XpColors.Card)
            .padding(XpSpace.page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            tr("转到", "Landed on"),
            style = MaterialTheme.typography.labelMedium,
            color = XpColors.InkSecondary,
        )
        Spacer(Modifier.height(XpSpace.xs))
        Text(
            text,
            style = MaterialTheme.typography.headlineLarge,
            color = XpColors.Accent,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(XpSpace.xs))
        Text(
            tr("第 $spinNumber 次 · 刚刚", "Spin $spinNumber · just now"),
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkTertiary,
        )

        Spacer(Modifier.height(XpSpace.l))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            XpButton(
                text = tr("再转一次", "Spin again"),
                onClick = onSpinAgain,
                modifier = Modifier.weight(1f),
                style = XpButtonStyle.Secondary,
                size = XpButtonSize.Small,
            )
            XpButton(
                text = tr("去掉它再转", "Skip it and spin"),
                onClick = onExclude,
                enabled = canExclude,
                modifier = Modifier.weight(1f),
                style = XpButtonStyle.Secondary,
                size = XpButtonSize.Small,
            )
        }

        if (excluded != null) {
            Spacer(Modifier.height(XpSpace.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("这次去掉了「$excluded」", "\u201C$excluded\u201D is out for this spin"),
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkSecondary,
                )
                Text(
                    tr("恢复", "Restore"),
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(XpRadius.pill))
                        .pressableClick(haptic = false, onClick = onRestore)
                        .padding(horizontal = XpSpace.s, vertical = 4.dp),
                )
            }
        }
    }
}

/** 最近 5 次结果：小胶囊，最新的在最左。没有广告、没有解释。 */
@Composable
internal fun WheelHistoryRow(entries: List<WheelHistoryEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(XpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.take(5).forEach { entry ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(XpRadius.pill))
                    .background(XpColors.Fill.copy(alpha = 0.7f))
                    .padding(horizontal = XpSpace.s, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(XpColors.InkTertiary))
                Text(
                    shortLabel(entry.text),
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
