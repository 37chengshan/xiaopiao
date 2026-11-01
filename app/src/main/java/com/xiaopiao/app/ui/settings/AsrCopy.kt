package com.xiaopiao.app.ui.settings

import com.xiaopiao.app.domain.asr.AsrLanguage
import com.xiaopiao.app.domain.asr.AsrModel
import java.util.Locale
import kotlin.math.roundToInt
import com.xiaopiao.app.ui.tr

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
    title = tr("语音模型", "Voice models"),
    lead = tr("记账的语音在本机识别，录音不上传。", "Voice entries are recognised on this phone. Audio is never uploaded."),
    privacy = tr("模型下载到手机上，识别时不出网；删掉模型就回到系统语音。", "The model downloads to this phone and works offline; delete it to go back to system speech."),
    small = tr("小模型（推荐）", "Small model (recommended)"),
    smallNote = tr("边说边出字，记账热词更准", "Types as you speak, tuned for receipt words"),
    large = tr("大模型 · 更准", "Large model · more accurate"),
    largeNote = tr("中/英/粤，说完再出字，约占 240 MB", "Chinese, English and Cantonese; types when you finish; about 240 MB"),
    off = tr("不下载", "Do not download"),
    offNote = tr("麦克风会提示你用输入法的语音输入", "The mic button points you to keyboard voice input"),
    download = tr("下载", "Download"),
    cancel = tr("取消", "Cancel"),
    retry = tr("重试", "Retry"),
    delete = tr("删除", "Delete"),
    installed = tr("已下载", "Downloaded"),
    downloading = tr("下载中 %d%%", "Downloading %d%%"),
    failed = tr("下载没成功，检查网络再试", "Download failed — check the connection and try again"),
    languageNote = tr("跟随手机语言", "Follows the phone language"),
    wifiNote = tr("大模型建议在 Wi-Fi 下下载", "Download the large model over Wi-Fi"),
    promptTitle = tr("先下载一个语音模型", "Download a voice model first"),
    promptBody = { model -> tr("「${modelLabel(model)}」约 ${mb(model.totalBytes)}，只下一次；录音不会离开这台手机。", "${modelLabel(model)} is about ${mb(model.totalBytes)}, downloaded once. Audio never leaves this phone.") },
    promptDownload = tr("下载并开始", "Download and start"),
    promptLater = tr("先用输入法", "Use the keyboard for now"),
    rowValueMissing = tr("未下载", "Not downloaded"),
    rowValueReady = tr("已就绪", "Ready"),
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
        tr("大模型 · SenseVoice", "Large model · SenseVoice")
    }

fun sizeText(bytes: Long): String = mb(bytes)

fun progressText(copy: AsrCopy, fraction: Float): String = String.format(Locale.US, copy.downloading, pct(fraction))
