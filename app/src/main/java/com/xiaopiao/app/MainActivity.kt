package com.xiaopiao.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.xiaopiao.app.ui.AppShell
import com.xiaopiao.app.ui.splash.SplashHost
import com.xiaopiao.app.ui.theme.XiaopiaoTheme
import com.xiaopiao.app.work.ReminderIntents

/** A deep-link request from a reminder notification; identity-compared so repeated taps re-trigger. */
class OpenRequest(val target: String)

class MainActivity : ComponentActivity() {
    private var openRequest by mutableStateOf<OpenRequest?>(null)

    /**
     * True only for a cold start (new task / process). A warm start keeps this activity alive, and a
     * configuration change recreates it with a saved state, so both skip the paper animation (§2.5).
     */
    private var playSplash by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the platform splash icon (AVD) is wired to this window.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as XiaopiaoApp).container
        if (savedInstanceState == null) openRequest = intent.toOpenRequest()
        playSplash = savedInstanceState == null
        setContent {
            XiaopiaoTheme {
                Box(Modifier.fillMaxSize()) {
                    AppShell(container, openRequest, onOpenConsumed = { openRequest = null })
                    if (playSplash) {
                        SplashHost(onFinished = { playSplash = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toOpenRequest()?.let { openRequest = it }
    }

    private fun Intent.toOpenRequest() = getStringExtra(ReminderIntents.EXTRA_OPEN)?.let(::OpenRequest)
}
