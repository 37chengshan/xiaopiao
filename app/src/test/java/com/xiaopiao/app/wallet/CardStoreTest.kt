package com.xiaopiao.app.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.util.Base64

/** JVM stand-in for the Keystore: same AES-256-GCM format, key supplied by the test. */
private class FakeCipher(private val key: ByteArray = ByteArray(32) { it.toByte() }) : CardCipher {
    override fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val body = cipher.doFinal(plain.toByteArray())
        return Base64.getEncoder().encodeToString(iv + body)
    }

    override fun open(token: String): String? = runCatching {
        if (token.isEmpty()) return ""
        val packed = Base64.getDecoder().decode(token)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        String(cipher.doFinal(packed.copyOfRange(12, packed.size)))
    }.getOrNull()
}

private class FakeIo(var content: String? = null) : CardIo {
    override fun read(): String? = content
    override fun write(text: String) { content = text }
}

class CardStoreTest {
    private val pan = "6222021234567890"

    @Test fun cardNumberNeverLandsOnDiskInPlainText() {
        val io = FakeIo()
        val store = CardStore(io, FakeCipher(), clock = { 1L })
        store.saveBank(BankCard(id = "", bankName = "中国工商银行"), pan)

        val written = io.content.orEmpty()
        assertFalse("plain PAN must not be written", written.contains(pan))
        assertFalse(written.contains("6222 0212 3456 7890"))
        assertTrue(written.contains("panSealed"))
        assertEquals("7890", store.wallet.value.banks.single().last4)
    }

    @Test fun sealedPanOpensOnlyWithTheSameKey() {
        val io = FakeIo()
        val store = CardStore(io, FakeCipher(), clock = { 1L })
        val saved = store.saveBank(BankCard(id = "", bankName = "中国工商银行"), pan)
        assertEquals(pan, store.revealPan(saved))

        val stranger = CardStore(io, FakeCipher(ByteArray(32) { 7 }))
        stranger.load()
        assertNull(stranger.revealPan(stranger.wallet.value.banks.single()))
    }

    @Test fun editingOtherFieldsKeepsTheSealedPan() {
        val io = FakeIo()
        val store = CardStore(io, FakeCipher(), clock = { 1L })
        val saved = store.saveBank(BankCard(id = "", bankName = "中国工商银行"), pan)
        val renamed = store.saveBank(saved.copy(nickname = "工资卡"), pan = null)

        assertEquals("工资卡", renamed.nickname)
        assertEquals(saved.panSealed, renamed.panSealed)
        assertEquals(pan, store.revealPan(renamed))
        assertEquals(1, store.wallet.value.banks.size)
    }

    @Test fun replacingTheNumberResealsAndMovesTheTail() {
        val io = FakeIo()
        val store = CardStore(io, FakeCipher(), clock = { 1L })
        val saved = store.saveBank(BankCard(id = "", bankName = "中国工商银行"), pan)
        val updated = store.saveBank(saved, "6222029999888877")
        assertEquals("8877", updated.last4)
        assertNotEquals(saved.panSealed, updated.panSealed)
        assertEquals("6222029999888877", store.revealPan(updated))
    }

    @Test fun membersRoundTripAndDelete() {
        val io = FakeIo()
        val store = CardStore(io, FakeCipher(), clock = { 42L })
        val member = store.saveMember(MemberCard(id = "", storeName = "星巴克", memberNo = "88991234", points = 320))
        assertEquals(42L, member.createdAt)
        val reloaded = CardStore(io, FakeCipher())
        reloaded.load()
        assertEquals("星巴克", reloaded.wallet.value.members.single().storeName)
        assertEquals(320L, reloaded.wallet.value.members.single().points)

        reloaded.deleteMember(member.id)
        assertTrue(reloaded.wallet.value.members.isEmpty())
    }

    @Test fun corruptFileDegradesToAnEmptyWallet() {
        val io = FakeIo("{not json")
        val store = CardStore(io, FakeCipher())
        store.load()
        assertTrue(store.wallet.value.isEmpty)
    }
}
