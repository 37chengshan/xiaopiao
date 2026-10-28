package com.xiaopiao.app.capture

import com.xiaopiao.app.domain.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantCategorizerTest {
    private fun c(s: String) = MerchantCategorizer.categorize(s)

    @Test fun drinks() { assertEquals(Category.DRINK, c("瑞幸咖啡")); assertEquals(Category.DRINK, c("喜茶")); assertEquals(Category.DRINK, c("蜜雪冰城")) }
    @Test fun transport() { assertEquals(Category.TRANSPORT, c("滴滴出行")); assertEquals(Category.TRANSPORT, c("上海地铁")); assertEquals(Category.TRANSPORT, c("12306")) }
    @Test fun meal() { assertEquals(Category.MEAL, c("美团外卖")); assertEquals(Category.MEAL, c("饿了么")); assertEquals(Category.MEAL, c("午饭")) }
    @Test fun subscription() {
        assertEquals(Category.SUBSCRIPTION, c("爱奇艺")); assertEquals(Category.SUBSCRIPTION, c("腾讯视频VIP"))
        assertEquals(Category.SUBSCRIPTION, c("网易云音乐")); assertEquals(Category.SUBSCRIPTION, c("QQ音乐")); assertEquals(Category.SUBSCRIPTION, c("Apple"))
    }
    @Test fun specificBeatsGeneric() {
        assertEquals(Category.TRANSPORT, c("美团单车")); assertEquals(Category.GROCERY, c("美团买菜")); assertEquals(Category.HEALTH, c("美团买药"))
    }
    @Test fun caseInsensitive() { assertEquals(Category.SUBSCRIPTION, c("iCloud")); assertEquals(Category.SUBSCRIPTION, c("NETFLIX")) }
    @Test fun unknownIsOther() { assertEquals(Category.OTHER, c("某某工作室")) }
    @Test fun noteHelps() { assertEquals(Category.DRINK, MerchantCategorizer.categorize("某店", "奶茶")) }
}
