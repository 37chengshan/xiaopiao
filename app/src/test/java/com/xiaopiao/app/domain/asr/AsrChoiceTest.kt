package com.xiaopiao.app.domain.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Model selection: language × what is downloaded × what the user picked. */
class AsrChoiceTest {

    private val zhSmall = AsrCatalog.ZH_SMALL.id
    private val enSmall = AsrCatalog.EN_SMALL.id
    private val large = AsrCatalog.MULTI_LARGE.id

    @Test fun nothingDownloadedOffersTheSmallModelOfTheLanguage() {
        val zh = AsrChoice.pick(AsrLanguage.ZH, AsrPreference.SMALL, emptySet())
        assertTrue(zh is AsrChoice.NeedsDownload && zh.model.id == zhSmall)

        val en = AsrChoice.pick(AsrLanguage.EN, AsrPreference.SMALL, emptySet())
        assertTrue(en is AsrChoice.NeedsDownload && en.model.id == enSmall)
    }

    @Test fun theRightSmallModelIsUsedPerLanguage() {
        val zh = AsrChoice.pick(AsrLanguage.ZH, AsrPreference.SMALL, setOf(zhSmall))
        assertTrue(zh is AsrChoice.Ready && zh.model.id == zhSmall)

        val en = AsrChoice.pick(AsrLanguage.EN, AsrPreference.SMALL, setOf(enSmall))
        assertTrue(en is AsrChoice.Ready && en.model.id == enSmall)
    }

    @Test fun theOtherLanguagesModelIsNotSubstituted() {
        val en = AsrChoice.pick(AsrLanguage.EN, AsrPreference.SMALL, setOf(zhSmall))
        assertTrue(en is AsrChoice.NeedsDownload && en.model.id == enSmall)
    }

    @Test fun largeIsPreferredWhenAskedAndFallsBackToSmall() {
        val ready = AsrChoice.pick(AsrLanguage.ZH, AsrPreference.LARGE, setOf(large))
        assertTrue(ready is AsrChoice.Ready && ready.model.id == large)

        val fallback = AsrChoice.pick(AsrLanguage.ZH, AsrPreference.LARGE, setOf(zhSmall))
        assertTrue(fallback is AsrChoice.Ready && fallback.model.id == zhSmall)

        val none = AsrChoice.pick(AsrLanguage.ZH, AsrPreference.LARGE, emptySet())
        assertTrue(none is AsrChoice.NeedsDownload && none.model.id == large)
    }

    @Test fun anInstalledLargeModelStillServesTheSmallPreference() {
        val choice = AsrChoice.pick(AsrLanguage.ZH, AsrPreference.SMALL, setOf(large))
        assertTrue(choice is AsrChoice.Ready && choice.model.id == large)
    }

    @Test fun offNeverOpensALocalEngine() {
        assertEquals(AsrChoice.Off, AsrChoice.pick(AsrLanguage.ZH, AsrPreference.OFF, setOf(zhSmall)))
    }
}
