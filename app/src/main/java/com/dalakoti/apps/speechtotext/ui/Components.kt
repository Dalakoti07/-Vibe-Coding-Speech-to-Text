package com.dalakoti.apps.speechtotext.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/** Small uppercase label used above sections. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** State readable at a glance, not only by reading the number next to it. */
@Composable
fun StatusPill(
    text: String,
    tone: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
) {
    Surface(
        color = tone.copy(alpha = 0.12f),
        contentColor = tone,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Live input meter. Drawn rather than animated with a spinner because what the user needs
 * to know is "is it hearing me", and only the signal itself answers that.
 */
@Composable
fun LevelWaveform(
    level: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 36,
) {
    val amplitude by animateFloatAsState(
        targetValue = if (active) level.coerceIn(0.02f, 1f) else 0.02f,
        label = "amplitude",
    )
    val color = if (active) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier) {
        val barWidth = size.width / (barCount * 2f)
        val mid = size.height / 2f
        for (i in 0 until barCount) {
            // A fixed envelope shaped by the live amplitude: reads as a voice, not a
            // random jitter, and stays honest about silence.
            val envelope = sin(Math.PI * i / (barCount - 1)).toFloat()
            val h = (mid * amplitude * envelope).coerceAtLeast(1.5f)
            val x = barWidth + i * barWidth * 2
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, mid - h),
                end = androidx.compose.ui.geometry.Offset(x, mid + h),
                strokeWidth = barWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CodeBlock(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun Bullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    val tenths = (ms % 1000) / 100
    return if (m > 0) "%d:%02d.%d".format(m, s, tenths) else "%d.%d s".format(s, tenths)
}

@Composable
fun BoxScopeCenter(content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) { content() }
}
