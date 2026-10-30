package com.xiaopiao.app.domain.asr

import com.xiaopiao.app.domain.CategoryDef
import com.xiaopiao.app.domain.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hotwords: presets first, the user's own names after them, no duplicates, capped. */
class AsrHotwordsTest {

    private fun custom(id: String, label: String) =
        CategoryDef(id = "custom:$id", label = label, iconKey = "star", hue = "blue", direction = Direction.OUT)

    @Test fun presetsComeFirstAndCoverMoneyAndCategories() {
        val words = AsrHotwords.build(AsrLanguage.ZH)
        assertTrue(words.contains("块钱"))
        assertTrue(words.contains("外卖"))
        assertTrue(words.contains("奶茶"))
        assertTrue(words.indexOf("块钱") < words.indexOf("外卖"))
    }

    @Test fun dynamicEntriesAreAddedOnceAndNeverDisplacePresets() {
        val words = AsrHotwords.build(AsrLanguage.ZH, listOf("老王面馆", "老王面馆", "奶茶", "  "))
        assertEquals(1, words.count { it == "老王面馆" })
        assertEquals(1, words.count { it == "奶茶" })
        assertEquals(AsrHotwords.presetZh.size + 1, words.size)
    }

    @Test fun theListIsCapped() {
        val words = AsrHotwords.build(AsrLanguage.ZH, (1..200).map { "小店$it" }, limit = 100)
        assertEquals(100, words.size)
        assertEquals(100, words.distinct().size)
    }

    @Test fun englishHasItsOwnPresets() {
        val words = AsrHotwords.build(AsrLanguage.EN)
        assertTrue(words.contains("spent"))
        assertTrue(words.contains("bubble tea"))
        assertTrue(words.none { it == "块钱" })
    }

    @Test fun dynamicUsesCustomCategoriesAndRecentMerchants() {
        val dynamic = AsrHotwords.dynamicFor(
            defs = listOf(custom("1", "健身房"), custom("2", "宠物")),
            merchants = listOf("老王面馆", "老王面馆", "菜市场"),
        )
        assertEquals(listOf("老王面馆", "老王面馆", "菜市场", "健身房", "宠物"), dynamic)
    }

    @Test fun serialisedFileIsOnePhrasePerLine() {
        assertEquals("a\nb\n", AsrHotwords.serialize(listOf("a", "b")))
        assertEquals("", AsrHotwords.serialize(emptyList()))
    }
}
