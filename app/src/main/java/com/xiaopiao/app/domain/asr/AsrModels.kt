package com.xiaopiao.app.domain.asr

import java.util.Locale

/**
 * The two languages the shipped speech models come in. The app follows the phone's language, so a user
 * with an English system gets the English small model and English hotwords.
 */
enum class AsrLanguage(val tag: String, val label: String) {
    ZH("zh", "中文"),
    EN("en", "English"),
    ;

    companion object {
        fun of(tag: String?): AsrLanguage =
            if (tag?.lowercase(Locale.ROOT)?.startsWith("en") == true) EN else ZH

        fun system(): AsrLanguage = of(Locale.getDefault().language)
    }
}

/** The two sizes tt asked for: a small model that is always good enough, and an optional bigger one. */
enum class AsrTier { SMALL, LARGE }

/**
 * One file of a model. [sha256] is checked after download; a file that already matches its size and hash
 * is never fetched again, so a cancelled download simply resumes.
 */
data class AsrFile(
    val name: String,
    val bytes: Long,
    val sha256: String,
)

/**
 * A downloadable recognition model.
 *
 * Small models are streaming transducers (partial text while the user is still talking, and hotwords via
 * `modified_beam_search`). The large model is SenseVoice: better accuracy, no partials and no hotwords —
 * the UI never has to explain that, it just hears the sentence once the user lets go.
 */
data class AsrModel(
    val id: String,
    val language: AsrLanguage,
    val tier: AsrTier,
    /** Hugging Face repo that hosts the files. */
    val repo: String,
    val files: List<AsrFile>,
    val streaming: Boolean,
) {
    val totalBytes: Long get() = files.sumOf { it.bytes }
    val supportsHotwords: Boolean get() = streaming
    val encoder: AsrFile? get() = files.firstOrNull { it.name.startsWith("encoder") }
    val decoder: AsrFile? get() = files.firstOrNull { it.name.startsWith("decoder") }
    val joiner: AsrFile? get() = files.firstOrNull { it.name.startsWith("joiner") }
    val model: AsrFile? get() = files.firstOrNull { it.name.startsWith("model") }
    val tokens: AsrFile get() = files.first { it.name == "tokens.txt" }
}

/** Sizes are the real byte counts of the files (see qa/voice-v10/REPORT.md for the download log). */
object AsrCatalog {
    val ZH_SMALL = AsrModel(
        id = "zh-small-zipformer-14m",
        language = AsrLanguage.ZH,
        tier = AsrTier.SMALL,
        repo = "csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23",
        streaming = true,
        files = listOf(
            AsrFile("encoder-epoch-99-avg-1.int8.onnx", 21_621_684L, "1c556ea57cec304e55ec4b72e52c1cc098bb01476ed7d90f3de939fe126487b1"),
            AsrFile("decoder-epoch-99-avg-1.int8.onnx", 1_888_682L, "22f123bb8cba9b38974b3df18a3f45e7081f4985ebb2e075d9f21f618c468bbf"),
            AsrFile("joiner-epoch-99-avg-1.int8.onnx", 1_795_562L, "a7cf9d82757bdcf786059454495a9ca95e4bd7347f72473fc08d794475c36169"),
            AsrFile("tokens.txt", 48_697L, "8b294db9045d6e5f94647f4c1eec1af4da143a75053c399611444b378ff966ac"),
        ),
    )

    val EN_SMALL = AsrModel(
        id = "en-small-zipformer-20m",
        language = AsrLanguage.EN,
        tier = AsrTier.SMALL,
        repo = "csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17",
        streaming = true,
        files = listOf(
            AsrFile("encoder-epoch-99-avg-1.int8.onnx", 42_845_182L, "3810755ce7c3ab26b42a8bcf39d191308fa27fb0f53358823ba46141d03b7eb3"),
            AsrFile("decoder-epoch-99-avg-1.int8.onnx", 539_499L, "21e2a2acd961b3ac72f55be2f10f1a285e1b0b0ba010d7c0b6eab141411b163c"),
            AsrFile("joiner-epoch-99-avg-1.int8.onnx", 259_572L, "e085d73b593cf9b0707f370dbd656d58327d3fe36d80d849202ef81df02cb01e"),
            AsrFile("tokens.txt", 5_048L, "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb"),
        ),
    )

    val MULTI_LARGE = AsrModel(
        id = "multi-large-sensevoice",
        language = AsrLanguage.ZH,
        tier = AsrTier.LARGE,
        repo = "csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17",
        streaming = false,
        files = listOf(
            AsrFile("model.int8.onnx", 239_233_841L, "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"),
            AsrFile("tokens.txt", 315_894L, "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"),
        ),
    )

    val all: List<AsrModel> = listOf(ZH_SMALL, EN_SMALL, MULTI_LARGE)

    fun byId(id: String): AsrModel? = all.firstOrNull { it.id == id }

    /** The small model that matches [language]. */
    fun smallFor(language: AsrLanguage): AsrModel = if (language == AsrLanguage.EN) EN_SMALL else ZH_SMALL

    /**
     * What the settings list shows for [language]: its small model plus the one large model, which is
     * listed for both languages because SenseVoice covers Chinese and English in the same file.
     */
    fun forLanguage(language: AsrLanguage): List<AsrModel> = listOf(smallFor(language), MULTI_LARGE)

    /** Download bases: GitHub's own release host first, then two mirrors people in China actually reach. */
    val mirrors: List<String> = listOf(
        "https://huggingface.co",
        "https://hf-mirror.com",
    )

    fun url(mirror: String, model: AsrModel, file: AsrFile): String =
        "$mirror/${model.repo}/resolve/main/${file.name}"
}
