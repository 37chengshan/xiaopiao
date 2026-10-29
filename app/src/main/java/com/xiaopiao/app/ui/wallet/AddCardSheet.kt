package com.xiaopiao.app.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.XpSwitch
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpFonts
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.wallet.BankCard
import com.xiaopiao.app.wallet.BankCardType
import com.xiaopiao.app.wallet.CardBin
import com.xiaopiao.app.wallet.CardFace
import com.xiaopiao.app.wallet.MemberCard
import com.xiaopiao.app.wallet.MemberCodeFormat

enum class AddCardKind { Bank, Member }

/**
 * 添加卡（docs/APP-DESIGN.md §5.5）：拍照识别 / 手动输入卡号 / 6 款卡面预设。
 * 卡号只在这里短暂存在，保存时立即交给 CardStore 加密，UI 不保留明文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardSheet(
    initialKind: AddCardKind,
    onDismiss: () -> Unit,
    onSaveBank: (BankCard, String) -> Unit,
    onSaveMember: (MemberCard) -> Unit,
) {
    var kind by remember { mutableStateOf(initialKind) }
    var scanOpen by remember { mutableStateOf(false) }
    var scanMode by remember { mutableStateOf(ScanMode.CardNumber) }
    var pan by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(BankCardType.Debit) }
    var face by remember { mutableStateOf(CardFace.Ink) }
    var faceTouched by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var showBank by remember { mutableStateOf(true) }
    var statementDay by remember { mutableStateOf(0) }
    var dueDay by remember { mutableStateOf(0) }

    var storeName by remember { mutableStateOf("") }
    var memberNo by remember { mutableStateOf("") }
    var memberFace by remember { mutableStateOf(CardFace.Mint) }
    var codeFormat by remember { mutableStateOf(MemberCodeFormat.Qr) }
    var points by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var expiryMonths by remember { mutableStateOf(0) }

    // 卡号一够长就用本机 BIN 表带出银行 / 卡种 / 默认卡面；用户自己挑过卡面就不再覆盖。
    LaunchedEffect(pan.length) {
        if (pan.length >= 6) {
            val info = CardBin.identify(pan)
            if (info.known) {
                bankName = info.bankName
                type = info.type
                if (!faceTouched) face = info.face
            }
        }
    }

    XpSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            Text("添加卡片", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, modifier = Modifier.padding(top = XpSpace.s))
            Segmented(listOf("银行卡", "会员卡"), if (kind == AddCardKind.Bank) 0 else 1, onSelect = { kind = if (it == 0) AddCardKind.Bank else AddCardKind.Member })

            if (kind == AddCardKind.Bank) {
                Field(
                    value = if (pan.isEmpty()) "" else CardBin.groups(pan),
                    onValueChange = { pan = CardBin.digitsOnly(it).take(19) },
                    placeholder = "卡号（15–19 位）",
                    keyboard = KeyboardType.Number,
                    mono = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    XpButton("拍照识别", { scanMode = ScanMode.CardNumber; scanOpen = true }, style = XpButtonStyle.Secondary, size = XpButtonSize.Small)
                    if (pan.isNotEmpty() && !CardBin.looksValid(pan)) {
                        Text("卡号看起来不完整", style = MaterialTheme.typography.bodySmall, color = XpColors.Warning, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Field(value = bankName, onValueChange = { bankName = it }, placeholder = "银行（BIN 自动识别）")
                Label("卡种")
                Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    Chip("储蓄卡", type == BankCardType.Debit) { type = BankCardType.Debit }
                    Chip("信用卡", type == BankCardType.Credit) { type = BankCardType.Credit }
                }
                if (type == BankCardType.Credit) {
                    Label("账单日")
                    DayChips(statementDay) { statementDay = it }
                    Label("还款日")
                    DayChips(dueDay) { dueDay = it }
                }
                Field(value = nickname, onValueChange = { nickname = it }, placeholder = "卡片名称（可选）")
                Label("卡面")
                FaceRow(face) { face = it; faceTouched = true }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("显示银行名", style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, modifier = Modifier.weight(1f))
                    XpSwitch(showBank, onCheckedChange = { showBank = it })
                }
                XpButton(
                    text = "保存银行卡",
                    onClick = {
                        onSaveBank(
                            BankCard(
                                id = "",
                                nickname = nickname,
                                bankName = bankName.ifBlank { "银行卡" },
                                brand = CardBin.identify(pan).brand,
                                type = type,
                                face = face,
                                showBankName = showBank,
                                statementDay = if (type == BankCardType.Credit) statementDay else 0,
                                dueDay = if (type == BankCardType.Credit) dueDay else 0,
                            ),
                            pan,
                        )
                    },
                    enabled = pan.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = XpSpace.s),
                )
            } else {
                Field(value = storeName, onValueChange = { storeName = it }, placeholder = "店名")
                Field(
                    value = memberNo, onValueChange = { memberNo = it.filter { c -> c.isLetterOrDigit() }.take(32) },
                    placeholder = "会员号 / 条形码内容", mono = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    XpButton("扫码录入", { scanMode = ScanMode.MemberCode; scanOpen = true }, style = XpButtonStyle.Secondary, size = XpButtonSize.Small)
                }
                Label("展示方式")
                Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    Chip("二维码", codeFormat == MemberCodeFormat.Qr) { codeFormat = MemberCodeFormat.Qr }
                    Chip("条形码", codeFormat == MemberCodeFormat.Code128) { codeFormat = MemberCodeFormat.Code128 }
                }
                Field(value = points, onValueChange = { points = it.filter(Char::isDigit).take(9) }, placeholder = "积分（可选）", keyboard = KeyboardType.Number)
                Field(value = balance, onValueChange = { balance = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, placeholder = "余额（可选）", keyboard = KeyboardType.Decimal)
                Label("到期提醒")
                Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    listOf(0 to "不提醒", 1 to "1 个月", 3 to "3 个月", 6 to "6 个月", 12 to "1 年").forEach { (m, label) ->
                        Chip(label, expiryMonths == m) { expiryMonths = m }
                    }
                }
                Label("卡面")
                FaceRow(memberFace) { memberFace = it }
                XpButton(
                    text = "保存会员卡",
                    onClick = {
                        val expiry = if (expiryMonths == 0) 0L else {
                            java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, expiryMonths) }.timeInMillis
                        }
                        onSaveMember(
                            MemberCard(
                                id = "",
                                storeName = storeName.ifBlank { "会员卡" },
                                memberNo = memberNo,
                                face = memberFace,
                                codeFormat = codeFormat,
                                points = points.toLongOrNull() ?: 0L,
                                balanceCents = balance.toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L,
                                expiryAt = expiry,
                            ),
                        )
                    },
                    enabled = memberNo.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = XpSpace.s),
                )
            }
            Spacer(Modifier.height(XpSpace.s))
        }
    }

    if (scanOpen) {
        CardScannerSheet(
            mode = scanMode,
            onDismiss = { scanOpen = false },
            onScanned = { digits ->
                if (kind == AddCardKind.Bank) pan = digits.take(19) else memberNo = digits.take(32)
                scanOpen = false
            },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, modifier = Modifier.padding(top = XpSpace.xs))
}

private val FieldShape = RoundedCornerShape(XpRadius.pill)

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(FieldShape).background(XpColors.Fill).padding(horizontal = XpSpace.l),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = XpFonts.sans, fontSize = 15.sp,
                letterSpacing = if (mono) 1.2.sp else 0.sp,
                fontFeatureSettings = if (mono) "tnum" else "",
                color = XpColors.Ink,
            ),
            cursorBrush = SolidColor(XpColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkTertiary)
                }
                inner()
            },
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(FieldShape)
            .background(if (selected) XpColors.AccentSoft else XpColors.Fill)
            .clickable(onClick = onClick)
            .padding(horizontal = XpSpace.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) XpColors.Accent else XpColors.Ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun DayChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0, 5, 8, 10, 15, 20, 25, 28).forEach { day ->
            Chip(if (day == 0) "未设置" else "$day 日", selected == day) { onSelect(day) }
        }
    }
}

/** 6 款卡面预设：色块 + 名称，选中的加一圈 ink 描边（只有这里允许描边，因为它是色卡选择器）。 */
@Composable
private fun FaceRow(selected: CardFace, onSelect: (CardFace) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        CardFace.entries.forEach { face ->
            val color = Color(face.argb.toInt())
            val ring = if (face == selected) XpColors.Ink else Color.Transparent
            Box(
                Modifier.size(width = 44.dp, height = 30.dp).clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .border(2.dp, ring, RoundedCornerShape(8.dp))
                    .clickable { onSelect(face) },
            )
        }
    }
}
