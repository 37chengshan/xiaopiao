package com.xiaopiao.app.ui.settings

import com.xiaopiao.app.domain.asr.AsrLanguage
import com.xiaopiao.app.domain.asr.AsrModel
import java.util.Locale
import kotlin.math.roundToInt

/** Every string the speech-model surfaces show, in both app languages (tt 2026-10-29). */
data class AsrCopy(
    val title: String,
    val lead: String,
    val privacy: String,
    val small: String,
    val smallNote: String,
    val large: String,
    val largeNote: String,
    val off: String,
    val offNote: String,
    val download: String,
    val cancel: String,
    val retry: String,
    val delete: String,
    val installed: String,
    val downloading: String,
    val failed: String,
    val languageNote: String,
    val wifiNote: String,
    val promptTitle: String,
    val promptBody: (AsrModel) -> String,
    val promptDownload: String,
    val promptLater: String,
    val rowValueMissing: String,
    val rowValueReady: String,
)

private fun mb(bytes: Long): String = String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)

private fun pct(fraction: Float): Int = (fraction * 100).roundToInt().coerceIn(0, 100)

fun asrCopy(language: AsrLanguage): AsrCopy = if (language == AsrLanguage.EN) english else chinese

private val chinese = AsrCopy(
    title = "语音模型",
    lead = "记账的语音在本机识别，录音不上传。",
    privacy = "模型下载到手机上，识别时不出网；删掉模型就回到系统语音。",
    small = "小模型（推荐）",
    smallNote = "边说边出字，记账热词更准",
    large = "大模型 · 更准",
    largeNote = "中/英/粤，说完再出字，约占 240 MB",
    off = "不下载",
    offNote = "麦克风会提示你用输入法的语音输入",
    download = "下载",
    cancel = "取消",
    retry = "重试",
    delete = "删除",
    installed = "已下载",
    downloading = "下载中 %d%%",
    failed = "下载没成功，检查网络再试",
    languageNote = "跟随手机语言",
    wifiNote = "大模型建议在 Wi-Fi 下下载",
    promptTitle = "先下载一个语音模型",
    promptBody = { model -> "「${modelLabel(model)}」约 ${mb(model.totalBytes)}，只下一次；录音不会离开这台手机。" },
    promptDownload = "下载并开始",
    promptLater = "先用输入法",
    rowValueMissing = "未下载",
    rowValueReady = "已就绪",
)

private val english = AsrCopy(
    title = "Speech models",
    lead = "Speech is recognised on the phone. Audio never leaves it.",
    privacy = "The model is downloaded to this phone and recognition runs offline; delete it to fall back to system speech.",
    small = "Small model (recommended)",
    smallNote = "Live words while you speak, ledger hotwords",
    large = "Large model · more accurate",
    largeNote = "Chinese/English/Cantonese, decodes when you stop, about 240 MB",
    off = "Do not download",
    offNote = "The mic will point you at your keyboard's dictation",
    download = "Download",
    cancel = "Cancel",
    retry = "Retry",
    delete = "Delete",
    installed = "Downloaded",
    downloading = "Downloading %d%%",
    failed = "Download failed — check the connection and try again",
    languageNote = "Follows the phone's language",
    wifiNote = "The large model is best on Wi-Fi",
    promptTitle = "Download a speech model first",
    promptBody = { model -> "\"${modelLabel(model)}\" is about ${mb(model.totalBytes)}, downloaded once. Audio never leaves this phone." },
    promptDownload = "Download and start",
    promptLater = "Use the keyboard",
    rowValueMissing = "Not downloaded",
    rowValueReady = "Ready",
)

/** Row label for a model: 「小模型 · 中文」 / 「Large model · SenseVoice」. */
fun modelLabel(model: AsrModel, copy: AsrCopy = asrCopy(model.language)): String =
    if (model.tier == com.xiaopiao.app.domain.asr.AsrTier.SMALL) {
        "${copy.small} · ${model.language.label}"
    } else if (copy === english) {
        "Large model · SenseVoice"
    } else {
        "大模型 · SenseVoice"
    }

fun sizeText(bytes: Long): String = mb(bytes)

fun progressText(copy: AsrCopy, fraction: Float): String = String.format(Locale.US, copy.downloading, pct(fraction))
