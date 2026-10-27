package com.xiaopiao.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xiaopiao.app.ui.AppShell
import com.xiaopiao.app.ui.theme.XiaopiaoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as XiaopiaoApp).container
        setContent {
            XiaopiaoTheme { AppShell(container) }
        }
    }
}
