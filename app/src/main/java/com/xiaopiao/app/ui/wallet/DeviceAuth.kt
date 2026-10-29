package com.xiaopiao.app.ui.wallet

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 设备验证（docs/APP-DESIGN.md §5.5）：API 28+ 走系统 [BiometricPrompt]（允许设备凭据），
 * API 26–27 退回锁屏密码确认。不引第三方库，验证与业务分离。
 */
class DeviceAuthGuard(
    private val activity: Activity?,
    private val launchCredential: (Intent, (Boolean) -> Unit) -> Unit,
) {
    fun request(title: String, subtitle: String, onResult: (Boolean) -> Unit) {
        val host = activity
        if (host == null) {
            onResult(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(code: Int, msg: CharSequence?) = onResult(false)
            }
            val prompt = BiometricPrompt.Builder(host)
                .setTitle(title)
                .setSubtitle(subtitle)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setAllowedAuthenticators(
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setDeviceCredentialAllowed(true)
                    }
                }
                .build()
            runCatching { prompt.authenticate(CancellationSignal(), host.mainExecutor, callback) }
                .onFailure { onResult(false) }
            return
        }
        val keyguard = host.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val intent = keyguard?.createConfirmDeviceCredentialIntent(title, subtitle)
        if (intent == null) onResult(false) else launchCredential(intent, onResult)
    }
}

@Composable
fun rememberDeviceAuthGuard(): DeviceAuthGuard {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending?.invoke(result.resultCode == Activity.RESULT_OK)
        pending = null
    }
    return remember(activity) {
        DeviceAuthGuard(activity) { intent, onResult ->
            pending = onResult
            launcher.launch(intent)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
