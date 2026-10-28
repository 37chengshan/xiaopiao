package com.xiaopiao.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xiaopiao.app.ui.AppShell
import com.xiaopiao.app.ui.theme.XiaopiaoTheme
import com.xiaopiao.app.work.ReminderIntents

/** A deep-link request from a reminder notification; identity-compared so repeated taps re-trigger. */
class OpenRequest(val target: String)

class MainActivity : ComponentActivity() {
    private var openRequest by mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as XiaopiaoApp).container
        if (savedInstanceState == null) openRequest = intent.toOpenRequest()
        setContent {
            XiaopiaoTheme {
                AppShell(container, openRequest, onOpenConsumed = { openRequest = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toOpenRequest()?.let { openRequest = it }
    }

    private fun Intent.toOpenRequest() = getStringExtra(ReminderIntents.EXTRA_OPEN)?.let(::OpenRequest)
}
