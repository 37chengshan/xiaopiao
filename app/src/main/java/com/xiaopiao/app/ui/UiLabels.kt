package com.xiaopiao.app.ui

/** Static chrome only. Never translate merchant names, user text or other ledger data. */
object UiLabels {
    fun title(value: String): String = if (java.util.Locale.getDefault().language != "en") value else when (value) {
        "记账" -> "Ledger"; "小票" -> "Receipt"; "统计" -> "Insights"; "圈子" -> "Circles"; "游戏" -> "Games"
        "设置" -> "Settings"; "返回" -> "Back"; "取消" -> "Cancel"; "账号" -> "Account"
        "外观" -> "Appearance"; "语音模型" -> "Voice models"; "数据与隐私" -> "Data & privacy"; "关于" -> "About"
        "预算" -> "Budget"; "每日预算" -> "Daily budget"; "每周预算" -> "Weekly budget"; "每月预算" -> "Monthly budget"
        "分类预算" -> "Category budgets"; "编辑" -> "Edit"; "添加" -> "Add"; "保存" -> "Save"; "清除" -> "Clear"
        "打开 App 时进入" -> "Start page"; "主题配色" -> "Accent color"; "自动记账" -> "Automatic capture"
        "引导" -> "Getting started"; "AI 记账" -> "AI entries"; "搜索" -> "Search"; "每周小票" -> "Weekly receipts"
        "订阅侦探" -> "Subscriptions"; "后悔模拟" -> "What-if spending"; "卡包" -> "Wallet"
        "主题与配色" -> "Theme & colors"
        "导入账单、通知记录、清空数据" -> "Import bills, view capture history, or clear data"
        "数据" -> "Data"
        "跟随系统、浅色、深色；五套主题配色" -> "System, light or dark; five color themes"
        "隐私说明 · 版本" -> "Privacy & version"
        "打印音效" -> "Printing sound"
        "通知读取" -> "Payment notifications"
        "小票提醒" -> "Receipt reminders"
        "重看引导" -> "Replay onboarding"
        "了解更多" -> "Learn more"
        "版本" -> "Version"
        "隐私说明" -> "Privacy"
        "未开启" -> "Off"
        "已开启" -> "On"
        "跟随系统" -> "System"
        "浅色" -> "Light"
        "深色" -> "Dark"
        "晴空" -> "Sky"
        "樱花" -> "Blossom"
        "薄荷" -> "Mint"
        "暖沙" -> "Sand"
        "墨色" -> "Ink"
        else -> value
    }
}
