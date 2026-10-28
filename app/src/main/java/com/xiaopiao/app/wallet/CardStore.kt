package com.xiaopiao.app.wallet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/** Storage seam so the store can be exercised on the JVM without touching the filesystem. */
interface CardIo {
    fun read(): String?
    fun write(text: String)
}

/**
 * 卡包仓库（docs/APP-DESIGN.md §5.5）：一份本机 JSON，银行卡只写 [BankCard.panSealed]，完整卡号从不落盘。
 * 会员卡的商户名 / 会员号属于可展示信息，仍放在 app 私有目录，并被备份规则排除。
 */
class CardStore(
    private val io: CardIo,
    private val cipher: CardCipher,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _wallet = MutableStateFlow(Wallet())
    val wallet: StateFlow<Wallet> = _wallet

    fun load() {
        val raw = runCatching { io.read() }.getOrNull()
        _wallet.value = if (raw.isNullOrBlank()) {
            Wallet()
        } else {
            runCatching { json.decodeFromString<Wallet>(raw) }.getOrDefault(Wallet())
        }
    }

    /** [pan] 为空表示只改其他字段（不改卡号）。返回落盘后的卡。 */
    fun saveBank(card: BankCard, pan: String? = null): BankCard {
        val sealed = when {
            pan == null -> card.panSealed
            pan.isBlank() -> ""
            else -> cipher.seal(CardBin.digitsOnly(pan))
        }
        val digits = CardBin.digitsOnly(pan.orEmpty())
        val merged = card.copy(
            id = card.id.ifBlank { newId() },
            panSealed = sealed,
            last4 = if (digits.length >= 4) CardBin.last4(digits) else card.last4,
            createdAt = card.createdAt.takeIf { it > 0 } ?: clock(),
        )
        persist(_wallet.value.copy(banks = _wallet.value.banks.upsert(merged) { it.id }))
        return merged
    }

    fun saveMember(card: MemberCard): MemberCard {
        val merged = card.copy(
            id = card.id.ifBlank { newId() },
            createdAt = card.createdAt.takeIf { it > 0 } ?: clock(),
        )
        persist(_wallet.value.copy(members = _wallet.value.members.upsert(merged) { it.id }))
        return merged
    }

    fun deleteBank(id: String) {
        persist(_wallet.value.copy(banks = _wallet.value.banks.filterNot { it.id == id }))
    }

    fun deleteMember(id: String) {
        persist(_wallet.value.copy(members = _wallet.value.members.filterNot { it.id == id }))
    }

    /** 只在用户通过设备验证后调用；返回 null 表示密文打不开。 */
    fun revealPan(card: BankCard): String? =
        if (card.panSealed.isEmpty()) null else cipher.open(card.panSealed)

    /** 单测用：磁盘上真实写下的内容。 */
    fun rawJson(): String = json.encodeToString(_wallet.value)

    private fun persist(next: Wallet) {
        io.write(json.encodeToString(next))
        _wallet.value = next
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun <T> List<T>.upsert(item: T, id: (T) -> String): List<T> =
        if (any { id(it) == id(item) }) map { if (id(it) == id(item)) item else it } else this + item
}
