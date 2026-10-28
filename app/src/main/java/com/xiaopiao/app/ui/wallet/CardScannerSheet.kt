package com.xiaopiao.app.ui.wallet

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.concurrent.Executors

/** 两种取词方式：银行卡读卡面数字（OCR），会员卡读条码/二维码本身（扫码）。 */
enum class ScanMode(val title: String, val hint: String) {
    CardNumber("拍照识别卡号", "把卡号放进取景框"),
    MemberCode("扫码录入会员号", "对准条形码或二维码"),
}

/**
 * 本机识别（docs/APP-DESIGN.md §5.5）：CameraX 取帧 → ML Kit（文字识别 / 条码识别），图像不出本机。
 * 识别到就回传并立刻关闭相机；没有相机权限时只显示手动输入这条路。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CardScannerSheet(
    mode: ScanMode,
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val textClient = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val barcodeClient: BarcodeScanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build())
    }
    var hint by remember { mutableStateOf(mode.hint) }

    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    DisposableEffect(Unit) {
        onDispose {
            textClient.close()
            barcodeClient.close()
            executor.shutdown()
        }
    }

    XpSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            Text(mode.title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, modifier = Modifier.padding(top = XpSpace.s))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            if (granted) {
                Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(XpRadius.l)).background(XpColors.Ink)) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build()
                                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                                    val media = proxy.image
                                    if (media == null) {
                                        proxy.close()
                                        return@setAnalyzer
                                    }
                                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                    if (mode == ScanMode.CardNumber) {
                                        textClient.process(image)
                                            .addOnSuccessListener { result ->
                                                longestDigits(result.text)?.let(onScanned)
                                                    ?: run { hint = "没看清，靠近一点再试" }
                                            }
                                            .addOnCompleteListener { proxy.close() }
                                    } else {
                                        barcodeClient.process(image)
                                            .addOnSuccessListener { codes ->
                                                val raw = codes.firstOrNull()?.rawValue?.trim()
                                                if (raw.isNullOrEmpty()) {
                                                    hint = "没找到条码，把会员码对准取景框"
                                                } else {
                                                    onScanned(raw.filter { it.isLetterOrDigit() }.take(32))
                                                }
                                            }
                                            .addOnCompleteListener { proxy.close() }
                                    }
                                }
                                runCatching {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                                }.onFailure { hint = "相机不可用，请手动输入" }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        onRelease = {
                            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
                        },
                    )
                }
            } else {
                Text(
                    "需要相机权限才能识别；也可以手动输入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XpColors.InkSecondary,
                )
                XpButton("授予相机权限", { permission.launch(Manifest.permission.CAMERA) }, style = XpButtonStyle.Secondary)
            }
            XpButton("手动输入", onDismiss, modifier = Modifier.fillMaxWidth(), style = XpButtonStyle.Secondary)
        }
    }
}

/**
 * 卡面上的卡号是 15–19 位；OCR 会按四位插空格，还可能把旁边的数字连成一串。
 * 做法：按空白切成数字组，从每个起点贪心拼到 ≤19 位，长度落在 15–19 就算候选；
 * 最后优先要 Luhn 校验通过的那个（能挡住把日期/金额拼进来的误读），否则取最长。
 */
internal fun longestDigits(text: String): String? {
    val candidates = mutableListOf<String>()
    Regex("""\d[\d ]*\d""").findAll(text).forEach { match ->
        val groups = match.value.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        for (start in groups.indices) {
            var acc = ""
            for (i in start until groups.size) {
                acc += groups[i]
                if (acc.length > 19) break
                if (acc.length >= 15) {
                    if (acc.length <= 19) candidates += acc
                    break
                }
            }
        }
    }
    if (candidates.isEmpty()) return null
    return candidates.firstOrNull { com.xiaopiao.app.wallet.CardBin.looksValid(it) }
        ?: candidates.maxByOrNull { it.length }
}
