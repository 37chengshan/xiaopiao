package com.xiaopiao.app.ui.account

import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.xiaopiao.app.ui.motion.XpTiming
import com.xiaopiao.app.ui.components.rememberReducedMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaopiao.app.network.AccountClient
import com.xiaopiao.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AccountSheet(client: AccountClient, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AccountPage(client, onDismiss)
    }
}

/** The same optional account page is used in settings, onboarding, and the chat drawer. */
@Composable
fun AccountPage(client: AccountClient, onDismiss: () -> Unit) {
    val user by client.user.collectAsState()
    val reduced = rememberReducedMotion()
    val entrance = remember { Animatable(0.7f) }
    LaunchedEffect(reduced) { if (reduced) entrance.snapTo(1f) else entrance.animateTo(1f, tween(XpTiming.PushIn, easing = XpEase.out)) }
    val scope = rememberCoroutineScope()
    val english = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "en"
    fun copy(zh: String, en: String) = if (english) en else zh
    var mode by rememberSaveable { mutableStateOf(if (user != null && user?.verified == false) "verify" else "login") }
    var identifier by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun perform(action: String = mode) {
        if (busy) return
        busy = true; error = null; notice = null
        scope.launch {
            try {
                val fields = when (action) {
                    "login" -> mapOf("identifier" to identifier, "password" to password)
                    "register" -> mapOf("email" to email, "username" to username, "password" to password)
                    "verify" -> mapOf("code" to code)
                    "forgot" -> mapOf("email" to email)
                    "reset" -> mapOf("email" to email, "code" to code, "password" to password)
                    else -> emptyMap()
                }
                if (action == "logout") client.logout() else client.auth(action, fields)
                password = ""
                when (action) {
                    "register" -> { mode = "verify"; notice = copy("验证码已发送到邮箱", "Check your email for a code") }
                    "forgot" -> { mode = "reset"; notice = copy("如果邮箱已注册，我们会发送验证码", "If registered, a code will arrive by email") }
                    "reset" -> { mode = "login"; notice = copy("密码已更新，请重新登录", "Password updated. Please sign in") }
                    "resend" -> notice = copy("验证码已发送", "Code sent")
                    "login" -> if (client.user.value?.verified == false) mode = "verify"
                    "logout" -> mode = "login"
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: copy("连接失败，请重试", "Connection failed. Try again") }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { runCatching { client.refresh() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(XpColors.Canvas).systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(XpSpace.l).graphicsLayer { alpha = entrance.value; translationY = if (reduced) 0f else (1f - entrance.value) * XpSpace.xl.toPx() }, shape = androidx.compose.foundation.shape.RoundedCornerShape(XpRadius.xl), color = XpColors.Canvas) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(XpSpace.xl), verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("今日小票", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = XpColors.InkSecondary)
                    TextButton(onClick = onDismiss, enabled = !busy) { Text(copy("返回", "Back")) }
                }
                Text(if (user?.verified == true) copy("你好，${user?.username}", "Hello, ${user?.username}") else when (mode) {
                    "register" -> copy("让日常，有迹可循。", "A little more clarity, every day.")
                    "verify" -> copy("验证你的邮箱", "Verify your email")
                    "forgot", "reset" -> copy("找回你的账号", "Recover your account")
                    else -> copy("欢迎回来", "Welcome back")
                }, style = MaterialTheme.typography.headlineSmall, color = XpColors.Ink)
                Text(if (user?.verified == true) user!!.email else copy("一份账号，连接手机与网页。账本原文留在本机。", "One account for phone and web. Original receipts stay on your device."), color = XpColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
                AnimatedContent(targetState = if (user?.verified == true) "account" else mode,
                    transitionSpec = { ((fadeIn(tween(XpTiming.PopOut, easing = XpEase.out)) + slideInVertically(tween(if (reduced) 0 else XpTiming.PopOut, easing = XpEase.out)) { if (reduced) 0 else it / 20 }) togetherWith fadeOut(tween(if (reduced) 0 else XpTiming.Press))).using(SizeTransform(sizeAnimationSpec = { _, _ -> tween(if (reduced) 0 else XpTiming.PopOut, easing = XpEase.out) })) }, label = "accountFields") { formMode ->
                    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                    if (formMode == "login") OutlinedTextField(identifier, { identifier = it }, label = { Text(copy("邮箱或用户名", "Email or username")) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (formMode in listOf("register", "forgot", "reset")) OutlinedTextField(email, { email = it }, label = { Text(copy("邮箱", "Email")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (formMode == "register") OutlinedTextField(username, { username = it }, label = { Text(copy("用户名", "Username")) }, supportingText = { Text(copy("3–30 位字母、数字或下划线，以字母开头", "3–30 letters, digits or underscores; start with a letter")) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (formMode in listOf("login", "register", "reset")) OutlinedTextField(password, { password = it }, label = { Text(copy("密码", "Password")) }, supportingText = { Text(copy("至少 15 个字符", "At least 15 characters")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (formMode in listOf("verify", "reset")) OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text(copy("6 位邮箱验证码", "6-digit email code")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                }
                }
                error?.let { Text(it, color = XpColors.Negative, style = MaterialTheme.typography.bodySmall) }
                notice?.let { Text(it, color = XpColors.Positive, style = MaterialTheme.typography.bodySmall) }
                if (!client.configured) Text(copy("云端连接准备中，仍可关闭此页使用本机记账。", "Cloud connection is being prepared. You can still use the local ledger."), color = XpColors.InkSecondary)
                Button(onClick = { perform(if (user?.verified == true) "logout" else mode) }, enabled = !busy && client.configured, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = XpColors.Primary, contentColor = XpColors.OnPrimary)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = XpColors.OnPrimary, strokeWidth = 2.dp)
                    else Text(if (user?.verified == true) copy("退出登录", "Sign out") else when (mode) {
                        "register" -> copy("创建账号", "Create account"); "verify" -> copy("验证邮箱", "Verify email")
                        "forgot" -> copy("发送验证码", "Send code"); "reset" -> copy("重设密码", "Reset password"); else -> copy("登录", "Sign in")
                    })
                }
                if (user?.verified != true) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { error = null; mode = if (mode == "login") "register" else "login" }, enabled = !busy) { Text(if (mode == "login") copy("创建账号", "Create account") else copy("返回登录", "Back to sign in")) }
                    TextButton(onClick = { if (mode == "verify") perform("resend") else { mode = "forgot"; error = null } }, enabled = !busy && client.configured) { Text(if (mode == "verify") copy("重新发送", "Resend code") else copy("忘记密码？", "Forgot password?")) }
                }
                if (user == null) TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(copy("暂不登录，继续记账", "Continue without signing in"), color = XpColors.InkSecondary)
                }
            }
        }
    }
}
