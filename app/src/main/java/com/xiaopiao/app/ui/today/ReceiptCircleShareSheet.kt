package com.xiaopiao.app.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.xiaopiao.app.domain.DailyReceipt
import com.xiaopiao.app.domain.ReceiptCirclePayload
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.network.AccountClient
import com.xiaopiao.app.ui.account.AccountSheet
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private data class ShareCircle(val id: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceiptCircleShareSheet(client: AccountClient, receipt: DailyReceipt, onDismiss: () -> Unit, onPosted: (String) -> Unit) {
    val user by client.user.collectAsState()
    val en = LocalConfiguration.current.locales[0].language == "en"
    fun copy(zh: String, english: String) = if (en) english else zh
    val scope = rememberCoroutineScope()
    var circles by remember { mutableStateOf<List<ShareCircle>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var includeCategories by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(user?.id, user?.verified, generation) {
        circles = emptyList(); selected = null; error = null
        if (user?.verified != true) return@LaunchedEffect
        loading = true
        try {
            circles = client.request("/v1/circles").getValue("circles").jsonArray.map { item ->
                val obj = item.jsonObject
                ShareCircle(obj.getValue("id").jsonPrimitive.content, obj.getValue("name").jsonPrimitive.content)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: copy("圈子加载失败，请重试", "Could not load circles. Try again.") }
        finally { loading = false }
    }
    XpSheet(onDismiss = { if (!posting) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = XpSpace.page).padding(bottom = XpSpace.xl).imePadding(),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
            Text(copy("发到圈子", "Share to a circle"), style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            val date = ReceiptCirclePayload.build(receipt, "", false).getValue("receipt").jsonObject.getValue("date").jsonPrimitive.content
            Text("$date · ¥${receipt.totalOutCents.yuan()} · ${receipt.items.size}", color = XpColors.Ink)
            Text(copy("默认只分享日期、合计和笔数，不包含商户或逐笔明细。", "Only the date, total and count are shared by default. No merchants or individual transactions."), color = XpColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            when {
                user?.verified != true -> {
                    Text(copy("请先登录并验证邮箱，再选择你的圈子。", "Sign in and verify your email to choose a circle."), color = XpColors.InkSecondary)
                    Button(onClick = { accountOpen = true }) { Text(copy("登录 / 验证邮箱", "Sign in / Verify email")) }
                }
                loading -> CircularProgressIndicator()
                error != null && circles.isEmpty() -> TextButton(onClick = { generation++ }) { Text(copy("重新加载圈子", "Reload circles")) }
                circles.isEmpty() -> {
                    Text(copy("还没有可发布的圈子，可以先创建一个。", "No circles yet. Create one to share your receipt."), color = XpColors.InkSecondary)
                    OutlinedTextField(name, { name = it.take(20) }, label = { Text(copy("圈子名称", "Circle name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = !posting && name.isNotBlank(), onClick = {
                        posting = true; error = null
                        scope.launch {
                            try { client.request("/v1/circles", buildJsonObject { put("name", name.trim()) }); generation++ }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message }
                            finally { posting = false }
                        }
                    }) { Text(copy("创建圈子", "Create circle")) }
                }
                else -> {
                    Text(copy("选择接收圈子", "Choose a circle"), style = MaterialTheme.typography.titleSmall, color = XpColors.Ink)
                    circles.forEach { circle ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected == circle.id, onClick = { selected = circle.id }, enabled = !posting)
                            TextButton(onClick = { selected = circle.id }, enabled = !posting) { Text(circle.name, color = XpColors.Ink) }
                        }
                    }
                    OutlinedTextField(caption, { caption = it.take(500) }, label = { Text(copy("说点什么（可选）", "Add a caption (optional)")) }, modifier = Modifier.fillMaxWidth(), enabled = !posting)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(copy("附上分类合计", "Include category totals"), Modifier.weight(1f), color = XpColors.Ink)
                        Switch(includeCategories, { includeCategories = it }, enabled = !posting)
                    }
                    Button(enabled = selected != null && !posting, onClick = {
                        val circle = circles.firstOrNull { it.id == selected } ?: return@Button
                        posting = true; error = null
                        scope.launch {
                            try {
                                client.request("/v1/circles/${circle.id}/posts", ReceiptCirclePayload.build(receipt, caption, includeCategories))
                                onPosted(circle.name)
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = copy("发布未确认，请先查看圈子再重试，避免重复发送。", "Publishing could not be confirmed. Check the circle before retrying to avoid duplicates.") }
                            finally { posting = false }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (posting) copy("正在发布…", "Publishing…") else copy("确认发布", "Confirm post"))
                    }
                }
            }
            error?.let { Text(it, color = XpColors.Negative, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onDismiss, enabled = !posting) { Text(copy("返回小票预览", "Back to receipt preview")) }
        }
    }
    if (accountOpen) AccountSheet(client) { accountOpen = false }
}
