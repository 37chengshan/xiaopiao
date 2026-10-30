package com.xiaopiao.app.domain

import org.junit.Assert.*
import org.junit.Test

class AccountValidationTest {
    private val valid = mapOf("email" to "qa@example.test", "username" to "test_user",
        "password" to "A test-only long password", "confirmPassword" to "A test-only long password")
    @Test fun emailInputNormalizesImePunctuationWithoutTouchingPasswords() {
        assertEquals("qa@example.test", AccountValidation.identifier(" QA@EXAMPLE。TEST "))
    }
    @Test fun invalidFieldsAreReportedIndividually() {
        val errors = AccountValidation.validate("register", valid + mapOf("email" to "invalid", "username" to "1?", "password" to "short"))
        assertEquals(setOf("email", "username", "password", "confirmPassword"), errors.keys)
    }
    @Test fun confirmationMustMatchAndDoesNotAcceptAnEmptyValue() {
        assertEquals(AccountIssue.PASSWORD_CONFIRM, AccountValidation.validate("register", valid + ("confirmPassword" to ""))["confirmPassword"])
        assertTrue(AccountValidation.validate("register", valid).isEmpty())
    }
    @Test fun loginDoesNotApplyNewPasswordLengthRulesToLegacyCredentials() {
        assertTrue(AccountValidation.validate("login", mapOf("identifier" to "owner", "password" to "testshort")).isEmpty())
    }
    @Test fun serverPolicyControlsLengthRatherThanHardcodedHint() {
        val short = valid + mapOf("password" to "testshort", "confirmPassword" to "testshort")
        assertEquals(AccountIssue.PASSWORD_LENGTH, AccountValidation.validate("register", short, AccountPolicy(passwordMin = 15))["password"])
        assertTrue(AccountValidation.validate("register", short, AccountPolicy(passwordMin = 8)).isEmpty())
    }
}
