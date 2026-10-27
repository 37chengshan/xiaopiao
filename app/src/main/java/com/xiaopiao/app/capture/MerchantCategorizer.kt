package com.xiaopiao.app.capture

import com.xiaopiao.app.domain.Category

/**
 * Keyword -> category table. First matching category wins, so order matters:
 * more specific brands (美团单车, 美团买菜) must sit above generic ones (美团).
 * Adding a brand = adding a string to one of the lists below.
 */
object MerchantCategorizer {
    private val table: List<Pair<Category, List<String>>> = listOf(
        Category.SOCIAL to listOf("红包", "礼金", "份子", "请客"),
        Category.SUBSCRIPTION to listOf(
            "爱奇艺", "腾讯视频", "优酷", "芒果tv", "哔哩哔哩大会员", "b站大会员", "网易云", "qq音乐", "酷狗",
            "apple", "app store", "icloud", "netflix", "spotify", "youtube", "百度网盘", "wps", "知乎盐选",
            "会员", "连续包月", "自动续费", "订阅",
        ),
        Category.TRANSPORT to listOf(
            "滴滴", "地铁", "公交", "12306", "高德", "哈啰", "青桔", "美团单车", "出租", "打车", "加油",
            "中石化", "中石油", "停车", "etc", "携程", "航空", "机票", "高铁", "火车", "曹操出行", "t3出行", "共享单车",
        ),
        Category.DRINK to listOf(
            "瑞幸", "喜茶", "蜜雪", "奈雪", "古茗", "茶百道", "霸王茶姬", "书亦烧仙草", "一点点", "coco", "沪上阿姨",
            "库迪", "manner", "星巴克", "luckin", "奶茶", "咖啡", "果汁", "柠檬茶", "饮品",
        ),
        Category.GROCERY to listOf(
            "美团买菜", "盒马", "永辉", "沃尔玛", "山姆", "叮咚", "每日优鲜", "超市", "菜市场", "物美", "大润发", "生鲜",
            "日用", "洗衣液", "纸巾",
        ),
        Category.HEALTH to listOf("医院", "药房", "大药房", "药店", "诊所", "体检", "牙科", "美团买药", "老百姓"),
        Category.MEAL to listOf(
            "美团", "饿了么", "外卖", "午饭", "晚饭", "早饭", "早餐", "午餐", "晚餐", "夜宵", "吃饭", "餐厅", "饭店",
            "麦当劳", "肯德基", "kfc", "汉堡王", "必胜客", "海底捞", "食堂", "小吃", "火锅", "烧烤", "黄焖鸡", "沙县",
            "拉面", "面馆", "米线", "和府捞面", "快餐", "餐饮", "饭",
        ),
        Category.SNACK to listOf(
            "零食", "三只松鼠", "良品铺子", "来伊份", "蛋糕", "烘焙", "面包", "便利店", "便利蜂", "全家", "罗森", "7-11", "好利来",
        ),
        Category.FUN to listOf(
            "电影", "影院", "猫眼", "淘票票", "ktv", "游戏", "steam", "腾讯游戏", "网易游戏", "米哈游", "剧本杀",
            "演唱会", "大麦", "密室", "桌游",
        ),
        Category.HOUSING to listOf(
            "房租", "水费", "电费", "燃气", "物业", "宽带", "国家电网", "自如", "贝壳", "中国移动", "中国联通", "中国电信", "话费",
        ),
        Category.LEARNING to listOf("书店", "当当", "得到", "知乎", "网课", "培训", "考试", "学费", "慕课", "多邻国", "kindle", "微信读书"),
        Category.SHOPPING to listOf(
            "淘宝", "天猫", "京东", "拼多多", "唯品会", "得物", "闲鱼", "优衣库", "zara", "小米", "抖音商城", "衣", "鞋", "旗舰店",
        ),
    )

    fun categorize(merchant: String, note: String = ""): Category {
        val haystack = "$merchant $note".lowercase()
        return table.firstOrNull { (_, words) -> words.any { haystack.contains(it) } }?.first ?: Category.OTHER
    }
}
