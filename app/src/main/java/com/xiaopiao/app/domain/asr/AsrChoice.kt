package com.xiaopiao.app.domain.asr

/**
 * Which recogniser to start, decided from the language, the user's choice and what is already on disk.
 *
 * Kept pure so the rule can be unit-tested without a phone: "preferred tier, but never leave the user
 * without a working microphone if a usable model is already installed".
 */
enum class AsrPreference { SMALL, LARGE, OFF }

sealed interface AsrChoice {
    /** [model] is installed and should be used. */
    data class Ready(val model: AsrModel) : AsrChoice

    /** Nothing usable yet; [model] is the one to offer for download. */
    data class NeedsDownload(val model: AsrModel) : AsrChoice

    /** The user asked for no on-device model: the mic button should point at the keyboard's own mic. */
    data object Off : AsrChoice

    companion object {
        fun pick(
            language: AsrLanguage,
            preference: AsrPreference,
            installedIds: Set<String>,
        ): AsrChoice {
            val small = AsrCatalog.smallFor(language)
            val large = AsrCatalog.MULTI_LARGE
            val smallReady = small.id in installedIds
            val largeReady = large.id in installedIds
            return when (preference) {
                AsrPreference.OFF -> Off
                AsrPreference.LARGE -> when {
                    largeReady -> Ready(large)
                    smallReady -> Ready(small)
                    else -> NeedsDownload(large)
                }
                AsrPreference.SMALL -> when {
                    smallReady -> Ready(small)
                    largeReady -> Ready(large)
                    else -> NeedsDownload(small)
                }
            }
        }
    }
}
