package com.xiaopiao.app.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.NavTextAction
import com.xiaopiao.app.ui.components.UndoToastHost
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberUndoToastState
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.games.wheel.MaxWheelSlices
import com.xiaopiao.app.ui.games.wheel.MinWheelSlices
import com.xiaopiao.app.ui.games.wheel.Wheel
import com.xiaopiao.app.ui.games.wheel.WheelDisc
import com.xiaopiao.app.ui.games.wheel.WheelEditor
import com.xiaopiao.app.ui.games.wheel.WheelEase
import com.xiaopiao.app.ui.games.wheel.WheelHistoryEntry
import com.xiaopiao.app.ui.games.wheel.WheelHistoryRow
import com.xiaopiao.app.ui.games.wheel.WheelPreset
import com.xiaopiao.app.ui.games.wheel.WheelPresets
import com.xiaopiao.app.ui.games.wheel.WheelResultCard
import com.xiaopiao.app.ui.games.wheel.WheelStore
import com.xiaopiao.app.ui.games.wheel.WheelSwitcher
import com.xiaopiao.app.ui.games.wheel.landingRotation
import com.xiaopiao.app.ui.games.wheel.newWheel
import com.xiaopiao.app.ui.games.wheel.shouldTick
import com.xiaopiao.app.ui.games.wheel.sliceUnder
import com.xiaopiao.app.ui.games.wheel.spinDurationMs
import com.xiaopiao.app.ui.games.wheel.spinTurns
import com.xiaopiao.app.ui.games.wheel.wheelPalette
import com.xiaopiao.app.ui.motion.XpMotion
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.tr
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

/** 一次转动落在哪：扇面下标、文字、第几次。 */
private data class SpinResult(val index: Int, val text: String, val spin: Int)

/** 指针被拨开的角度。够小，看起来像被弹了一下，不像坏掉。 */
private const val PointerKickDegrees = 7f

/** 拨盘松手后还愿意转的最小角速度（度/秒）。更慢就是摆一摆，不转。 */
private const val FlingThreshold = 320f

/**
 * 转盘：通用决策模块（tt 2026-10-30 首版，2026-11-02 重做）。
 *
 * 人可以建多个盘、自己填每一格；配置存在本机（prefs 名与 JSON 形状沿用旧版），重开还在。
 * 盘只做一件事——把人已经写好的选项随机指一个出来，不给建议、不预设答案。
 *
 * 转动是**先定结果、再算角度**：赢的扇面由 Random 挑好，然后至少转 4 圈、落在那一格内部的随机点上，
 * 永远不落在分隔线上。整段用一条 [Animatable] + 一条减速曲线跑完，转值只在 draw/layer 阶段读。
 */
@Composable
fun WheelGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WheelStore(context) }
    val reduced = rememberReducedMotion()
    val haptics = rememberXpHaptics()
    val scope = rememberCoroutineScope()
    val toast = rememberUndoToastState()

    var wheels by remember { mutableStateOf(store.load().ifEmpty { listOf(newWheel(1)) }) }
    var currentId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentIndex = wheels.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
    val wheel = wheels[currentIndex]
    val palette = wheelPalette()

    fun persist(list: List<Wheel>) {
        wheels = list
        store.save(list)
    }

    fun updateCurrent(transform: (Wheel) -> Wheel) {
        persist(wheels.mapIndexed { i, item -> if (i == currentIndex) transform(item) else item })
    }

    var editing by remember(wheel.id) { mutableStateOf(false) }
    val rotation = remember(wheel.id) { Animatable(0f) }
    /** 手指拖出来、还没折进 [rotation] 的角度。只在 draw 阶段与 [rotation] 相加。 */
    var dragAngle by remember(wheel.id) { mutableFloatStateOf(0f) }
    var spinning by remember(wheel.id) { mutableStateOf(false) }
    var spinCount by remember(wheel.id) { mutableIntStateOf(0) }
    var result by remember(wheel.id) { mutableStateOf<SpinResult?>(null) }
    /** 这一转里被排除的选项（只用于展示；下一转默认恢复）。 */
    var outNow by remember(wheel.id) { mutableStateOf<Set<String>>(emptySet()) }
    var history by remember(wheel.id) { mutableStateOf<List<WheelHistoryEntry>>(emptyList()) }
    val pointerKick = remember { Animatable(0f) }
    var kickJob by remember { mutableStateOf<Job?>(null) }
    var discSize by remember { mutableStateOf(IntSize.Zero) }

    val slices = wheel.slices
    val filled = slices.count { it.isNotBlank() }
    val canSpin = filled >= MinWheelSlices

    /** Fold the finger's angle into the animatable in one go, so the drawn angle never jumps. */
    suspend fun foldDrag() {
        if (dragAngle == 0f) return
        rotation.snapTo(rotation.value + dragAngle)
        dragAngle = 0f
    }

    fun kickPointer() {
        kickJob?.cancel()
        kickJob = scope.launch {
            pointerKick.snapTo(PointerKickDegrees)
            pointerKick.animateTo(0f, XpMotion.bouncy())
        }
    }

    fun finish(index: Int, spin: Int) {
        val text = slices.getOrNull(index).orEmpty().trim()
        spinning = false
        result = SpinResult(index, text, spin)
        history = (listOf(WheelHistoryEntry(text, spin)) + history).take(5)
        haptics.confirm()
    }

    fun spin(exclude: Set<String> = emptySet(), flingVelocity: Float = 0f) {
        if (spinning) return
        val candidates = slices.indices.filter {
            val label = slices[it].trim()
            label.isNotEmpty() && label !in exclude
        }
        if (candidates.size < MinWheelSlices) return
        val random = Random
        val winner = candidates[random.nextInt(candidates.size)]
        val turns = spinTurns(flingVelocity)
        val duration = spinDurationMs(turns)
        val number = spinCount + 1
        spinCount = number
        outNow = exclude
        result = null
        spinning = true

        scope.launch {
            foldDrag()
            val target = landingRotation(rotation.value, winner, slices.size, random, turns)
            if (reduced) {
                rotation.snapTo(target)
                finish(winner, number)
                return@launch
            }
            var lastSlice = sliceUnder(rotation.value, slices.size)
            var lastTick = 0L
            rotation.animateTo(target, tween(duration, easing = WheelEase)) {
                val now = sliceUnder(value, slices.size)
                if (now != lastSlice) {
                    lastSlice = now
                    val at = android.os.SystemClock.uptimeMillis()
                    if (shouldTick(lastTick, at)) {
                        lastTick = at
                        haptics.tick()
                        kickPointer()
                    }
                }
            }
            finish(winner, number)
        }
    }

    fun restoreExcluded() {
        outNow = emptySet()
    }

    fun removeSlice(index: Int) {
        if (slices.size <= MinWheelSlices) {
            toast.show(
                tr("至少要 2 个选项，先加一个再删", "Two slices minimum — add one before removing"),
                durationMs = 2400,
            )
            return
        }
        val before = slices
        val removed = slices[index]
        updateCurrent { it.copy(slices = it.slices.filterIndexed { i, _ -> i != index }) }
        result = null
        // 撤销把整张列表放回去：位置、文字、空格都对得上，不需要反推。
        toast.show(
            tr("已删掉「${removed.ifBlank { "空的一格" }}」", "Removed \u201C${removed.ifBlank { "empty slice" }}\u201D"),
            tr("撤销", "Undo"),
        ) {
            updateCurrent { it.copy(slices = before) }
        }
    }

    // Driven by the page scroll like every other page: scrolled content fades under the header instead of
    // running over the title and the status bar (tt 2026-11-02 screenshot).
    val pageScroll = rememberScrollState()
    val tabBarInset = com.xiaopiao.app.ui.components.LocalTabBarInset.current
    NavScaffold(
        title = tr("转盘", "Wheel"),
        scroll = com.xiaopiao.app.ui.components.rememberNavScroll(pageScroll),
        onBack = onBack,
        trailing = {
            // 转的时候不给编辑入口：盘面在动，改一半只会让高亮对不上。
            if (!spinning) {
                NavTextAction(
                    text = if (editing) tr("完成", "Done") else tr("编辑", "Edit"),
                    onClick = {
                        haptics.select()
                        editing = !editing
                    },
                )
            }
        },
        overlay = { glass ->
            UndoToastHost(
                toast,
                // Above the app's tab bar, never under it (the glass bar was sampling the toast).
                Modifier.align(Alignment.BottomCenter).padding(bottom = tabBarInset + XpSpace.m),
                backdrop = glass,
            )
        },
    ) { top ->
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(pageScroll)
                .padding(top = top, bottom = tabBarInset + XpSpace.xl)
                .padding(horizontal = XpSpace.page),
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(XpSpace.s))
            WheelSwitcher(
                wheels = wheels,
                current = currentIndex,
                enabled = !spinning,
                palette = palette,
                onSelect = { index ->
                    currentId = wheels[index].id
                },
                onAdd = {
                    val next = wheels + newWheel(wheels.size + 1)
                    persist(next)
                    currentId = next.last().id
                },
                onRename = { name ->
                    updateCurrent { it.copy(name = name) }
                    toast.show(tr("已改名为「$name」", "Renamed to \u201C$name\u201D"), durationMs = 2000)
                },
                onDelete = {
                    val removed = wheel.name
                    if (wheels.size == 1) {
                        persist(listOf(newWheel(1)))
                    } else {
                        persist(wheels.filterIndexed { i, _ -> i != currentIndex })
                    }
                    currentId = null
                    toast.show(tr("已删掉「$removed」", "Deleted \u201C$removed\u201D"), durationMs = 2400)
                },
            )

            Spacer(Modifier.height(XpSpace.m))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WheelDisc(
                    slices = slices,
                    palette = palette,
                    rotationProvider = { rotation.value + dragAngle },
                    spinning = spinning,
                    highlight = result?.takeIf { !spinning }?.index,
                    dimmed = result != null && !spinning,
                    bulbsLit = result != null && !spinning,
                    pointerKick = { pointerKick.value },
                    hubEnabled = !spinning && canSpin,
                    hubLabel = if (result == null) tr("转", "Spin") else tr("再转", "Again"),
                    hubSubLabel = if (canSpin) tr("${filled} 格", "$filled slices") else tr("先填字", "Fill it"),
                    onSpin = { spin() },
                    modifier = Modifier.fillMaxWidth(0.94f)
                        .aspectRatio(1f)
                        .onSizeChanged { discSize = it }
                        .pointerInput(wheel.id, discSize, spinning) {
                            if (discSize.width == 0) return@pointerInput
                            val center = Offset(discSize.width / 2f, discSize.height / 2f)
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (spinning) {
                                    // A spinning wheel ignores the finger instead of fighting it.
                                    down.consume()
                                    return@awaitEachGesture
                                }
                                var lastAngle = angleAt(down.position - center)
                                var lastAt = down.uptimeMillis
                                var total = 0f
                                var velocity = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val angle = angleAt(change.position - center)
                                    var delta = angle - lastAngle
                                    if (delta > 180f) delta -= 360f
                                    if (delta < -180f) delta += 360f
                                    total += delta
                                    val now = change.uptimeMillis
                                    val dt = (now - lastAt).coerceAtLeast(1L)
                                    velocity = velocity * 0.55f + (delta / dt * 1000f) * 0.45f
                                    lastAngle = angle
                                    lastAt = now
                                    dragAngle = total
                                    change.consume()
                                }
                                if (abs(total) > 3f) {
                                    haptics.tick()
                                    if (abs(velocity) > FlingThreshold) {
                                        spin(emptySet(), velocity)
                                    } else {
                                        scope.launch { foldDrag() }
                                    }
                                } else {
                                    scope.launch { foldDrag() }
                                }
                            }
                        },
                )
            }

            if (result == null) {
                Spacer(Modifier.height(XpSpace.s))
                Text(
                    if (canSpin) {
                        tr("点中间的「转」，或者用手指拨一下盘面", "Tap 转 in the middle, or flick the disc")
                    } else {
                        tr("先填两格以上，才能转", "Fill in at least two slices to spin")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = XpColors.InkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 空盘不是一个死胡同：直接给一条进入编辑的路。
                if (!canSpin && !editing) {
                    Spacer(Modifier.height(XpSpace.s))
                    XpButton(
                        text = tr("填写选项", "Fill the slices"),
                        onClick = {
                            haptics.select()
                            editing = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = XpButtonStyle.Secondary,
                        size = XpButtonSize.Small,
                    )
                }
            } else {
                Spacer(Modifier.height(XpSpace.m))
            }

            WheelHistoryRow(history, Modifier.padding(top = if (result == null) XpSpace.m else 0.dp))

            AnimatedVisibility(
                visible = result != null,
                // Reduced motion: the card just fades in, no rise.
                enter = if (reduced) XpMotion.enterFade() else XpMotion.enterRise(),
                exit = XpMotion.exitSink(),
            ) {
                val current = result
                if (current != null) {
                    Box(Modifier.padding(top = XpSpace.m)) {
                        WheelResultCard(
                            text = current.text,
                            spinNumber = current.spin,
                            excluded = outNow.firstOrNull(),
                            canExclude = filled - outNow.size >= MinWheelSlices,
                            onSpinAgain = {
                                outNow = emptySet()
                                spin(emptySet())
                            },
                            onExclude = { spin(setOf(current.text)) },
                            onRestore = { restoreExcluded() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(XpSpace.l))

            when {
                editing && spinning -> Text(
                    tr("转完再改。", "Edit again once the wheel stops."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = XpColors.InkSecondary,
                )
                editing -> WheelEditor(
                    wheel = wheel,
                    palette = palette,
                    onSliceChange = { index, text ->
                        updateCurrent { item ->
                            item.copy(slices = item.slices.toMutableList().also { it[index] = text })
                        }
                        result = null
                    },
                    onReorder = { from, to ->
                        updateCurrent { item ->
                            val list = item.slices.toMutableList()
                            if (from in list.indices && to in list.indices) {
                                val moved = list.removeAt(from)
                                list.add(to, moved)
                            }
                            item.copy(slices = list)
                        }
                        result = null
                    },
                    onDelete = { index -> removeSlice(index) },
                    onAdd = {
                        if (slices.size < MaxWheelSlices) {
                            updateCurrent { it.copy(slices = it.slices + "") }
                            haptics.select()
                        }
                    },
                    onPreset = { preset: WheelPreset ->
                        val fresh = WheelPresets.wheelFor(preset, wheels.size + 1)
                        persist(wheels + fresh)
                        currentId = fresh.id
                        editing = false
                        toast.show(tr("新建了「${fresh.name}」", "Created \u201C${fresh.name}\u201D"), durationMs = 2200)
                    },
                )
                else -> Text(
                    tr(
                        "盘面上的字只有你自己看得到，也只存在这台手机里。",
                        "Your slices stay on this phone — nobody else sees them.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkTertiary,
                )
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

/** 屏幕坐标系里的角度：右手边是 0°，顺时针为正（和 rotate()/drawArc 一致）。 */
private fun angleAt(offset: Offset): Float = (atan2(offset.y, offset.x) * 180.0 / PI).toFloat()
