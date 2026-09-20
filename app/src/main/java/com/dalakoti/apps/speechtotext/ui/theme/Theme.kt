package com.dalakoti.apps.speechtotext.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
    primaryContainer = BlueLightContainer,
    onPrimaryContainer = Color(0xFF07293D),
    secondary = AmberLight,
    onSecondary = Color.White,
    background = NeutralLightBg,
    onBackground = NeutralLightInk,
    surface = NeutralLightSurface,
    onSurface = NeutralLightInk,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightInkVariant,
    outline = Color(0xFFB6C3CC),
    outlineVariant = Color(0xFFD3DDE4),
    error = ErrorLight,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF04202F),
    primaryContainer = BlueDarkContainer,
    onPrimaryContainer = Color(0xFFCFE4F1),
    secondary = AmberDark,
    onSecondary = Color(0xFF2E1804),
    background = NeutralDarkBg,
    onBackground = NeutralDarkInk,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkInk,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = NeutralDarkInkVariant,
    outline = Color(0xFF3A4852),
    outlineVariant = Color(0xFF29343C),
    error = ErrorDark,
    onError = Color(0xFF3A100A),
)

@Composable
fun SpeechToTextTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Deliberately not dynamic color: the amber "recording" state has to stay amber
    // whatever wallpaper happens to be on the phone.
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
