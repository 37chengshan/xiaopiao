@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.xiaopiao.app.ui.circle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.R
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.domain.yuanGrouped
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.SelectCapsule
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.Calendar

/**
 * 排行榜（docs/APP-DESIGN.md §5.2）：自然周、四个榜、样本门槛 3 人。
 *
 * Privacy rules are structural, not copy: spend boards only contain members who share 今日总额 (the
 * domain's `leaderboard` filters them), values are week totals, and the only per-person detail line is a
 * count, a streak length or badge titles — never a merchant or a single purchase.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleLeaderboardSheet(
    book: CircleBook,
    now: Long,
    viewerId: String = CircleDemo.MY_ID,
    onClose: () -> Unit,
    onInvite: (() -> Unit)? = null,
) {
    XpSheet(onDismiss = onClose) {
        CircleLeaderboardContent(
            book = book,
            now = now,
            viewerId = viewerId,
            onClose = onClose,
            onBack = null,
            onInvite = onInvite,
        )
    }
}

@Composable
internal fun CircleLeaderboardContent(
    book: CircleBook,
    now: Long,
    viewerId: String,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onInvite: (() -> Unit)?,
) {
    val circle = book.current()
    var type by remember { mutableStateOf(LeaderboardType.LeastSpent) }
    val board = remember(circle, type, now, viewerId) { leaderboard(circle, type, now, viewerId) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val reduced = rememberReducedMotion()
    val enter = if (reduced) EnterTransition.None else expandVertically() + fadeIn()
    val exit = if (reduced) ExitTransition.None else shrinkVertically() + fadeOut()

    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XpSpace.page)
            .padding(bottom = XpSpace.l),
    ) {
        CircleSheetHeader(
            title = stringResource(R.string.circle_lb_title),
            subtitle = weekCaption(now),
            onBack = onBack,
        )

        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
            verticalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            LeaderboardType.entries.forEach { option ->
                SelectCapsule(
                    text = leaderboardTabLabel(option),
                    selected = option == type,
                    onClick = {
                        type = option
                        expandedId = null
                    },
                )
            }
        }
        Spacer(Modifier.height(XpSpace.m))

        if (!board.visible) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(XpRadius.m))
                    .background(XpColors.Surface2)
                    .padding(XpSpace.l),
            ) {
                Text(
                    stringResource(R.string.circle_lb_gate, board.eligible),
                    style = MaterialTheme.typography.bodyMedium,
                    color = XpColors.InkSecondary,
                )
                Spacer(Modifier.height(XpSpace.m))
                XpButton(
                    text = stringResource(R.string.circle_lb_invite),
                    onClick = { onInvite?.invoke() },
                    enabled = onInvite != null,
                    style = XpButtonStyle.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // 拟物榜单 (tt 2026-10-29): the board is a printed receipt — torn bottom, dashed rules,
            // monospaced figures and a rubber stamp on the leader.
            Box(Modifier.fillMaxWidth()) {
                TornReceiptCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.circle_lb_stub),
                            style = MaterialTheme.typography.labelSmall,
                            color = XpColors.Ink,
                            letterSpacing = 1.5.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(XpColors.Hairline))
                    board.rows.forEachIndexed { index, row ->
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(XpColors.Hairline.copy(alpha = 0.6f)))
                        CircleRankRow(
                            circle = circle,
                            row = row,
                            type = type,
                            now = now,
                            expanded = expandedId == row.memberId,
                            enter = enter,
                            exit = exit,
                            onToggle = {
                                expandedId = if (expandedId == row.memberId) null else row.memberId
                            },
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(XpColors.Hairline))
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(
                            stringResource(R.string.circle_lb_total, board.eligible),
                            style = MaterialTheme.typography.labelSmall,
                            color = XpColors.InkTertiary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.circle_lb_scope),
                            style = MaterialTheme.typography.labelSmall,
                            color = XpColors.InkTertiary,
                        )
                    }
                }
                if (board.rows.isNotEmpty()) {
                    Text(
                        "TOP",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                        color = XpColors.Stamp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = XpSpace.m, end = XpSpace.m)
                            .rotate(-9f)
                            .border(1.5.dp, XpColors.Stamp, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            if (board.unshared > 0) {
                CircleNote(stringResource(R.string.circle_lb_unshared, board.unshared))
            }
            if (board.rows.all { it.value == 0L }) {
                CircleNote(stringResource(R.string.circle_lb_empty_week))
            }
        }

        CircleNote(stringResource(R.string.circle_lb_footer))
        Spacer(Modifier.height(XpSpace.l))
        XpButton(
            text = stringResource(R.string.circle_sheet_done),
            onClick = onClose,
            style = XpButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(XpSpace.s))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CircleRankRow(
    circle: Circle,
    row: LeaderboardRow,
    type: LeaderboardType,
    now: Long,
    expanded: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    onToggle: () -> Unit,
) {
    val member = circle.member(row.memberId)
    val spendBoard = type == LeaderboardType.LeastSpent || type == LeaderboardType.MostSpent
    val badges = if (row.badges.isNotEmpty()) row.badges else badgesFor(circle, row.memberId, now)
    val detail = when (type) {
        LeaderboardType.LeastSpent, LeaderboardType.MostSpent -> {
            stringResource(R.string.circle_lb_count, row.count)
        }
        LeaderboardType.Streak -> {
            stringResource(R.string.circle_lb_best_streak, longestStreakDays(circle, row.memberId))
        }
        LeaderboardType.Badges -> {
            val labels = badges.map { "${it.emoji} ${badgeLabel(it)}" }
            labels.joinToString(" · ")
        }
    }

    Column(
        Modifier.fillMaxWidth()
            .background(if (row.isMe) XpColors.AccentSoft else androidx.compose.ui.graphics.Color.Transparent),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .pressableClick(scaleDown = 0.98f, haptic = true, onClick = onToggle)
                .padding(start = XpSpace.m, end = XpSpace.s, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.rank.toString(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (row.rank <= 3) XpColors.Accent else XpColors.InkTertiary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            if (member != null) {
                CircleAvatar(member, size = 28.dp)
            } else {
                RankFallbackAvatar(row.name, size = 28.dp)
            }
            Spacer(Modifier.width(XpSpace.s))
            Column(Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = XpColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val extras = buildList {
                    if (row.isMe) add(stringResource(R.string.circle_home_me))
                    if (row.dormant && spendBoard) add(stringResource(R.string.circle_lb_dormant))
                    if (!row.shared) add(stringResource(R.string.circle_home_shared_none))
                }
                Text(
                    buildString {
                        if (extras.isNotEmpty()) append(extras.joinToString(" · ")).append(" · ")
                        append(detail)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            when (type) {
                LeaderboardType.LeastSpent, LeaderboardType.MostSpent ->
                    MoneyText(
                        amount = row.value.yuanGrouped(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = XpColors.Ink,
                    )
                LeaderboardType.Streak ->
                    Text(
                        stringResource(R.string.circle_lb_streak_unit, row.value.toInt()),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = XpColors.Ink,
                        maxLines = 1,
                    )
                LeaderboardType.Badges ->
                    Text(
                        stringResource(R.string.circle_lb_badge_unit, row.value.toInt()),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = XpColors.Ink,
                        maxLines = 1,
                    )
            }
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = XpColors.InkTertiary,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }

        AnimatedVisibility(visible = expanded, enter = enter, exit = exit) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(start = XpSpace.l, end = XpSpace.l, bottom = XpSpace.m),
            ) {
                if (badges.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                        verticalArrangement = Arrangement.spacedBy(XpSpace.s),
                    ) {
                        badges.forEach { badge ->
                            Row(
                                Modifier.clip(RoundedCornerShape(XpRadius.pill))
                                    .background(XpColors.Fill)
                                    .padding(horizontal = XpSpace.s + 2.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(badge.emoji, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(XpSpace.xs + 1.dp))
                                Text(
                                    badgeLabel(badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = XpColors.Ink,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.circle_lb_badge_caption),
                        style = MaterialTheme.typography.labelSmall,
                        color = XpColors.InkTertiary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RankFallbackAvatar(name: String, size: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(XpColors.Fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1),
            style = MaterialTheme.typography.labelLarge,
            color = XpColors.InkSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun leaderboardTabLabel(type: LeaderboardType): String = stringResource(
    when (type) {
        LeaderboardType.LeastSpent -> R.string.circle_lb_least
        LeaderboardType.MostSpent -> R.string.circle_lb_most
        LeaderboardType.Streak -> R.string.circle_lb_streak
        LeaderboardType.Badges -> R.string.circle_lb_badges
    },
)

/** "10/26–11/1 · 第 43 周" — built here from [weekStart] so English reads "week 43" instead of the domain's Chinese label. */
@Composable
private fun weekCaption(now: Long): String {
    val start = remember(now) { weekStart(now) }
    val range = remember(start) { "${monthDay(start)}–${monthDay(start + 6 * DAY_MS)}" }
    val week = remember(start) {
        Calendar.getInstance().apply { timeInMillis = start }.get(Calendar.WEEK_OF_YEAR).toString()
    }
    return stringResource(R.string.circle_lb_week, range, week)
}

private fun monthDay(at: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = at }
    return "${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}"
}

/** Longest run of consecutive days with at least one receipt; display-only, current streak comes from the domain. */
private fun longestStreakDays(circle: Circle, memberId: String): Int {
    val days = circle.posts.asSequence()
        .filterIsInstance<ReceiptPost>()
        .filter { it.authorId == memberId }
        .map { startOfDay(it.at) }
        .distinct()
        .sorted()
        .toList()
    var best = 0
    var run = 0
    var previous: Long? = null
    for (day in days) {
        run = if (previous != null && day - previous == DAY_MS) run + 1 else 1
        if (run > best) best = run
        previous = day
    }
    return best
}
