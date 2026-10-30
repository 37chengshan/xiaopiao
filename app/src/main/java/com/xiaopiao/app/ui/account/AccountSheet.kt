package com.xiaopiao.app.ui.account

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import com.xiaopiao.app.ui.components.liquidGlass
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
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
    // The brand scene is recorded on its own; the form card is liquid glass that bends the slips behind its top
    // edge (tt 2026-10-30: 登录页做成液态玻璃的感觉). The page itself stays plain.
    val scene = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(XpColors.Canvas)) {
        Box(Modifier.fillMaxSize().layerBackdrop(scene).background(XpColors.Canvas)) {
            LoginScene(reduced, Modifier.align(Alignment.TopCenter).statusBarsPadding())
        }
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = XpSpace.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !busy) { Text(copy("返回", "Back"), color = XpColors.InkSecondary) }
            }
            Spacer(Modifier.height(SceneHeight - 48.dp))
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .graphicsLayer { alpha = entrance.value; translationY = if (reduced) 0f else (1f - entrance.value) * XpSpace.xl.toPx() }
                    .liquidGlass(
                        scene, androidx.compose.foundation.shape.RoundedCornerShape(XpRadius.xl),
                        film = XpColors.Card.copy(alpha = if (XpColors.isDark) 0.78f else 0.70f),
                        blur = 14.dp, lensHeight = 24.dp, lensAmount = 36.dp, shadow = 28.dp,
                    )
                    .padding(horizontal = XpSpace.xl, vertical = XpSpace.xl),
                verticalArrangement = Arrangement.spacedBy(XpSpace.m),
            ) {
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
                    if (formMode == "login") LoginField(identifier, { identifier = it }, label = { Text(copy("邮箱或用户名", "Email or username")) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (formMode in listOf("register", "forgot", "reset")) LoginField(email, { email = it }, label = { Text(copy("邮箱", "Email")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (formMode == "register") LoginField(username, { username = it }, label = { Text(copy("用户名", "Username")) }, supportingText = { Text(copy("3–30 位字母、数字或下划线，以字母开头", "3–30 letters, digits or underscores; start with a letter")) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (formMode in listOf("login", "register", "reset")) LoginField(password, { password = it }, label = { Text(copy("密码", "Password")) }, supportingText = { Text(copy("至少 15 个字符", "At least 15 characters")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (formMode in listOf("verify", "reset")) LoginField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text(copy("6 位邮箱验证码", "6-digit email code")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                }
                }
                error?.let { Text(it, color = XpColors.Negative, style = MaterialTheme.typography.bodySmall) }
                notice?.let { Text(it, color = XpColors.Positive, style = MaterialTheme.typography.bodySmall) }
                if (!client.configured) Text(copy("云端连接准备中，仍可关闭此页使用本机记账。", "Cloud connection is being prepared. You can still use the local ledger."), color = XpColors.InkSecondary)
                Button(onClick = { perform(if (user?.verified == true) "logout" else mode) }, enabled = !busy && client.configured, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().padding(top = XpSpace.xs).heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = XpColors.Primary, contentColor = XpColors.OnPrimary)) {
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
            Spacer(Modifier.height(XpSpace.xl))
        }
    }
}

/** Height of the brand scene above the form card: the mark, the wordmark and the slips the card overlaps. */
private val SceneHeight = 236.dp

/**
 * The sign-in scene: the launcher mark (coral tile, torn white receipt) and three receipt slips drifting behind
 * where the glass card begins. Static with reduced motion.
 */
@Composable
private fun LoginScene(reduced: Boolean, modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "loginDrift")
    val t by drift.animateFloat(
        0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse), label = "loginT",
    )
    val phase = if (reduced) 0.5f else t
    Box(modifier.fillMaxWidth().height(SceneHeight + 96.dp)) {
        // Slips first, so the mark sits above them.
        Slip(Modifier.align(Alignment.TopCenter).offset(x = (-92).dp, y = (150 + 6 * phase).dp), -14f + 3f * phase)
        Slip(Modifier.align(Alignment.TopCenter).offset(x = 96.dp, y = (164 - 8 * phase).dp), 11f - 2f * phase)
        Slip(Modifier.align(Alignment.TopCenter).offset(x = 6.dp, y = (196 + 5 * phase).dp), -3f + 2f * phase)
        Column(Modifier.align(Alignment.TopCenter).padding(top = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp)
                    .shadow(14.dp, androidx.compose.foundation.shape.RoundedCornerShape(18.dp), spotColor = XpColors.Brand)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .background(XpColors.Brand),
                contentAlignment = Alignment.Center,
            ) {
                // The launcher foreground keeps a 108dp safe zone; drawn larger so the receipt fills the tile.
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.xiaopiao.app.R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.requiredSize(104.dp),
                )
            }
            Spacer(Modifier.height(XpSpace.s))
            Text("今日小票", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
        }
    }
}

/** One receipt slip: paper, a few printed lines, a torn bottom edge. */
@Composable
private fun Slip(modifier: Modifier, angle: Float) {
    val shape = TornBottom
    Column(
        modifier.graphicsLayer { rotationZ = angle }
            .size(width = 96.dp, height = 124.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(XpColors.Paper)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        listOf(0.55f, 0.85f, 0.7f, 0.8f).forEach { w ->
            Box(Modifier.fillMaxWidth(w).height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(XpColors.PaperInk.copy(alpha = 0.16f)))
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth(0.45f).height(6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(XpColors.PaperInk.copy(alpha = 0.34f)))
    }
}

/** A rectangle whose bottom edge is a row of small teeth, like paper torn off a printer. */
private val TornBottom = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val tooth = size.width / 12f
    val depth = tooth * 0.55f
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height - depth)
    var x = size.width
    var down = true
    while (x > 0f) {
        x = (x - tooth / 2f).coerceAtLeast(0f)
        lineTo(x, if (down) size.height else size.height - depth)
        down = !down
    }
    close()
}

/** Filled, rounded field on the glass card: no outline, the fill lifts on focus. */
@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: (@Composable () -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        enabled = enabled,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = XpColors.Card,
            unfocusedContainerColor = XpColors.Fill.copy(alpha = 0.75f),
            disabledContainerColor = XpColors.Fill.copy(alpha = 0.5f),
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedLabelColor = XpColors.Accent,
            unfocusedLabelColor = XpColors.InkSecondary,
            cursorColor = XpColors.Accent,
            focusedTextColor = XpColors.Ink,
            unfocusedTextColor = XpColors.Ink,
            focusedSupportingTextColor = XpColors.InkTertiary,
            unfocusedSupportingTextColor = XpColors.InkTertiary,
        ),
        modifier = modifier,
    )
}
