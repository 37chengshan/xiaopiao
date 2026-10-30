package com.xiaopiao.app.ui.account

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.liquidGlass
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xiaopiao.app.ui.components.LocalGlass
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.theme.XpColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Sparse ambient light behind the glass; only layer translations change during the 32s cycle. */
@Composable
internal fun AccountBackdrop(scene: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}, content: @Composable () -> Unit) {
    val ground = rememberLayerBackdrop()
    val reduced = rememberReducedMotion()
    val phase: State<Float> = if (reduced) remember { mutableFloatStateOf(0f) } else
        rememberInfiniteTransition(label = "accountAmbient").animateFloat(0f, (2 * PI).toFloat(),
            infiniteRepeatable(tween(com.xiaopiao.app.ui.motion.XpTiming.AccountAmbient, easing = LinearEasing)), label = "ambientPhase")
    val accent = XpColors.Accent
    val tint = XpColors.Highlight
    val dark = XpColors.isDark
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(ground).background(XpColors.CanvasGrouped)) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = sin(phase.value) * size.width * 0.07f
                translationY = cos(phase.value) * size.height * 0.035f
            }.drawBehind {
                val center = Offset(size.width * 0.12f, size.height * 0.2f)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (dark) 0.13f else 0.16f), Color.Transparent),
                    center, size.width * 0.85f), size.width * 0.85f, center)
            })
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = cos(phase.value) * size.width * 0.06f
                translationY = sin(phase.value) * size.height * 0.04f
            }.drawBehind {
                val center = Offset(size.width * 0.92f, size.height * 0.77f)
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = if (dark) 0.07f else 0.08f), Color.Transparent),
                    center, size.width * 0.7f), size.width * 0.7f, center)
            })
            scene()
        }
        CompositionLocalProvider(LocalGlass provides ground, LocalAccountGround provides ground) { content() }
    }
}

@Composable
internal fun Modifier.accountGlass(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(Modifier.liquidGlass(LocalGlass.current, shape,
        film = XpColors.Card.copy(alpha = if (XpColors.isDark) 0.20f else 0.16f),
        blur = 4.dp, lensHeight = 10.dp, lensAmount = 14.dp))

@Composable
internal fun AccountClose(onClick: () -> Unit, description: String) {
    val ground = LocalAccountGround.current
    if (ground != null) com.xiaopiao.app.ui.components.GlassCapsule(ground) {
        com.xiaopiao.app.ui.components.NavIconButton(androidx.compose.material.icons.Icons.Outlined.Close, description, onClick)
    }
}
private val LocalAccountGround = androidx.compose.runtime.staticCompositionLocalOf<com.kyant.backdrop.backdrops.LayerBackdrop?> { null }
