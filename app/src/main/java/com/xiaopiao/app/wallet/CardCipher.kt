package com.xiaopiao.app.wallet

/**
 * 卡号加密的抽象：Android 侧是 Keystore（密钥不可导出），JVM 单测用一个注入密钥的实现，
 * 因此「落盘只有密文」这条规则可以在普通单测里断言（docs/APP-DESIGN.md §5.5）。
 */
interface CardCipher {
    /** 返回自描述密文（IV + 密文，Base64）；空字符串输入返回空串。 */
    fun seal(plain: String): String

    /** 打不开就返回 null —— 密钥换过 / 数据损坏时宁可让用户重录，也不抛到界面上。 */
    fun open(token: String): String?
}
