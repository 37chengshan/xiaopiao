package com.xiaopiao.app.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.CategoryDefaults
import com.xiaopiao.app.domain.CategoryDef
import com.xiaopiao.app.domain.CategoryHue
import com.xiaopiao.app.domain.CategoryRepository
import com.xiaopiao.app.ui.theme.Contrast
import com.xiaopiao.app.ui.theme.XpColors

/** The category colour for the active light/dark palette (12/13 hues, both ≥3:1 on their own 12% tint). */
@Composable
fun categoryColor(hue: String): Color {
    val h = CategoryHue.of(hue)
    return Color(if (XpColors.isDark) h.dark else h.light)
}

/** 12% of the category colour — the tile background, instead of a generic grey fill. */
@Composable
fun categoryTint(hue: String): Color = categoryColor(hue).copy(alpha = 0.12f)

/** Icon + name resolution for the whole app: collect the defs once per screen. */
@Composable
fun rememberCategoryDefs(repository: CategoryRepository): List<CategoryDef> {
    val flow = remember(repository) { repository.observeDefs() }
    val defs by flow.collectAsState(initial = CategoryDefaults.all)
    return defs.ifEmpty { CategoryDefaults.all }
}

/**
 * The coloured category tile shared by the ledger rows, the record sheet and 分类管理: rounded square,
 * colour at 12%, icon in the colour. [selected] flips it to a solid colour block with a white icon
 * (the reference's filled cell).
 */
@Composable
fun CategoryTile(
    def: CategoryDef,
    size: Dp = 40.dp,
    selected: Boolean = false,
    iconRatio: Float = 0.56f,
    modifier: Modifier = Modifier,
) {
    val hue = categoryColor(def.hue)
    val bg = if (selected) hue else categoryTint(def.hue)
    // Dark hues are pastels, so a white glyph on the filled cell was ~1.7:1 and vanished. Pick the ink that
    // actually reads on that hue; in light the hues are dark enough that white stays the answer.
    val fg = when {
        !selected -> hue
        XpColors.isDark -> Contrast.onAccent(hue)
        else -> Color.White
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CategoryIconCatalog.icon(def.iconKey),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(size * iconRatio),
        )
    }
}
