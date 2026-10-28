package com.xiaopiao.app.wallet

import com.xiaopiao.app.domain.Cents
import kotlinx.serialization.Serializable

/**
 * 卡面预设（docs/APP-DESIGN.md §5.5）. 卡面是全局唯一允许出现大面积色块的地方，所以颜色放在这里，
 * 不进 `XpColors`；其余界面依旧只用系统灰白。
 */
@Serializable
enum class CardFace(val label: String, val argb: Long, val onArgb: Long) {
    Ink("墨黑", 0xFF161618, 0xFFFFFFFF),
    Mist("雾蓝", 0xFF9FB6CF, 0xFFFFFFFF),
    Mint("薄荷", 0xFF74E2EF, 0xFF0C2B2F),
    Coral("珊瑚", 0xFFF2A792, 0xFF3A1A12),
    Champagne("香槟金", 0xFFE0C691, 0xFF3A2C12),
    Aurora("极光", 0xFF8E9BF5, 0xFFFFFFFF),
}

@Serializable
enum class BankCardType(val label: String) { Debit("储蓄卡"), Credit("信用卡") }

@Serializable
enum class MemberCodeFormat(val label: String) { Qr("二维码"), Code128("条形码") }

/** 银行卡：完整卡号只以 [panSealed]（AES-GCM 密文）落盘，内存里也只在用户验证后短暂解密。 */
@Serializable
data class BankCard(
    val id: String,
    val nickname: String = "",
    val bankName: String = "未知银行",
    val type: BankCardType = BankCardType.Debit,
    val last4: String = "",
    /** 卡组织字样（VISA / 银联 …），录入时由 BIN 给出，卡面右下角直接用它。 */
    val brand: String = "银联",
    val panSealed: String = "",
    val face: CardFace = CardFace.Ink,
    val showBankName: Boolean = true,
    /** 信用卡才用：账单日 / 还款日（1–28，0 = 未设置）。 */
    val statementDay: Int = 0,
    val dueDay: Int = 0,
    val createdAt: Long = 0,
) {
    val displayName: String get() = nickname.ifBlank { bankName }
    val hasPan: Boolean get() = panSealed.isNotEmpty()
}

/** 会员卡：店名 + 会员号，条码/二维码只在本机渲染。 */
@Serializable
data class MemberCard(
    val id: String,
    val storeName: String,
    val memberNo: String,
    val face: CardFace = CardFace.Mint,
    val codeFormat: MemberCodeFormat = MemberCodeFormat.Qr,
    val points: Long = 0,
    val balanceCents: Cents = 0,
    val note: String = "",
    /** 0 = 不设到期提醒。 */
    val expiryAt: Long = 0,
    val createdAt: Long = 0,
)

@Serializable
data class Wallet(
    val version: Int = 1,
    val banks: List<BankCard> = emptyList(),
    val members: List<MemberCard> = emptyList(),
) {
    val isEmpty: Boolean get() = banks.isEmpty() && members.isEmpty()
}

/** 本月在这张卡上花了多少（账单按尾号归卡，§5.5）。 */
data class CardSpend(val cents: Cents, val count: Int)

/** 30 秒显示窗口 + 60 秒剪贴板清理，抽成常量便于单测与文案复用。 */
object RevealWindow {
    const val VISIBLE_MS = 30_000L
    const val CLIP_CLEAR_MS = 60_000L

    fun visible(startedAt: Long, now: Long): Boolean = now >= startedAt && now - startedAt < VISIBLE_MS
    fun shouldClearClipboard(copiedAt: Long, now: Long): Boolean = now >= copiedAt && now - copiedAt >= CLIP_CLEAR_MS
}
