package com.xiaopiao.app.ui.account

import com.xiaopiao.app.ui.components.pressableClick
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.AccountValidation
import com.xiaopiao.app.network.AccountClient
import com.xiaopiao.app.network.ApiException
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AccountPasswordField(value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, error: String? = null, hint: String? = null, enabled: Boolean = true) {
    var shown by remember { mutableStateOf(false) }
    val en = LocalConfiguration.current.locales[0].language == "en"
    OutlinedTextField(value, onChange, modifier, enabled = enabled, singleLine = true,
        shape = RoundedCornerShape(XpRadius.m), label = { Text(label) }, placeholder = { Text(label) }, isError = error != null,
        supportingText = if (error != null || hint != null) ({ Text(error ?: hint.orEmpty()) }) else null,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { IconButton(onClick = { shown = !shown }, enabled = enabled) {
            Icon(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (shown) if (en) "Hide password" else "隐藏密码" else if (en) "Show password" else "显示密码")
        } })
}

@Composable
internal fun AccountProfilePage(client: AccountClient, onDismiss: () -> Unit) {
    val user by client.user.collectAsState()
    val policy by client.policy.collectAsState()
    val en = LocalConfiguration.current.locales[0].language == "en"
    fun copy(zh: String, english: String) = if (en) english else zh
    val scope = rememberCoroutineScope()
    var username by remember(user?.id) { mutableStateOf(user?.username.orEmpty()) }
    var email by remember(user?.id) { mutableStateOf(user?.email.orEmpty()) }
    var currentPassword by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saved by remember { mutableStateOf(false) }
    var avatarOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { client.refreshPolicy(); runCatching { client.refresh() } }
    BackHandler { if (!busy) onDismiss() }
    fun save() {
        val emailChanged = email.trim().lowercase() != user?.email
        val action = if (pending != null) "email-confirm" else if (emailChanged) "email-request" else "profile"
        val fields = mapOf("username" to username, "email" to email.trim(), "currentPassword" to currentPassword, "code" to code)
        val invalid = AccountValidation.validate(if (pending == null) "profile" else action, fields, policy) +
            if (emailChanged && pending == null) AccountValidation.validate("email-request", fields, policy) else emptyMap()
        errors = invalid.mapValues { copy("请检查此项", "Check this field") }
        if (errors.isNotEmpty() || busy) return
        busy = true; message = null; saved = false
        scope.launch {
            try {
                if (pending == null && username.trim().lowercase() != user?.username) client.auth("profile", mapOf("username" to username))
                if (action != "profile") client.auth(action, if (pending == null) mapOf("email" to email.trim(), "currentPassword" to currentPassword) else mapOf("code" to code))
                if (action == "email-request") { pending = email.trim(); message = copy("验证码已发往新邮箱，验证后才会修改。", "Code sent to the new address. Verify it to finish.") }
                else { pending = null; username = client.user.value?.username.orEmpty(); email = client.user.value?.email.orEmpty(); saved = true; message = copy("资料已保存", "Profile saved") }
                currentPassword = ""; code = ""
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val field = (e as? ApiException)?.field ?: if ((e as? ApiException)?.code == "CODE_INVALID") "code" else null
                if (field != null) errors = mapOf(field to (e.message ?: copy("请检查此项", "Check this field"))) else message = e.message
            } finally { busy = false }
        }
    }
    AccountBackdrop {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(XpSpace.page),
            verticalArrangement = Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(copy("个人资料", "Profile"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                AccountClose(onClick = { if (!busy) onDismiss() }, description = copy("返回", "Back"))
            }
            Column(Modifier.fillMaxWidth().accountGlass(RoundedCornerShape(XpRadius.xl)).padding(XpSpace.xl), verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                // Avatar: tap to pick a preset or a photo (saved at once; the sheet reports the result).
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalAccount provides client) {
                        XpAvatar(
                            user?.avatar, user?.username.orEmpty(), size = 76.dp,
                            modifier = Modifier.pressableClick(scaleDown = 0.94f) { avatarOpen = true },
                        )
                    }
                    TextButton(onClick = { avatarOpen = true }) { Text(copy("更换头像", "Change avatar")) }
                }
                OutlinedTextField(username, { username = it; errors = errors - "username" }, label = { Text(copy("用户名", "Username")) },
                    isError = "username" in errors, supportingText = { Text(errors["username"] ?: copy("3–30 位字母、数字或下划线，以字母开头", "3–30 letters, digits or underscores; start with a letter")) },
                    singleLine = true, enabled = !busy && pending == null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(XpRadius.m))
                OutlinedTextField(email, { email = it; errors = errors - "email" }, label = { Text(copy("邮箱", "Email")) },
                    isError = "email" in errors, supportingText = { Text(errors["email"] ?: copy("更换邮箱需要当前密码和新邮箱验证码。", "Changing email requires your current password and a code sent to the new address.")) },
                    singleLine = true, enabled = !busy && pending == null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(XpRadius.m), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                if (pending != null) OutlinedTextField(code, { code = it.filter { c -> c in '0'..'9' }.take(6); errors = errors - "code" },
                    label = { Text(copy("新邮箱验证码", "New email code")) }, singleLine = true, enabled = !busy, isError = "code" in errors,
                    supportingText = { errors["code"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                else if (email.trim().lowercase() != user?.email) AccountPasswordField(currentPassword, { currentPassword = it; errors = errors - "currentPassword" },
                    copy("当前密码", "Current password"), Modifier.fillMaxWidth(), errors["currentPassword"], enabled = !busy)
                message?.let { Text(it, color = if (saved) XpColors.Positive else XpColors.InkSecondary, style = MaterialTheme.typography.bodySmall) }
                if (!policy.profileEditing) Text(copy("资料编辑服务正在更新，请稍后再试。", "Profile editing is being updated. Please try again shortly."), color = XpColors.InkSecondary)
                Button(onClick = ::save, enabled = !busy && policy.profileEditing && (pending != null || username != user?.username || email != user?.email),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = XpColors.Primary, contentColor = XpColors.OnPrimary)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = XpColors.OnPrimary, strokeWidth = 2.dp)
                    else Text(if (pending != null) copy("验证并修改邮箱", "Verify and update email") else copy("保存修改", "Save changes"))
                }
                if (pending != null) TextButton(onClick = { pending = null; code = ""; message = null; errors = emptyMap() }, enabled = !busy) { Text(copy("重新填写邮箱", "Use another address")) }
            }
        }
    }
    if (avatarOpen) {
        androidx.compose.runtime.CompositionLocalProvider(LocalAccount provides client) {
            AvatarPickerSheet(client, user?.avatar, onDismiss = { avatarOpen = false })
        }
    }
}
