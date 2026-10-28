package com.xiaopiao.app.ui.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpPaletteOverride
import com.xiaopiao.app.ui.theme.merchantPalette
import com.xiaopiao.app.ui.theme.paletteFor

/**
 * 小店版 is a whole-product mode (docs/APP-DESIGN.md §6): its own palette, its own bottom navigation,
 * its own landing page. Entered from 我的, left through 回到用户版.
 */
@Composable
fun MerchantShell(container: AppContainer, onExit: () -> Unit) {
    val palette = remember(XpColors.style, XpColors.isDark) { merchantPalette(paletteFor(XpColors.style, XpColors.isDark)) }
    XpPaletteOverride(palette) {
        val routeState = remember { mutableStateOf(MerchantRoute.Landing) }
        Column(Modifier.fillMaxSize().background(XpColors.Canvas)) {
            Box(Modifier.weight(1f)) {
                MerchantScreen(container, onExit = onExit, routeState = routeState)
            }
            MerchantNavBar(routeState.value) { routeState.value = it }
        }
    }
}

private enum class MTab(val label: String, val icon: ImageVector, val route: MerchantRoute) {
    Home("概览", Icons.Outlined.Storefront, MerchantRoute.Landing),
    Insights("洞察", Icons.Outlined.Insights, MerchantRoute.Insights),
    Setup("发起实验", Icons.Outlined.Science, MerchantRoute.Setup),
    Results("结果", Icons.Outlined.TaskAlt, MerchantRoute.Results),
    Mine("我的", Icons.Outlined.Person, MerchantRoute.Decide),
}

@Composable
private fun MerchantNavBar(current: MerchantRoute, onSelect: (MerchantRoute) -> Unit) {
    if (current == MerchantRoute.Game) return
    val capsule = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().padding(top = 16.dp).height(64.dp)
                .then(if (XpColors.isDark) Modifier else Modifier.shadow(24.dp, capsule, ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000)))
                .clip(capsule)
                .background(if (XpColors.isDark) XpColors.Surface2.copy(alpha = 0.94f) else XpColors.Card.copy(alpha = 0.94f)),
        )
        val primary = MTab.Setup
        Row(Modifier.fillMaxWidth().padding(top = 16.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                MTab.entries.take(2).forEach { NavItem(it, current, Modifier.weight(1f), onSelect) }
            }
            Spacer(Modifier.size(78.dp))
            Row(Modifier.weight(1f)) {
                MTab.entries.drop(3).forEach { NavItem(it, current, Modifier.weight(1f), onSelect) }
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).size(66.dp)
                .background(XpColors.CanvasGrouped, CircleShape).padding(4.dp)
                .clip(CircleShape).background(XpColors.Primary)
                .pressableClick { onSelect(primary.route) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(primary.icon, contentDescription = primary.label, tint = XpColors.OnPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun NavItem(tab: MTab, current: MerchantRoute, modifier: Modifier, onSelect: (MerchantRoute) -> Unit) {
    val selected = current == tab.route
    val color = if (selected) XpColors.Accent else XpColors.InkSecondary
    Column(
        modifier.fillMaxHeight().pressableClick(haptic = false, scaleDown = 0.94f) { onSelect(tab.route) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
        Text(tab.label, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
