package com.xiaopiao.app.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.asr.AsrInstallState
import com.xiaopiao.app.domain.asr.AsrLanguage
import com.xiaopiao.app.domain.asr.AsrModel
import com.xiaopiao.app.ui.settings.asrCopy
import com.xiaopiao.app.ui.settings.modelLabel
import com.xiaopiao.app.ui.settings.progressText
import com.xiaopiao.app.ui.theme.XpColors

/**
 * The first tap on the mic when no model exists yet: one tap downloads the small model for this language
 * (with its real size and live progress) and starts listening the moment it is verified. Nothing is
 * downloaded silently.
 */
@Composable
fun VoiceModelPrompt(model: AsrModel, host: VoiceHostState) {
    val copy = asrCopy(AsrLanguage.system())
    val states by host.models.states.collectAsState()
    val state = states[model.id]
    val progress = (state as? AsrInstallState.Downloading)?.fraction

    AlertDialog(
        onDismissRequest = { if (!host.downloading) host.dismissPrompt() },
        title = { Text(copy.promptTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(copy.promptBody(model), style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
                if (model.tier == com.xiaopiao.app.domain.asr.AsrTier.LARGE) {
                    Text(copy.wifiNote, style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary)
                }
                if (progress != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f),
                            color = XpColors.Primary,
                            trackColor = XpColors.Fill,
                        )
                        Text(
                            progressText(copy, progress),
                            style = MaterialTheme.typography.labelSmall,
                            color = XpColors.InkSecondary,
                        )
                    }
                }
                host.downloadError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = XpColors.Negative)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { host.downloadAndListen() }, enabled = !host.downloading) {
                Text(if (host.downloading) copy.downloading.replace("%d%%", "") else copy.promptDownload, color = XpColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { host.dismissPrompt() }, enabled = !host.downloading) {
                Text(copy.promptLater, color = XpColors.InkSecondary)
            }
        },
    )
}
