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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CoralPrimaryDark,
    onPrimary = OnCoralPrimaryDark,
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = OnCoralContainerDark,
    secondary = TealSecondaryDark,
    onSecondary = OnTealSecondaryDark,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = OnTealContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = OnAmberTertiaryDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = StudioBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = StudioSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = StudioBackgroundDark,
    surfaceContainerLow = StudioSurfaceDark,
    surfaceContainer = StudioSurfaceVariantDark,
    surfaceContainerHigh = StudioSurfaceElevatedDark,
    outline = StudioOutlineDark,
    outlineVariant = StudioOutlineDark.copy(alpha = 0.5f),
    error = ErrorDark,
    onError = OnErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = CoralPrimaryLight,
    onPrimary = OnCoralPrimaryLight,
    primaryContainer = CoralContainerLight,
    onPrimaryContainer = OnCoralContainerLight,
    secondary = TealSecondaryLight,
    onSecondary = OnTealSecondaryLight,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = OnTealContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = OnAmberTertiaryLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    background = StudioBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = StudioSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = StudioSurfaceLight,
    surfaceContainerLow = StudioBackgroundLight,
    surfaceContainer = StudioSurfaceVariantLight,
    outline = StudioOutlineLight,
    outlineVariant = StudioOutlineLight.copy(alpha = 0.6f),
    error = ErrorLight,
    onError = OnErrorLight
)

val PodDeckShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PodDeckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PodDeckShapes,
        content = content
    )
}
