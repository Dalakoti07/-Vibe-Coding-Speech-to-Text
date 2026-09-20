package com.dalakoti.apps.speechtotext.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    // Transcripts are the point of the app; give them room.
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
    labelSmall = base.labelSmall.copy(letterSpacing = 1.2.sp),
)

/** Numbers that tick — timers, memory, milliseconds — read better monospaced. */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace)
