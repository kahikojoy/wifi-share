package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class AppThemeMode {
    DARK_NAVY,
    CRISP_LIGHT,
    SYSTEM
}

private val WifiShareDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = ElectricBlueOnContainer,
    secondary = CyanPulse,
    onSecondary = Color(0xFF002129),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = EmeraldSignal,
    onTertiary = Color(0xFF002619),
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldOnContainer,
    background = Navy950,
    onBackground = SlateTextPrimary,
    surface = Navy900,
    onSurface = SlateTextPrimary,
    surfaceVariant = Navy800,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerLowest = Navy950,
    surfaceContainerLow = Navy900,
    surfaceContainer = Navy850,
    surfaceContainerHigh = Navy800,
    surfaceContainerHighest = Navy750,
    outline = NavyBorder,
    outlineVariant = Color(0xFF1C2E4D),
    error = CoralAlert,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = CoralOnContainer
)

private val WifiShareLightColorScheme = lightColorScheme(
    primary = LightBluePrimary,
    onPrimary = Color.White,
    primaryContainer = LightBlueContainer,
    onPrimaryContainer = LightBlueOnContainer,
    secondary = LightCyanSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightCyanContainer,
    onSecondaryContainer = LightCyanOnContainer,
    tertiary = LightEmeraldTertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightEmeraldContainer,
    onTertiaryContainer = LightEmeraldOnContainer,
    background = MistBackground,
    onBackground = LightTextPrimary,
    surface = MistSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = MistSurfaceLow,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = MistSurface,
    surfaceContainerLow = MistBackground,
    surfaceContainer = MistSurfaceLow,
    surfaceContainerHigh = MistSurfaceHigh,
    surfaceContainerHighest = Color(0xFFD2E0F5),
    outline = MistBorder,
    outlineVariant = Color(0xFFD8E4F3),
    error = Color(0xFFBA1A2B),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410006)
)

val WifiShareShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun WifiShareTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK_NAVY,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        AppThemeMode.DARK_NAVY -> true
        AppThemeMode.CRISP_LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = if (useDarkTheme) WifiShareDarkColorScheme else WifiShareLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WifiShareTypography,
        shapes = WifiShareShapes,
        content = content
    )
}
