package com.dalakoti.apps.speechtotext.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dalakoti.apps.speechtotext.core.data.ClipboardWriter
import com.dalakoti.apps.speechtotext.core.data.HistoryStore
import com.dalakoti.apps.speechtotext.core.data.TextSharer
import com.dalakoti.apps.speechtotext.core.model.Transcript
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: DictationViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val history by vm.history.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Last ${HistoryStore.MAX_ITEMS} transcripts")
                if (history.isNotEmpty()) {
                    TextButton(onClick = vm::clearHistory) { Text("Clear") }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing dictated yet",
                    body = "Transcripts are kept here, in the app. " +
                        "The last ${HistoryStore.MAX_ITEMS} stay available after a restart.",
                )
            }
        }

        // A list of transcripts, not a list of segments — this LazyColumn is the correct
        // kind. The one that produced bullets rendered per-VAD-segment results.
        items(history, key = { it.createdAt }) { transcript ->
            HistoryRow(
                transcript = transcript,
                onCopy = { ClipboardWriter.copy(context, transcript.text) },
                onShare = { TextSharer.share(context, transcript.text) },
            )
        }
    }
}

@Composable
private fun HistoryRow(
    transcript: Transcript,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timestamp(transcript.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${transcript.durationMs / 1000}s",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(transcript.text, style = MaterialTheme.typography.bodyLarge)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onShare) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                TextButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy to clipboard")
                }
            }
        }
    }
}

private fun timestamp(millis: Long): String =
    SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(millis))
