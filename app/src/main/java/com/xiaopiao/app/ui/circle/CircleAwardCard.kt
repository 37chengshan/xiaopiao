@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaopiao.app.ui.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.R
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 周报颁奖 (docs/APP-DESIGN.md §5.2): one quiet line in the feed — trophy, “本周颁奖 · N 枚头衔” —
 * and the full list lives in a bottom sheet (tt 2026-10-29: 压成一行，点开抽屉).
 */
@Composable
fun CircleAwardCard(
    circle: Circle,
    now: Long,
    viewerId: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.l))
            .background(XpColors.Card)
            .pressableClick(scaleDown = 0.98f, haptic = true, onClick = onOpen)
            .padding(horizontal = XpSpace.m, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(XpColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = XpColors.Ink, modifier = Modifier.size(16.dp)) }
        Text(
            stringResource(R.string.circle_home_award_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = XpColors.Ink,
            modifier = Modifier.padding(start = XpSpace.s + 2.dp),
        )
        Text(
            stringResource(R.string.circle_home_award_line, circle.members.size),
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkSecondary,
            modifier = Modifier.padding(start = XpSpace.s),
        )
        Spacer(Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleMedium, color = XpColors.InkTertiary)
    }
}

@Composable
fun CircleAwardSheet(circle: Circle, now: Long, viewerId: String, onClose: () -> Unit) {
    XpSheet(onDismiss = onClose) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l),
        ) {
            Text(
                stringResource(R.string.circle_home_award_title),
                style = MaterialTheme.typography.titleLarge,
                color = XpColors.Ink,
            )
            Text(
                weekWindowLabel(now),
                style = MaterialTheme.typography.labelMedium,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.size(XpSpace.m))
            TornReceiptCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.circle_home_award_stub),
                        style = MaterialTheme.typography.labelSmall,
                        color = XpColors.InkTertiary,
                        modifier = Modifier.padding(start = XpSpace.s),
                    )
                }
                Spacer(Modifier.size(XpSpace.s))
                circle.members.forEach { member ->
                    val badge = weeklyTitle(circle, member, now)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                    ) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(XpColors.Fill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(member.initial, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                member.displayName(viewerId, circle.ownerId),
                                style = MaterialTheme.typography.bodyMedium,
                                color = XpColors.Ink,
                            )
                            Text(
                                "${badge.emoji} ${badge.title}",
                                style = MaterialTheme.typography.labelMedium,
                                color = XpColors.InkSecondary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(XpSpace.s))
            Text(
                stringResource(R.string.circle_home_award_sub),
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(horizontal = XpSpace.xs),
            )
            Spacer(Modifier.size(XpSpace.m))
            XpButton(
                text = stringResource(R.string.circle_sheet_done),
                onClick = onClose,
                style = XpButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
