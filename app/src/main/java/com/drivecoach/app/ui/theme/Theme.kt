package com.drivecoach.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AutomotivePrecisionColorScheme = darkColorScheme(
    primary = OemColors.AccentPrimary,
    onPrimary = OemColors.TextOnAccent,
    primaryContainer = OemColors.AccentPrimarySoft,
    onPrimaryContainer = OemColors.TextPrimary,
    secondary = OemColors.AccentPrimary,
    onSecondary = OemColors.TextOnAccent,
    secondaryContainer = OemColors.SurfaceElevated,
    onSecondaryContainer = OemColors.TextPrimary,
    tertiary = OemColors.StatusWarning,
    error = OemColors.StatusAlert,
    background = OemColors.BackgroundPrimary,
    onBackground = OemColors.TextPrimary,
    surface = OemColors.SurfacePrimary,
    onSurface = OemColors.TextPrimary,
    surfaceVariant = OemColors.SurfaceSecondary,
    onSurfaceVariant = OemColors.TextSecondary,
    outline = OemColors.Divider,
)

@Composable
fun DriveCoachTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AutomotivePrecisionColorScheme,
        typography = Typography,
        content = content,
    )
}
