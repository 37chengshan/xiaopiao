package com.xiaopiao.app.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardBinTest {
    @Test fun knownDebitBinIdentifiesBankAndDefaultFace() {
        val info = CardBin.identify("6222021234567890123")
        assertTrue(info.known)
        assertEquals("中国工商银行", info.bankName)
        assertEquals(BankCardType.Debit, info.type)
        assertEquals("银联", info.brand)
        assertEquals(CardFace.Ink, info.face)
    }

    @Test fun knownCreditBinCarriesTheCreditType() {
        val info = CardBin.identify("4391 8812 3456 7890")
        assertTrue(info.known)
        assertEquals("招商银行", info.bankName)
        assertEquals(BankCardType.Credit, info.type)
        assertEquals("VISA", info.brand)
    }

    @Test fun unknownPrefixStaysNeutralInsteadOfGuessing() {
        val info = CardBin.identify("6229990000000000")
        assertFalse(info.known)
        assertEquals("银行卡", info.bankName)
        assertEquals("银联", info.brand)
    }

    @Test fun maskingAndGrouping() {
        assertEquals("6222 0212 3456 7890", CardBin.groups("6222021234567890"))
        assertEquals("7890", CardBin.last4("6222 0212 3456 7890"))
        assertEquals("•••• •••• •••• 7890", CardBin.mask("7890"))
        assertEquals("•••• •••• •••• ••••", CardBin.mask(""))
    }

    @Test fun luhnSeparatesRealNumbersFromTypos() {
        assertTrue(CardBin.looksValid("4111111111111111"))
        assertTrue(CardBin.looksValid("4012888888881881"))
        assertFalse(CardBin.looksValid("4111111111111112"))
        assertFalse(CardBin.looksValid("6222021234567890"))   // Luhn 和为 56
        assertFalse(CardBin.looksValid("1234"))
    }
}
