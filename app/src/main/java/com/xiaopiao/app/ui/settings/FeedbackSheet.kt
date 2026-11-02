package com.xiaopiao.app.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.BuildConfig
import com.xiaopiao.app.network.AccountClient
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 一键反馈给开发者 (tt 2026-11-02). Notification text never leaves the phone on its own (AGENTS.md data rule), so
 * this sheet shows *exactly* what will be sent — editable — and only the user's tap sends it.
 * [attached] is the unrecognised notification, if the sheet was opened from the capture log.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSheet(client: AccountClient, attached: String?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf(attached.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val device = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
    val version = BuildConfig.VERSION_NAME

    XpSheet(onDismiss = { if (!busy) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l).imePadding(),
        ) {
            Text(tr("反馈给开发者", "Report to the developer"), style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            Text(
                tr("下面就是会发出去的全部内容，可以先删改。点发送才会上传。", "Below is everything that will be sent — edit it first if you like. Nothing goes until you tap Send."),
                style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary, modifier = Modifier.padding(top = 2.dp),
            )
            OutlinedTextField(
                note, { note = it.take(1000) },
                label = { Text(tr("遇到了什么问题？（可选）", "What went wrong? (optional)")) },
                modifier = Modifier.fillMaxWidth().padding(top = XpSpace.m).heightIn(min = 96.dp),
                enabled = !busy && !sent, shape = RoundedCornerShape(XpRadius.m),
            )
            if (attached != null) {
                Text(tr("附带的通知原文", "The notification text"), style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, modifier = Modifier.padding(top = XpSpace.m))
                OutlinedTextField(
                    payload, { payload = it.take(2000) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    enabled = !busy && !sent, shape = RoundedCornerShape(XpRadius.m),
                )
            }
            Text(
                tr("同时附上：版本 $version · $device", "Also attached: version $version · $device"),
                style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary,
                modifier = Modifier.padding(top = XpSpace.s).background(XpColors.Fill, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = XpColors.Negative, modifier = Modifier.padding(top = XpSpace.s)) }
            XpButton(
                when {
                    sent -> tr("已发送，谢谢！", "Sent — thank you!")
                    busy -> tr("发送中…", "Sending…")
                    else -> tr("发送", "Send")
                },
                {
                    busy = true; error = null
                    scope.launch {
                        runCatching {
                            client.request("/v1/feedback", buildJsonObject {
                                put("kind", if (attached != null) "capture_miss" else "general")
                                put("text", listOf(note.trim(), payload.trim()).filter { it.isNotEmpty() }.joinToString("\n---\n"))
                                put("appVersion", version)
                                put("device", device)
                            })
                        }.onSuccess { sent = true; delay(1200); onDismiss() }
                         .onFailure { error = it.message ?: tr("发送失败，请重试", "Couldn't send. Try again") }
                        busy = false
                    }
                },
                enabled = !busy && !sent && (note.isNotBlank() || payload.isNotBlank()),
                modifier = Modifier.fillMaxWidth().padding(top = XpSpace.l),
            )
        }
    }
}
