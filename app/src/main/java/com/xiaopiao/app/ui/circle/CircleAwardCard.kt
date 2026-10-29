@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.R
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 周报颁奖典礼卡 (docs/APP-DESIGN.md §5.2/§5.3): one card in the feed that hands every member their
 * weekly title. Titles come from [weeklyTitle], so they are stable for the whole week and never
 * depend on how much anyone spent — the card is a label, not a ranking.
 */
@Composable
fun CircleAwardCard(
    circle: Circle,
    now: Long,
    viewerId: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
) {
    val awards = circle.members.map { member ->
        member to weeklyTitle(circle, member, now)
    }
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.l))
            .background(XpColors.Card)
            .pressableClick(scaleDown = 0.99f, onClick = onOpen)
            .padding(XpSpace.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(XpColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) { Text("🏆", style = MaterialTheme.typography.titleSmall) }
            Column(Modifier.weight(1f).padding(start = XpSpace.s + 2.dp)) {
                Text(
                    stringResource(R.string.circle_home_award_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = XpColors.Ink,
                )
                Text(
                    weekWindowLabel(now),
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkTertiary,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth().padding(top = XpSpace.m),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
            verticalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            awards.forEach { (member, badge) ->
                AwardChip(
                    initial = member.initial,
                    name = member.displayName(viewerId, circle.ownerId),
                    badge = badge,
                    isMe = member.id == viewerId,
                )
            }
        }
        Text(
            stringResource(R.string.circle_home_award_sub),
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkTertiary,
            modifier = Modifier.padding(top = XpSpace.s + 2.dp),
        )
    }
}

@Composable
private fun AwardChip(initial: String, name: String, badge: CircleBadge, isMe: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(XpRadius.pill))
            .background(if (isMe) XpColors.AccentSoft else XpColors.Fill)
            .padding(horizontal = XpSpace.s + 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(XpColors.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
        }
        Column(Modifier.padding(start = 6.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${badge.emoji} ${badge.title}",
                style = MaterialTheme.typography.labelMedium,
                color = XpColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(2.dp))
    }
}
