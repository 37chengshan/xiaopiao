package com.xiaopiao.app.ui.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.network.FoundCircle
import com.xiaopiao.app.network.JoinRequest
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.tr
import com.xiaopiao.app.ui.wallet.CardScannerSheet
import com.xiaopiao.app.ui.wallet.ScanMode
import kotlinx.coroutines.launch

/** The code inside an invite QR (`xiaopiao://circle/join?code=…`) or a bare typed / pasted code. */
internal fun inviteCodeFrom(raw: String): String? {
    val fromLink = Regex("[?&]code=([A-Za-z0-9]+)").find(raw)?.groupValues?.get(1)
    val code = normalizeInviteCode(fromLink ?: raw).filter { it.isLetterOrDigit() }
    return code.takeIf { it.length == INVITE_LENGTH }
}

/**
 * The three ways into a circle (tt 2026-11-02): an invite code, scanning the invite QR — both join at once — or
 * searching the circle's 7-digit number, which only *asks*; the owner lets you in.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CircleJoinSheet(onDismiss: () -> Unit, onJoined: (String) -> Unit) {
    val store = LocalCircleStore.current ?: return
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var code by remember { mutableStateOf("") }
    var no by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<FoundCircle?>(null) }
    var scanning by remember { mutableStateOf(false) }

    fun messageOf(e: Throwable): String = when ((e as? com.xiaopiao.app.network.ApiException)?.code) {
        "INVITE_INVALID" -> tr("邀请码无效或已过期", "That code is invalid or expired")
        "CIRCLE_NOT_FOUND" -> tr("没有这个圈号", "No circle with that number")
        "CIRCLE_FULL" -> tr("这个圈子满员了", "That circle is full")
        "CIRCLE_LIMIT" -> tr("你已经加入了 10 个圈子", "You're already in 10 circles")
        "RATE_LIMITED" -> tr("操作太频繁，稍后再试", "Too many tries — wait a bit")
        else -> e.message ?: tr("连接失败，请重试", "Connection failed. Try again")
    }

    fun join(raw: String) {
        val c = inviteCodeFrom(raw) ?: run { error = tr("不是有效的圈子邀请", "Not a valid circle invite"); return }
        busy = true; error = null
        scope.launch {
            runCatching { store.join(c) }.onSuccess { onJoined(it) }.onFailure { error = messageOf(it) }
            busy = false
        }
    }

    XpSheet(onDismiss = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l).imePadding()) {
            Text(tr("加入圈子", "Join a circle"), style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            Spacer(Modifier.height(XpSpace.m))
            Segmented(listOf(tr("邀请码", "Invite code"), tr("圈号", "Circle number")), tab, onSelect = { tab = it; error = null })
            Spacer(Modifier.height(XpSpace.l))
            if (tab == 0) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = normalizeInviteCode(it).filter(Char::isLetterOrDigit).take(INVITE_LENGTH); error = null },
                    label = { Text(tr("8 位邀请码", "8-character code")) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(XpRadius.m),
                )
                Row(Modifier.fillMaxWidth().padding(top = XpSpace.m), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    XpButton(tr("扫码", "Scan QR"), { scanning = true }, style = XpButtonStyle.Secondary, enabled = !busy, modifier = Modifier.weight(1f))
                    XpButton(
                        if (busy) tr("加入中…", "Joining…") else tr("加入", "Join"), { join(code) },
                        enabled = !busy && code.length == INVITE_LENGTH, modifier = Modifier.weight(1f),
                    )
                }
            } else {
                OutlinedTextField(
                    value = no,
                    onValueChange = { no = it.filter(Char::isDigit).take(7); error = null; found = null },
                    label = { Text(tr("7 位圈号", "7-digit circle number")) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(XpRadius.m),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                XpButton(
                    if (busy && found == null) tr("搜索中…", "Searching…") else tr("搜索", "Search"),
                    {
                        busy = true; error = null; found = null
                        scope.launch {
                            runCatching { store.search(no) }.onSuccess { found = it }.onFailure { error = messageOf(it) }
                            busy = false
                        }
                    },
                    enabled = !busy && no.length == 7, style = XpButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth().padding(top = XpSpace.m),
                )
                found?.let { c ->
                    FoundCircleCard(
                        circle = c,
                        busy = busy,
                        onRequest = {
                            busy = true; error = null
                            scope.launch {
                                runCatching { store.requestJoin(c.no) }
                                    .onSuccess { status -> found = c.copy(status = status); if (status == "member") store.refresh() }
                                    .onFailure { error = messageOf(it) }
                                busy = false
                            }
                        },
                    )
                }
                Text(
                    tr("圈号只能找到圈子并申请，圈主同意后才能进。", "A number only lets you ask; the owner lets you in."),
                    style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary,
                    modifier = Modifier.padding(top = XpSpace.m),
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = XpColors.Negative, modifier = Modifier.padding(top = XpSpace.s)) }
        }
    }

    if (scanning) {
        CardScannerSheet(
            mode = ScanMode.CircleInvite,
            onDismiss = { scanning = false },
            onScanned = { raw -> scanning = false; inviteCodeFrom(raw)?.let { code = it }; join(raw) },
        )
    }
}

@Composable
private fun FoundCircleCard(circle: FoundCircle, busy: Boolean, onRequest: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = XpSpace.m).clip(RoundedCornerShape(XpRadius.m)).background(XpColors.Fill)
            .padding(XpSpace.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(circle.name, style = MaterialTheme.typography.titleSmall, color = XpColors.Ink)
            Text(tr("圈号 ${circle.no} · ${circle.memberCount} 人", "No. ${circle.no} · ${circle.memberCount} members"), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        }
        when (circle.status) {
            "member" -> Text(tr("已在圈子里", "Already a member"), style = MaterialTheme.typography.labelLarge, color = XpColors.Positive)
            "pending" -> Text(tr("已申请，等圈主同意", "Requested — waiting"), style = MaterialTheme.typography.labelLarge, color = XpColors.InkSecondary)
            else -> XpButton(tr("申请加入", "Ask to join"), onRequest, enabled = !busy, size = XpButtonSize.Small)
        }
    }
}

/** The owner's inbox of people who asked to join by circle number. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CircleRequestsSheet(circle: Circle, onDismiss: () -> Unit) {
    val store = LocalCircleStore.current ?: return
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf<List<JoinRequest>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deciding by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(circle.id) {
        runCatching { store.requests(circle.id) }.onSuccess { requests = it }.onFailure { error = it.message ?: tr("加载失败", "Couldn't load") }
    }
    XpSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l)) {
            Text(tr("加入申请", "Join requests"), style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            Text(tr("「${circle.name}」· 圈号 ${circle.no}", "“${circle.name}” · No. ${circle.no}"), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            Spacer(Modifier.height(XpSpace.m))
            val list = requests
            when {
                list == null && error == null -> CircularProgressIndicator(color = XpColors.Accent, modifier = Modifier.align(Alignment.CenterHorizontally))
                list.isNullOrEmpty() && error == null -> Text(tr("没有待处理的申请", "No pending requests"), color = XpColors.InkSecondary, modifier = Modifier.padding(vertical = XpSpace.l))
                else -> list.orEmpty().forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
                        CircleAvatarInitial(r.username)
                        Text(r.username, style = MaterialTheme.typography.bodyLarge, color = XpColors.Ink, modifier = Modifier.weight(1f).padding(start = XpSpace.m))
                        val busy = deciding == r.userId
                        fun decide(approve: Boolean) {
                            deciding = r.userId; error = null
                            scope.launch {
                                runCatching { store.decide(circle.id, r.userId, approve) }
                                    .onSuccess { requests = requests.orEmpty().filterNot { it.userId == r.userId } }
                                    .onFailure { error = it.message ?: tr("操作失败，请重试", "Failed. Try again") }
                                deciding = null
                            }
                        }
                        XpButton(tr("拒绝", "Decline"), { decide(false) }, style = XpButtonStyle.Secondary, size = XpButtonSize.Small, enabled = deciding == null)
                        Spacer(Modifier.size(XpSpace.s))
                        XpButton(if (busy) "…" else tr("同意", "Approve"), { decide(true) }, size = XpButtonSize.Small, enabled = deciding == null)
                    }
                }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = XpColors.Negative, modifier = Modifier.padding(top = XpSpace.s)) }
        }
    }
}

@Composable
private fun CircleAvatarInitial(name: String) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(36.dp).clip(CircleShape).background(XpColors.AccentSoft),
        contentAlignment = Alignment.Center,
    ) { Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = XpColors.Accent) }
}
