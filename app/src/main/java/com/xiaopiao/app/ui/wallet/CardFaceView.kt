package com.xiaopiao.app.ui.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpFonts
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.wallet.BankCard
import com.xiaopiao.app.wallet.CardBin
import com.xiaopiao.app.wallet.CardFace
import com.xiaopiao.app.wallet.CardSpend
import com.xiaopiao.app.wallet.MemberCard
import com.xiaopiao.app.wallet.MemberCodeFormat
import java.util.Calendar

/** 卡面底色与前景色：卡面是唯一允许大面积色块的地方（§5.5），所以不经过 XpColors。 */
internal val CardFace.bg: Color get() = Color(argb.toInt())
internal val CardFace.onBg: Color get() = Color(onArgb.toInt())

private val PanStyle = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 17.sp, lineHeight = 22.sp,
    fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, fontFeatureSettings = "tnum",
)

/**
 * 一张卡的底：16dp 圆角、预设底色 + 极淡斜纹（暗纹），内容由 [content] 画。
 * 叠放时后一张卡只露顶部标题条，所以标题条必须贴着上沿排版。
 */
@Composable
fun CardCanvas(
    face: CardFace,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val ink = face.onBg
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(face.bg),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            rotate(18f) {
                val step = 26.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(ink.copy(alpha = 0.05f), Offset(x, -size.height), Offset(x, size.height * 2), 1.5.dp.toPx())
                    x += step
                }
            }
        }
        content()
    }
}

/** 标题条：左卡名 20sp/600，右本月支出 + 一行小字（尾号 / 卡种）。 */
@Composable
fun CardStrip(
    title: String,
    amountText: String,
    caption: String,
    face: CardFace,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(71.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = face.onBg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                amountText,
                style = TextStyle(fontFamily = XpFonts.sans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = face.onBg,
                maxLines = 1,
            )
            Text(caption, style = MaterialTheme.typography.bodySmall, color = face.onBg.copy(alpha = 0.72f), maxLines = 1)
        }
    }
}

/** 别的卡被选中时，其余卡收拢成 40dp 的一条：只留卡名与金额（§5.5「其余下沉收拢」）。 */
@Composable
fun CardStripCompact(title: String, amountText: String, face: CardFace, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(40.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = face.onBg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            amountText,
            style = TextStyle(fontFamily = XpFonts.sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = face.onBg,
            maxLines = 1,
        )
    }
}

/** 完整银行卡面：银行名 / 卡号（可揭示、可复制）/ 还款提醒 / 卡组织。 */
@Composable
fun BankCardBody(
    card: BankCard,
    revealedPan: String?,
    spend: CardSpend,
    hidden: Boolean,
    onReveal: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val face = card.face
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (card.showBankName) {
                Text(
                    card.bankName,
                    style = MaterialTheme.typography.bodySmall,
                    color = face.onBg.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                if (hidden) "••••" else "本月 ${spend.cents.yuan()}",
                style = MaterialTheme.typography.bodySmall,
                color = face.onBg.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            revealedPan?.let { CardBin.groups(it) } ?: CardBin.mask(card.last4),
            style = PanStyle,
            color = face.onBg,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            FaceIcon(if (revealedPan != null) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (revealedPan != null) "遮住卡号" else "显示完整卡号", face, onReveal)
            FaceIcon(Icons.Outlined.ContentCopy, "复制卡号", face, onCopy)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                if (card.dueDay > 0) {
                    Text(
                        dueLine(card.dueDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = face.onBg.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(card.type.label, style = MaterialTheme.typography.bodySmall, color = face.onBg.copy(alpha = 0.7f))
            }
            Text(
                brandOf(card),
                style = TextStyle(
                    fontFamily = XpFonts.sans, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                ),
                color = face.onBg.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun FaceIcon(icon: ImageVector, description: String, face: CardFace, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).pressableClick(haptic = false, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = face.onBg.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
    }
}

/** 完整会员卡面：店名、条码/二维码（白底面板）、会员号。 */
@Composable
fun MemberCardBody(
    card: MemberCard,
    modifier: Modifier = Modifier,
    brighten: Boolean = false,
) {
    val face = card.face
    val bitmap = remember(card.memberNo, card.codeFormat, brighten) {
        MemberCode.bitmap(card.memberNo, card.codeFormat, if (card.codeFormat == MemberCodeFormat.Qr) 420 else 620)
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                card.storeName,
                style = MaterialTheme.typography.titleMedium,
                color = face.onBg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (card.points > 0) "${card.points} 分" else if (card.balanceCents > 0) "¥ ${card.balanceCents.yuan()}" else "",
                style = TextStyle(fontFamily = XpFonts.sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = face.onBg,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(XpRadius.s)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "会员码",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(if (card.codeFormat == MemberCodeFormat.Qr) 112.dp else 56.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else {
                Text("会员号为空", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8E8E93))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            card.memberNo,
            style = TextStyle(fontFamily = XpFonts.sans, fontSize = 13.sp, letterSpacing = 1.5.sp, fontFeatureSettings = "tnum"),
            color = face.onBg.copy(alpha = 0.9f),
            maxLines = 1,
        )
    }
}

/** 卡组织字样：录入时由 BIN 写进卡片，旧数据没有就退回银联。 */
internal fun brandOf(card: BankCard): String = card.brand.ifBlank { "银联" }

/** 「还款日 8 日 · 3 天后」——只陈述日期，不做还款建议。 */
internal fun dueLine(dueDay: Int, now: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val days = if (dueDay >= today) dueDay - today else dueDay + cal.getActualMaximum(Calendar.DAY_OF_MONTH) - today
    return when (days) {
        0 -> "今天还款日"
        1 -> "明天还款日"
        else -> "还款日 $dueDay 日 · $days 天后"
    }
}
