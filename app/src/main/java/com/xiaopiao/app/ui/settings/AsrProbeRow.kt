package com.xiaopiao.app.ui.settings

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.asr.AsrCatalog
import com.xiaopiao.app.domain.asr.AsrChoice
import com.xiaopiao.app.domain.asr.AsrHotwords
import com.xiaopiao.app.domain.asr.AsrLanguage
import com.xiaopiao.app.domain.asr.SherpaEngine
import com.xiaopiao.app.domain.asr.SpokenAmounts
import com.xiaopiao.app.domain.asr.WavReader
import com.xiaopiao.app.domain.parse.QuickParser
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.theme.XpColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Debug-only self-test: feeds `/sdcard/Download/asr-probe.wav` (16 kHz mono) through the real engine,
 * normalises the transcript and runs it through the real parser, then reports on screen and in logcat.
 * Never rendered in a release build — there is no user-facing entry to it.
 */
@Composable
fun AsrProbeSection(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }

    GroupedSection(header = "自测（调试版）", footer = result) {
        item {
            GroupedRow(
                title = "离线识别自测",
                subtitle = "读 files/asr-probe.wav 走一遍识别 + 解析",
                value = when {
                    running -> "运行中"
                    result != null -> "已完成"
                    else -> null
                },
                leading = { IconTile(Icons.Outlined.Science, tint = XpColors.Info, background = XpColors.InfoSoft) },
                onClick = {
                    if (running) return@GroupedRow
                    running = true
                    result = null
                    scope.launch {
                        val wav = listOf(File(context.filesDir, "asr-probe.wav"), File("/sdcard/Download/asr-probe.wav"))
                            .firstOrNull { it.isFile }
                        val text = if (wav == null) {
                            "没有找到 files/asr-probe.wav"
                        } else {
                            runProbe(container, context.filesDir, wav)
                        }
                        result = text
                        Log.i("XpAsr", "probe: $text")
                        running = false
                    }
                },
            )
        }
    }
}

private suspend fun runProbe(container: AppContainer, filesDir: File, wav: File): String = withContext(Dispatchers.IO) {
    if (!wav.isFile) return@withContext "没有找到 ${wav.absolutePath}"
    // Whatever the settings currently select — this also exercises the language × installed × preference rule.
    val preference = container.asrPrefs.observe().first()
    val model = when (val choice = AsrChoice.pick(AsrLanguage.ZH, preference, container.asrModels.modelStore.installedIds())) {
        is AsrChoice.Ready -> choice.model
        is AsrChoice.NeedsDownload -> return@withContext "没下载 ${choice.model.id}"
        AsrChoice.Off -> return@withContext "设置里选了「不下载」"
    }
    // Same hotwords the real listen path writes, so the probe measures what users get.
    if (model.supportsHotwords && AsrLanguage.ZH == AsrLanguage.system()) {
        val hotwords = AsrHotwords.build(AsrLanguage.ZH, listOf("老王面馆", "山野咖啡"))
        container.asrModels.modelStore.hotwordsFile(AsrLanguage.ZH).apply {
            parentFile?.mkdirs()
            writeText(AsrHotwords.serialize(hotwords))
        }
    }
    val engine = SherpaEngine(container.asrModels.modelStore)
    val session = engine.open(model, AsrLanguage.ZH) ?: return@withContext "模型打不开"
    val samples = runCatching { WavReader.read(wav) }.getOrElse { return@withContext "wav 读不了：${it.message}" }
    // Feed it the way the microphone does: 100 ms at a time, like a live utterance.
    var raw = ""
    var offset = 0
    while (offset < samples.size) {
        val end = minOf(offset + 1_600, samples.size)
        val chunk = samples.copyOfRange(offset, end)
        val partial = session.accept(chunk)
        if (partial.isNotBlank()) {
            raw = partial
            Log.i("XpAsr", "partial: $partial")
        }
        offset = end
    }
    val text = session.finish().ifBlank { raw }
    session.close()
    val normalized = SpokenAmounts.normalize(text)
    val parsed = QuickParser.parse(normalized, System.currentTimeMillis())
    val summary = parsed.joinToString("；") { "${it.amountCents / 100.0} 元 · ${it.category.label}" }
    "模型：${model.id}\n识别：$text\n规整：$normalized\n解析：${summary.ifBlank { "无" }}"
}
