package com.dalakoti.apps.speechtotext.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dalakoti.apps.speechtotext.core.data.ClipboardWriter
import com.dalakoti.apps.speechtotext.core.model.EngineState

@Composable
fun DictationScreen(
    vm: DictationViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engine by vm.engineState.collectAsState()
    val phase by vm.phase.collectAsState()
    val elapsed by vm.elapsedMs.collectAsState()
    val level by vm.level.collectAsState()
    val result by vm.result.collectAsState()
    val history by vm.history.collectAsState()

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { micGranted = it }

    val recording = phase == DictationPhase.Recording
    val ready = engine is EngineState.Ready

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        EngineBanner(engine = engine, onOpenSettings = onOpenSettings)

        Spacer(Modifier.height(28.dp))

        LevelWaveform(
            level = level,
            active = recording,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = when {
                recording -> formatElapsed(elapsed)
                phase == DictationPhase.Transcribing -> "Transcribing…"
                else -> " "
            },
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            color = if (recording) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        MicButton(
            enabled = ready && micGranted && phase != DictationPhase.Transcribing,
            recording = recording,
            level = level,
            onStart = {
                if (!micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                else vm.startRecording()
            },
            onStop = { vm.stopRecording() },
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = when {
                !micGranted -> "Tap to allow microphone access"
                !ready -> "Waiting for the model"
                recording -> "Release to transcribe — or tap again to stop"
                phase == DictationPhase.Transcribing -> "Working…"
                else -> "Hold to dictate, or tap to start and tap again to stop"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        (phase as? DictationPhase.Error)?.let { err ->
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(err.message, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = vm::clearError) { Text("Dismiss") }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        val shown = result ?: history.firstOrNull()?.text
        AnimatedVisibility(visible = shown != null) {
            TranscriptCard(
                text = shown.orEmpty(),
                isLatest = result != null,
                onCopy = { ClipboardWriter.copy(context, shown.orEmpty()) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EngineBanner(engine: EngineState, onOpenSettings: () -> Unit) {
    when (engine) {
        is EngineState.Ready -> StatusPill(
            "Model ready · ${engine.coldLoadMs} ms load",
            MaterialTheme.colorScheme.primary,
        )

        is EngineState.Loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Loading model…", style = MaterialTheme.typography.bodyMedium)
        }

        is EngineState.Failed -> Surface(
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.error,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Model failed to load", style = MaterialTheme.typography.titleSmall)
                Text(engine.message, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onOpenSettings) { Text("Open settings") }
            }
        }

        EngineState.Unloaded -> TextButton(onClick = onOpenSettings) {
            Text("No model selected — choose one")
        }
    }
}

/** A quick tap is a real gesture, not a mistake — so it toggles instead of doing nothing. */
private const val HOLD_THRESHOLD_MS = 400L

@Composable
private fun MicButton(
    enabled: Boolean,
    recording: Boolean,
    level: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val pulse by animateFloatAsState(
        targetValue = if (recording) 1f + (level * 0.18f) else 1f,
        label = "pulse",
    )
    // Read through a snapshot, never as a pointerInput key: re-keying on `recording`
    // restarts the gesture detector the instant recording begins, which cancels the
    // in-flight tryAwaitRelease() and loses the finger-lift entirely.
    val currentRecording by rememberUpdatedState(recording)

    val container = when {
        recording -> MaterialTheme.colorScheme.secondary
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Box(contentAlignment = Alignment.Center) {
        if (recording) {
            Box(
                Modifier
                    .size(160.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(container.copy(alpha = 0.16f))
            )
        }
        Surface(
            color = container,
            contentColor = if (recording) MaterialTheme.colorScheme.onSecondary
            else MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            shadowElevation = if (recording) 0.dp else 6.dp,
            modifier = Modifier
                .size(120.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            // Two gestures, one button:
                            //   hold  → record while held, stop on release
                            //   tap   → start, and the next tap stops
                            val wasRecording = currentRecording
                            if (wasRecording) onStop() else onStart()

                            val pressedAt = System.currentTimeMillis()
                            tryAwaitRelease()
                            val heldMs = System.currentTimeMillis() - pressedAt

                            if (!wasRecording && heldMs >= HOLD_THRESHOLD_MS) onStop()
                        }
                    )
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (enabled) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (recording) "Recording" else "Press and hold to dictate",
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}

@Composable
fun TranscriptCard(text: String, isLatest: Boolean, onCopy: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(if (isLatest) "Latest transcript" else "Most recent")
            // One Text. The moment this becomes a list of segments, the bullets are back.
            Text(text, style = MaterialTheme.typography.bodyLarge)
            FilledTonalButton(onClick = onCopy, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy to clipboard")
            }
        }
    }
}
