package com.xiaopiao.notifake

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.app.Activity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Posts fake payment notifications under a spoofed package name so the Xiaopiao capture pipeline
 * can be tested on an emulator. Test-only; never installed on a real phone.
 *
 * Adb usage (automation):
 *   adb shell am start -n com.tencent.mm/com.xiaopiao.notifake.PostActivity \
 *       --es title "微信支付" --es text "已支付¥15.00 · X" --ei count 1 --el delay 0
 */
class PostActivity : Activity() {
    private val manager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val title = intent.getStringExtra("title")
        val text = intent.getStringExtra("text")
        if (text != null) {
            val count = intent.getIntExtra("count", 1).coerceIn(1, 20)
            val delay = intent.getLongExtra("delay", 0L)
            repeat(count) { index ->
                post(title ?: "Payment", if (count > 1) "$text #${index + 1}" else text)
                if (delay > 0) SystemClock.sleep(delay)
            }
            finish()
            return
        }
        setContentView(buildUi())
    }

    private fun buildUi(): ScrollView {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        column.addView(TextView(this).apply {
            text = "${getString(R.string.fake_app_title)} · $packageName\n${getString(R.string.fake_app_hint)}"
            textSize = 15f
        })
        samples.forEach { (label, sample) ->
            column.addView(Button(this).apply {
                text = "$label\n${sample.first} / ${sample.second}"
                isAllCaps = false
                setOnClickListener { post(sample.first, sample.second) }
            })
        }
        return ScrollView(this).apply { addView(column) }
    }

    private fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "支付", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    @Suppress("DEPRECATION")
    private fun post(title: String, text: String) {
        val notification = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(SystemClock.elapsedRealtime().toInt(), notification)
    }

    private companion object {
        const val CHANNEL = "fake_pay"

        /** Mirrors the corpus in PaymentNotificationParserTest, so what the tests claim is what shows up. */
        val samples = listOf(
            "支付宝 · 支出" to ("支付宝通知" to "你有一笔 15.00 元的支出，点击查看详情"),
            "支付宝 · 收入" to ("支付宝通知" to "你有一笔200.50元的收入"),
            "支付宝 · 付款成功" to ("付款成功" to "付款成功 ¥15.00 山野咖啡"),
            "支付宝 · 向商户付款" to ("支付宝" to "你已向蜜雪冰城付款￥9.90"),
            "微信 · 已支付" to ("微信支付" to "已支付¥32.00 · 老乡鸡"),
            "微信 · 凭证" to ("微信支付凭证" to "支付金额 ¥18.50 商户：美宜佳"),
            "微信 · 收款" to ("微信收款助手" to "收款到账通知 收款金额¥6.00"),
            "微信 · 先享后付" to ("微信支付" to "先享后付订单扣款成功 ¥12.00"),
            "微信 · 退款" to ("微信支付" to "退款通知 退款¥25.00 已原路退回"),
            "营销 · 应忽略" to ("支付宝" to "限时领取5元红包，满20可用，快来领取！"),
            "陌生文案 · 应没认出" to ("支付宝" to "交易提醒：你有一笔支付，点击查看>"),
        )
    }
}
