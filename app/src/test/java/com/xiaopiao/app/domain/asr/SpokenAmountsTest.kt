package com.xiaopiao.app.domain.asr

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spoken amounts must reach the parser as digits, and long forms must not lose a zero (三千五). */
class SpokenAmountsTest {

    @Test fun yuanAndJiaoBecomeDecimal() {
        assertEquals("午饭35.5", SpokenAmounts.normalize("午饭三十五块五"))
        assertEquals("打车23", SpokenAmounts.normalize("打车二十三块"))
        assertEquals("奶茶12", SpokenAmounts.normalize("奶茶十二块"))
        assertEquals("晚饭1.5", SpokenAmounts.normalize("晚饭一块半"))
        assertEquals("买了0.5", SpokenAmounts.normalize("买了五毛"))
        assertEquals("一共12.3", SpokenAmounts.normalize("一共十二块三毛钱"))
        assertEquals("话费0.05", SpokenAmounts.normalize("话费五分"))
    }

    @Test fun longFormsKeepTheirZero() {
        assertEquals("花了3500", SpokenAmounts.normalize("花了三千五"))
        assertEquals("房租250", SpokenAmounts.normalize("房租两百五"))
        assertEquals("工资12000", SpokenAmounts.normalize("工资一万二"))
        assertEquals("餐费105", SpokenAmounts.normalize("餐费一百零五"))
    }

    @Test fun alreadyNumericAndTimesAreUntouched() {
        assertEquals("午饭35块5", SpokenAmounts.normalize("午饭35块5"))
        assertEquals("十二点三十分开会", SpokenAmounts.normalize("十二点三十分开会"))
        assertEquals("", SpokenAmounts.normalize(""))
    }

    @Test fun traditionalAndFormalCharactersFold() {
        assertEquals("奶茶16", SpokenAmounts.normalize("奶茶十六塊"))
        assertEquals("买菜32", SpokenAmounts.normalize("买菜叁拾贰元"))
    }

    @Test fun englishIsLeftAlone() {
        assertEquals("lunch 35 dollars", SpokenAmounts.normalize("lunch 35 dollars"))
    }
}
