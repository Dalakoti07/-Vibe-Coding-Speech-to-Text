package com.dalakoti.apps.speechtotext.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dalakoti.apps.speechtotext.core.asr.ModelLocator
import com.dalakoti.apps.speechtotext.core.audio.CaptureSource
import com.dalakoti.apps.speechtotext.core.model.EngineState
import com.dalakoti.apps.speechtotext.core.model.ScanResult
import com.dalakoti.apps.speechtotext.core.model.StorageAccess

@Composable
fun SettingsScreen(vm: DictationViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val storage by vm.storage.collectAsState()
    val scan by vm.scan.collectAsState()
    val root by vm.modelRoot.collectAsState()
    val engine by vm.engineState.collectAsState()
    val diag by vm.diagnostics.collectAsState()
    val source by vm.captureSource.collectAsState()

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = ModelLocator.treeUriToPath(uri)
        if (path != null) vm.setModelRoot(path.absolutePath)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── storage access ────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Model storage")
            if (storage == StorageAccess.Granted) {
                StatusPill("All files access granted", MaterialTheme.colorScheme.primary,
                    Icons.Default.CheckCircle)
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, Modifier.size(18.dp))
                            Text("All files access needed", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            "Models live in shared storage so they survive reinstalls. " +
                                "Android only grants this from its settings page.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = {
                            ModelLocator.grantIntent(context)?.let { intent ->
                                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Text("Grant access") }
                    }
                }
            }

            KeyValueRow("Model folder", root)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { treePicker.launch(null) }) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Change folder")
                }
                OutlinedButton(onClick = vm::refresh) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rescan")
                }
            }
        }

        HorizontalDivider()

        // ── models ────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Models found")

            if (scan.isEmpty()) {
                EmptyState(
                    title = "No model folders in $root",
                    body = "Copy a sherpa-onnx model directory there, then press Rescan.",
                )
                CodeBlock(
                    "adb shell mkdir -p $root\n" +
                        "adb push sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming $root/"
                )
            }

            val activeId = (engine as? EngineState.Ready)?.modelId
            scan.forEach { entry ->
                when (entry) {
                    is ScanResult.Recognised -> {
                        val d = entry.descriptor
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = if (d.id == activeId) 3.dp else 1.dp,
                            color = if (d.id == activeId)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = d.id == activeId,
                                    onClick = { vm.selectModel(d) },
                                ),
                        ) {
                            Column(Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(d.displayName, style = MaterialTheme.typography.titleSmall)
                                    if (d.id == activeId) {
                                        Icon(Icons.Default.CheckCircle, "Active",
                                            Modifier.size(18.dp))
                                    }
                                }
                                Text(
                                    "${d.sizeBytes / 1024 / 1024} MB · ${d.recipeId} · ${d.language}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Never drop a folder silently — say what is wrong with it.
                    is ScanResult.Unrecognised -> Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.dirName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                entry.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // ── audio ─────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("Audio capture")
            Text(
                "16 kHz mono is fixed by the model — any other rate quietly degrades " +
                    "accuracy rather than failing. What you can change is the source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            CaptureSource.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option == source,
                            onClick = { vm.setCaptureSource(option) },
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == source, onClick = null)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            option.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // ── diagnostics ───────────────────────────────────────────────────
        Column {
            SectionLabel("Diagnostics")
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Engine", when (val e = engine) {
                is EngineState.Ready -> "ready"
                is EngineState.Loading -> "loading"
                is EngineState.Failed -> "failed: ${e.message.take(40)}"
                EngineState.Unloaded -> "unloaded"
            })
            KeyValueRow("Cold load", diag.coldLoadMs?.let { "$it ms" } ?: "—")
            KeyValueRow("Memory after load", diag.pssAfterLoadKb?.let { "${it / 1024} MB" } ?: "—")
            KeyValueRow("Peak memory", diag.peakPssKb?.let { "${it / 1024} MB" } ?: "—")
            KeyValueRow("Last transcribe", diag.lastTranscribeMs?.let { "$it ms" } ?: "—")
            KeyValueRow("Last audio", diag.lastAudioSeconds?.let { "%.1f s".format(it) } ?: "—")
            Spacer(Modifier.height(10.dp))
            Bullet("No network permission — this app cannot reach the internet.")
            Bullet("Nothing is copied to the clipboard unless you press Copy.")
        }

        Spacer(Modifier.height(24.dp))
    }
}
