package com.xiaopiao.app.ui.budget

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.BudgetMath
import com.xiaopiao.app.domain.BudgetPeriod
import com.xiaopiao.app.domain.BudgetSettings
import com.xiaopiao.app.domain.BudgetStatus
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpFonts
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.record.RecordAmount
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(XpRadius.contentCard)
private val PillShape = RoundedCornerShape(XpRadius.pill)

private val BigNumber = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 26.sp, lineHeight = 32.sp,
    fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum",
)

private val Money = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 15.sp, fontFeatureSettings = "tnum",
)

/**
 * 预算页（tt#70）：三个周期各一张卡 + 分类月预算。数字全部是「已花 / 额度 / 剩余」的算术，
 * 不出现任何「建议少花」类文案。
 */
@Composable
fun BudgetPage(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    /** tt: opened from the ledger it is a bottom drawer, not a page — no nav bar, its own title row. */
    inSheet: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = remember { System.currentTimeMillis() }
    val settingsFlow = remember(container) { container.budget.observeSettings() }
    val settings: BudgetSettings by settingsFlow.collectAsStateWithLifecycle(initialValue = BudgetSettings())
    val txns: List<Txn> by container.ledger.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<BudgetEditTarget?>(null) }

    val statuses = remember(settings, txns, now) { BudgetMath.statuses(settings, txns, now) }
    val categories = remember(settings, txns, now) { BudgetMath.categoryBudgets(settings, txns, now) }

    val body: @Composable (Modifier) -> Unit = { frame ->
        Column(
            frame.verticalScroll(rememberScrollState())
                .padding(horizontal = XpSpace.page)
                .padding(top = XpSpace.m, bottom = if (inSheet) XpSpace.xl else 120.dp),
            verticalArrangement = Arrangement.spacedBy(XpSpace.cardGap),
        ) {
            Text(
                "额度由你自己定，这里只记录和提醒。",
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
            )
            BudgetPeriod.entries.forEach { period ->
                val status = statuses.firstOrNull { it.period == period }
                BudgetCard(
                    period = period,
                    status = status,
                    onEdit = { editing = BudgetEditTarget.ForPeriod(period, settings.limitOf(period)) },
                )
            }
            CategoryBudgetSection(
                budgets = categories,
                onAdd = { editing = BudgetEditTarget.ForCategory(null, 0L) },
                onEdit = { editing = BudgetEditTarget.ForCategory(it.category, it.limit) },
            )
        }
    }
    if (inSheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                "预算", style = MaterialTheme.typography.titleLarge, color = XpColors.Ink,
                modifier = Modifier.padding(horizontal = XpSpace.page),
            )
            body(Modifier.fillMaxWidth())
        }
    } else {
        NavScaffold(
            "预算",
            NavScrollNone,
            onBack = onBack,
            backLabel = "返回",
            trailing = { NavIconButton(Icons.Outlined.Settings, "设置", onClick = onOpenSettings) },
        ) { top -> body(Modifier.fillMaxSize().padding(top = top)) }
    }

    val target = editing
    if (target != null) {
        BudgetEditSheet(
            target = target,
            existingCategories = settings.categoryLimits.keys,
            onDismiss = { editing = null },
            onSave = { cents, category ->
                scope.launch {
                    when (target) {
                        is BudgetEditTarget.ForPeriod -> container.budget.setLimit(target.period, cents)
                        is BudgetEditTarget.ForCategory ->
                            (category ?: target.category)?.let { container.budget.setCategoryLimit(it, cents) }
                    }
                    editing = null
                }
            },
            onClear = { category ->
                scope.launch {
                    when (target) {
                        is BudgetEditTarget.ForPeriod -> container.budget.setLimit(target.period, null)
                        is BudgetEditTarget.ForCategory ->
                            (category ?: target.category)?.let { container.budget.setCategoryLimit(it, null) }
                    }
                    editing = null
                }
            },
        )
    }
}

/** 编辑目标：某个周期，或某个（可能还没选的）分类。 */
sealed interface BudgetEditTarget {
    data class ForPeriod(val period: BudgetPeriod, val current: Cents?) : BudgetEditTarget

    /** 嵌套类不能叫 Category，否则会把 domain 的 Category 遮蔽掉。 */
    data class ForCategory(val category: com.xiaopiao.app.domain.Category?, val current: Cents) : BudgetEditTarget
}

@Composable
private fun BudgetCard(period: BudgetPeriod, status: BudgetStatus?, onEdit: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(XpColors.Card)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${period.label}预算", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, modifier = Modifier.weight(1f))
            Text(
                if (status == null) "设置" else "编辑",
                style = MaterialTheme.typography.bodyMedium,
                color = XpColors.Accent,
                modifier = Modifier.clip(PillShape).pressableClick(haptic = false, onClick = onEdit)
                    .padding(horizontal = XpSpace.s, vertical = XpSpace.xs),
            )
        }
        if (status == null) {
            Text(
                "还没有设${period.noun}预算",
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = XpSpace.xs, bottom = 2.dp),
            )
            return@Column
        }
        Row(Modifier.padding(top = XpSpace.s), verticalAlignment = Alignment.Bottom) {
            Text("已花 ¥${status.spent.yuan()}", style = BigNumber, color = XpColors.Ink, maxLines = 1, modifier = Modifier.weight(1f))
            Text("预算 ¥${status.limit.yuan()}", style = Money, color = XpColors.InkSecondary, maxLines = 1)
        }
        BudgetBar(status.ratio, Modifier.padding(top = XpSpace.s))
        Row(Modifier.padding(top = XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
            val over = status.remaining < 0
            Text(
                if (over) "已超出 ¥${(-status.remaining).yuan()}" else "剩余 ¥${status.remaining.yuan()}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) XpColors.Negative else XpColors.Ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(XpSpace.s))
            Text(
                "还剩 ${status.daysLeft} 天 · 每天还可花 ¥${status.perDayLeft.yuan()}",
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * 进度条：未到 80% 用 accent，≥80% 用 warning，超了用 negative。宽度按比例裁切，动画只动 fraction。
 */
@Composable
private fun BudgetBar(ratio: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 8.dp) {
    val target = ratio.coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, tween(320), label = "budgetBar")
    val color = when {
        ratio > 1f -> XpColors.Negative
        ratio >= 0.8f -> XpColors.Warning
        else -> XpColors.Accent
    }
    Box(modifier.fillMaxWidth().height(height).clip(PillShape).background(XpColors.Fill)) {
        if (animated > 0f) {
            Box(Modifier.fillMaxWidth(animated).height(height).clip(PillShape).background(color))
        }
    }
}

@Composable
private fun CategoryBudgetSection(
    budgets: List<com.xiaopiao.app.domain.CategoryBudget>,
    onAdd: () -> Unit,
    onEdit: (com.xiaopiao.app.domain.CategoryBudget) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(XpColors.Card).padding(vertical = XpSpace.m)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l), verticalAlignment = Alignment.CenterVertically) {
            Text("分类预算", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, modifier = Modifier.weight(1f))
            Text(
                "添加",
                style = MaterialTheme.typography.bodyMedium,
                color = XpColors.Accent,
                modifier = Modifier.clip(PillShape).pressableClick(haptic = false, onClick = onAdd)
                    .padding(horizontal = XpSpace.s, vertical = XpSpace.xs),
            )
        }
        if (budgets.isEmpty()) {
            Text(
                "给某个分类单独设一个月度额度（可选）",
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s),
            )
            return@Column
        }
        budgets.forEach { budget ->
            Row(
                Modifier.fillMaxWidth().pressableClick(haptic = false) { onEdit(budget) }
                    .padding(horizontal = XpSpace.l, vertical = XpSpace.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(budget.category.label, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(
                            "¥${budget.spent.yuan()} / ¥${budget.limit.yuan()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = XpColors.InkSecondary,
                            maxLines = 1,
                        )
                    }
                    BudgetBar(budget.ratio, Modifier.padding(top = 6.dp), height = 4.dp)
                }
            }
        }
    }
}

/**
 * 编辑 sheet：大金额 + 数字键盘。键盘的输入规则直接复用记一笔的 [RecordAmount]（同一套「两位小数、整数最多 7 位」），
 * 只是版式按 sheet 重排——记一笔的 Keypad 是 ui/record 的私有组件，无法直接引。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditSheet(
    target: BudgetEditTarget,
    existingCategories: Set<Category>,
    onDismiss: () -> Unit,
    onSave: (Cents, Category?) -> Unit,
    onClear: (Category?) -> Unit,
) {
    var text by remember(target) {
        mutableStateOf(
            when (target) {
                is BudgetEditTarget.ForPeriod -> target.current?.let { centsToText(it) } ?: ""
                is BudgetEditTarget.ForCategory -> if (target.current > 0) centsToText(target.current) else ""
            },
        )
    }
    var category by remember(target) { mutableStateOf((target as? BudgetEditTarget.ForCategory)?.category) }
    val title = when (target) {
        is BudgetEditTarget.ForPeriod -> "${target.period.label}预算"
        is BudgetEditTarget.ForCategory -> "分类月预算"
    }
    val canSave = RecordAmount.toCents(text) > 0 && (target !is BudgetEditTarget.ForCategory || category != null)

    XpSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = XpSpace.s))
            if (target is BudgetEditTarget.ForCategory) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(bottom = XpSpace.s),
                    horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                    verticalArrangement = Arrangement.spacedBy(XpSpace.s),
                ) {
                    Category.entries.forEach { c ->
                        val used = c in existingCategories && c != (target as? BudgetEditTarget.ForCategory)?.category
                        ChoiceChip(c.label, selected = c == category, enabled = !used) { category = c }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(vertical = XpSpace.s)) {
                Text("¥", style = MaterialTheme.typography.titleLarge, color = XpColors.InkSecondary)
                Spacer(Modifier.width(XpSpace.xs))
                Text(
                    RecordAmount.display(text),
                    style = TextStyle(
                        fontFamily = XpFonts.sans, fontSize = 34.sp, lineHeight = 40.sp,
                        fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum",
                    ),
                    color = XpColors.Ink,
                    maxLines = 1,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(XpColors.Hairline))
            BudgetKeypad(Modifier.padding(top = XpSpace.s)) { key -> text = RecordAmount.press(text, key) }
            Row(Modifier.fillMaxWidth().padding(top = XpSpace.m), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                XpButton("清除", { onClear(category) }, Modifier.weight(1f), style = XpButtonStyle.Secondary, enabled = true)
                XpButton("保存", { onSave(RecordAmount.toCents(text), category) }, Modifier.weight(1f), enabled = canSave)
            }
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).clip(PillShape)
            .background(if (selected) XpColors.AccentSoft else XpColors.Fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = XpSpace.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = when {
                selected -> XpColors.Accent
                enabled -> XpColors.Ink
                else -> XpColors.InkTertiary
            },
            maxLines = 1,
        )
    }
}

/** 「1250」→「12.5」、「125000」→「1250」，与键盘输入的两位小数规则一致。 */
private fun centsToText(cents: Cents): String =
    if (cents % 100 == 0L) (cents / 100).toString() else "%d.%02d".format(cents / 100, cents % 100).trimEnd('0')

/** 3 列数字键盘：1–9 / . 0 ⌫。按键 52dp，与记一笔的键盘同一套触感。 */
@Composable
private fun BudgetKeypad(modifier: Modifier = Modifier, onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", RecordAmount.BACKSPACE),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
                row.forEach { key ->
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(XpRadius.m))
                            .pressableClick(haptic = false) { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            key,
                            style = TextStyle(fontFamily = XpFonts.sans, fontSize = 24.sp, fontWeight = FontWeight.Medium),
                            color = XpColors.Ink,
                        )
                    }
                }
            }
        }
    }
}
