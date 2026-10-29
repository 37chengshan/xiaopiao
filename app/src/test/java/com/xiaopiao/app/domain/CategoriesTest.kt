package com.xiaopiao.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Categories are data now (tt#70/71): defaults, legacy bridge, palette contrast, copy helpers. */
class CategoriesTest {

    @Test
    fun `every legacy enum value maps to a built-in category`() {
        Category.entries.forEach { category ->
            val id = CategoryDefaults.idFor(category)
            assertNotNull("no built-in for $category", CategoryDefaults.find(id))
        }
        assertEquals(13, CategoryDefaults.all.size)
        assertEquals(CategoryDefaults.all.size, CategoryDefaults.all.map { it.id }.toSet().size)
    }

    @Test
    fun `built-in ids keep the legacy order and labels`() {
        CategoryDefaults.all.forEachIndexed { index, def ->
            assertEquals(index, def.sortOrder)
            assertTrue(def.builtIn)
            assertTrue(def.hidden.not())
            val legacy = Category.entries.first { it.name.lowercase() == def.id }
            assertEquals(legacy.label, def.label)
        }
    }

    @Test
    fun `resolve prefers the stored id, falls back to the enum, then to other`() {
        val defs = CategoryDefaults.all
        val custom = CategoryDefaults.custom("猫粮", "pets", CategoryHue.PINK, null, now = 7L, sortOrder = 20)
        val all = defs + custom

        val stored = Txn(amountCents = 1, merchant = "m", category = Category.OTHER, occurredAt = 0, source = Source.MANUAL, categoryId = custom.id)
        assertEquals("猫粮", all.resolve(stored).label)

        val legacy = Txn(amountCents = 1, merchant = "m", category = Category.MEAL, occurredAt = 0, source = Source.MANUAL)
        assertEquals("吃饭", all.resolve(legacy).label)

        val orphan = Txn(amountCents = 1, merchant = "m", category = Category.OTHER, occurredAt = 0, source = Source.MANUAL, categoryId = "custom:gone")
        assertEquals(CategoryDefaults.OTHER_ID, all.resolve(orphan).id)
    }

    @Test
    fun `a new custom category trims, caps and namespaces its id`() {
        val def = CategoryDefaults.custom("  猫粮非常非常长  ", "pets", CategoryHue.PINK, Direction.OUT, now = 42L, sortOrder = 3)
        assertEquals("custom:42", def.id)
        assertTrue(def.label.length <= CategoryDefaults.MAX_LABEL)
        assertTrue(def.label.startsWith("猫粮"))
        assertEquals(Direction.OUT, def.direction)
        assertTrue(def.isCustom)
        assertTrue(def.fits(Direction.OUT))
        assertTrue(def.fits(Direction.IN).not())
    }

    @Test
    fun `a category offered for both directions fits either`() {
        val def = CategoryDefaults.find("meal")!!
        assertTrue(def.fits(Direction.OUT))
        assertTrue(def.fits(Direction.IN))
    }

    @Test
    fun `source chip names the payment channel`() {
        assertEquals("支付宝自动记账", sourceChipLabel(Source.NOTIFICATION, CHANNEL_ALIPAY))
        assertEquals("微信自动记账", sourceChipLabel(Source.NOTIFICATION, CHANNEL_WECHAT))
        assertEquals("自动记账", sourceChipLabel(Source.NOTIFICATION, ""))
        assertEquals("手动记账", sourceChipLabel(Source.MANUAL, ""))
        assertEquals("手动记账", sourceChipLabel(Source.QUICK_ADD, ""))
        assertEquals("账单导入", sourceChipLabel(Source.CSV, ""))
    }

    @Test
    fun `grouped yuan keeps thousands separators`() {
        assertEquals("0.00", 0L.yuanGrouped())
        assertEquals("8.50", 850L.yuanGrouped())
        assertEquals("631.92", 631_92L.yuanGrouped())
        assertEquals("1,280.50", 1_280_50L.yuanGrouped())
        assertEquals("1,234,567.00", 123_456_700L.yuanGrouped())
        assertEquals("-1,280.50", (-1_280_50L).yuanGrouped())
    }

    /** Every hue has to be readable on its own 12% tint, in both palettes (task C.7). */
    @Test
    fun `every category hue clears 3 to 1 on its tint`() {
        CategoryHue.entries.forEach { hue ->
            val lightOnTint = contrast(hue.light, blend(hue.light, 0xFFFFFFFF, 0.12))
            val darkOnTint = contrast(hue.dark, blend(hue.dark, 0xFF1C1C1E, 0.12))
            assertTrue("${hue.key} light $lightOnTint", lightOnTint >= 3.0)
            assertTrue("${hue.key} dark $darkOnTint", darkOnTint >= 3.0)
        }
    }

    private fun channel(argb: Long, shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(argb: Long): Double =
        0.2126 * channel(argb, 16) + 0.7152 * channel(argb, 8) + 0.0722 * channel(argb, 0)

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Source-over composite of [fg] at [alpha] onto [bg], as the tile background does. */
    private fun blend(fg: Long, bg: Long, alpha: Double): Long {
        fun mix(shift: Int): Long {
            val f = ((fg shr shift) and 0xFF).toDouble()
            val b = ((bg shr shift) and 0xFF).toDouble()
            return Math.round(f * alpha + b * (1 - alpha))
        }
        return 0xFF000000L or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
