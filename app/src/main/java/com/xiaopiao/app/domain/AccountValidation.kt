package com.xiaopiao.app.domain

data class AccountPolicy(val passwordMin: Int = 8, val passwordMax: Int = 128, val profileEditing: Boolean = false)
enum class AccountIssue { REQUIRED, EMAIL, USERNAME, PASSWORD_LENGTH, PASSWORD_CONFIRM, CODE }
object AccountValidation {
    fun identifier(value: String): String = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC)
        .replace('。', '.').replace('．', '.').lowercase()
    fun validate(mode: String, fields: Map<String, String>, policy: AccountPolicy = AccountPolicy()): Map<String, AccountIssue> {
        val errors = linkedMapOf<String, AccountIssue>()
        fun required(key: String) { if (fields[key].isNullOrBlank()) errors[key] = AccountIssue.REQUIRED }
        if (mode == "login") { required("identifier"); required("password") }
        if (mode in listOf("register", "forgot", "reset", "email-request")) {
            val email = identifier(fields["email"].orEmpty())
            if (email.length > 254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) errors["email"] = AccountIssue.EMAIL
        }
        if (mode in listOf("register", "profile")) {
            if (!Regex("^[a-z][a-z0-9_]{2,29}$").matches(fields["username"].orEmpty().trim().lowercase())) errors["username"] = AccountIssue.USERNAME
        }
        if (mode in listOf("register", "reset")) {
            val password = fields["password"].orEmpty()
            if (password.length !in policy.passwordMin..policy.passwordMax) errors["password"] = AccountIssue.PASSWORD_LENGTH
            if (fields["confirmPassword"].isNullOrEmpty() || fields["confirmPassword"] != password) errors["confirmPassword"] = AccountIssue.PASSWORD_CONFIRM
        }
        if (mode in listOf("verify", "reset", "email-confirm") && !Regex("[0-9]{6}").matches(fields["code"].orEmpty())) errors["code"] = AccountIssue.CODE
        if (mode == "email-request") required("currentPassword")
        return errors
    }
}
