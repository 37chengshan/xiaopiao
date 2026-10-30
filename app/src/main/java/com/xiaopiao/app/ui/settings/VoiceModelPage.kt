package com.xiaopiao.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.asr.AsrCatalog
import com.xiaopiao.app.domain.asr.AsrInstallState
import com.xiaopiao.app.domain.asr.AsrLanguage
import com.xiaopiao.app.domain.asr.AsrModel
import com.xiaopiao.app.domain.asr.AsrPreference
import com.xiaopiao.app.domain.asr.AsrTier
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch

/**
 * 设置 › 语音模型（tt 2026-10-29）：小模型（跟随手机语言）、大模型、不下载；可取消下载、删除已下载。
 * Sizes come from the catalog's real byte counts, not from a guess.
 */
@Composable
fun VoiceModelNavSection(container: AppContainer, onOpen: () -> Unit) {
    val language = AsrLanguage.system()
    val copy = remember(language) { asrCopy(language) }
    val states by container.asrModels.states.collectAsState()
    val anyInstalled = container.asrModels.installedIds().isNotEmpty() || states.values.any { it is AsrInstallState.Installed }
    GroupedSection(header = copy.title) {
        item {
            GroupedRow(
                title = copy.title,
                subtitle = copy.lead,
                value = if (anyInstalled) copy.rowValueReady else copy.rowValueMissing,
                leading = { IconTile(Icons.Outlined.GraphicEq, tint = XpColors.Info, background = XpColors.InfoSoft) },
                chevron = true,
                onClick = onOpen,
            )
        }
    }
}

@Composable
fun VoicePage(container: AppContainer, onBack: () -> Unit) {
    val language = AsrLanguage.system()
    val copy = remember(language) { asrCopy(language) }
    val scope = rememberCoroutineScope()
    val models = container.asrModels
    val states by models.states.collectAsState()
    val preference by container.asrPrefs.observe().collectAsState(initial = AsrPreference.SMALL)
    var confirmDelete by remember { mutableStateOf<AsrModel?>(null) }
    val scroll = rememberScrollState()

    NavScaffold(copy.title, rememberNavScroll(scroll), onBack = onBack, glassGround = true) { top ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            Text(
                copy.lead,
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(horizontal = XpSpace.xl),
            )

            GroupedSection(header = copy.languageNote, footer = copy.privacy, dividerInset = 58.dp) {
                AsrCatalog.forLanguage(language).forEach { model ->
                    val state = states[model.id]
                        ?: if (models.modelStore.installed(model)) AsrInstallState.Installed else AsrInstallState.Missing
                    item(key = model.id) {
                        Column {
                            ModelRow(
                                model = model,
                                copy = copy,
                                state = state,
                                selected = (model.tier == AsrTier.SMALL && preference == AsrPreference.SMALL) ||
                                    (model.tier == AsrTier.LARGE && preference == AsrPreference.LARGE),
                                onSelect = {
                                    scope.launch {
                                        container.asrPrefs.set(
                                            if (model.tier == AsrTier.SMALL) AsrPreference.SMALL else AsrPreference.LARGE,
                                        )
                                    }
                                },
                                onDownload = { models.start(model) },
                                onCancel = { models.cancel(model) },
                                onDelete = { confirmDelete = model },
                            )
                            if (state is AsrInstallState.Downloading) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 58.dp, end = XpSpace.l, bottom = XpSpace.s),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
                                ) {
                                    LinearProgressIndicator(
                                        progress = { state.fraction },
                                        modifier = Modifier.weight(1f),
                                        color = XpColors.Primary,
                                        trackColor = XpColors.Fill,
                                    )
                                    Text(
                                        progressText(copy, state.fraction),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = XpColors.InkSecondary,
                                    )
                                }
                            }
                            if (state is AsrInstallState.Failed) {
                                Text(
                                    copy.failed,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = XpColors.Negative,
                                    modifier = Modifier.padding(start = 58.dp, end = XpSpace.l, bottom = XpSpace.s),
                                )
                            }
                        }
                    }
                }
                item(key = "off") {
                    OffRow(
                        copy = copy,
                        selected = preference == AsrPreference.OFF,
                        onClick = { scope.launch { container.asrPrefs.set(AsrPreference.OFF) } },
                    )
                }
            }

            if (com.xiaopiao.app.BuildConfig.DEBUG) {
                AsrProbeSection(container)
            }

            Text(
                copy.wifiNote,
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(horizontal = XpSpace.xl),
            )
        }
    }

    confirmDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(copy.delete) },
            text = { Text(modelLabel(model, copy)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { models.delete(model) }
                    confirmDelete = null
                }) { Text(copy.delete, color = XpColors.Negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(copy.cancel) } },
        )
    }
}

@Composable
private fun ModelRow(
    model: AsrModel,
    copy: AsrCopy,
    state: AsrInstallState,
    selected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val downloading = state is AsrInstallState.Downloading
    GroupedRow(
        title = modelLabel(model, copy),
        subtitle = if (model.tier == AsrTier.SMALL) {
            "${sizeText(model.totalBytes)} · ${copy.smallNote}"
        } else {
            "${sizeText(model.totalBytes)} · ${copy.largeNote}"
        },
        leading = {
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) XpColors.Primary else XpColors.InkTertiary,
                modifier = Modifier.size(22.dp),
            )
        },
        value = when (state) {
            AsrInstallState.Installed -> copy.installed
            is AsrInstallState.Failed -> copy.retry
            else -> null
        },
        trailing = {
            when {
                downloading -> TextButton(onClick = onCancel) { Text(copy.cancel, color = XpColors.InkSecondary) }
                state is AsrInstallState.Installed -> TextButton(
                    onClick = onDelete,
                    modifier = Modifier.semantics { contentDescription = "${copy.delete} ${modelLabel(model, copy)}" },
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = XpColors.InkTertiary, modifier = Modifier.size(18.dp))
                }
                else -> TextButton(
                    onClick = onDownload,
                    modifier = Modifier.semantics { contentDescription = "${copy.download} ${modelLabel(model, copy)}" },
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, tint = XpColors.Primary, modifier = Modifier.size(18.dp))
                }
            }
        },
        onClick = onSelect,
    )
}

@Composable
private fun OffRow(copy: AsrCopy, selected: Boolean, onClick: () -> Unit) {
    GroupedRow(
        title = copy.off,
        subtitle = copy.offNote,
        leading = {
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) XpColors.Primary else XpColors.InkTertiary,
                modifier = Modifier.size(22.dp),
            )
        },
        onClick = onClick,
    )
}
