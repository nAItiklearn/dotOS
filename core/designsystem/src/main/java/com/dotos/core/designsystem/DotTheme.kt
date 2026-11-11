package com.dotos.core.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

object DotTheme {
    val colors: DotColors
        @Composable get() = LocalDotColors.current
    val type: DotTypography
        @Composable get() = LocalDotTypography.current
}

@Composable
fun DotTheme(
    dark: Boolean = true,
    accent: Color = DotPalette.SignalRed,
    useDotDisplayFont: Boolean = true,
    reduceMotion: Boolean = false,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = remember(dark, accent) { if (dark) DotColors.dark(accent) else DotColors.light(accent) }
    val typography = remember(useDotDisplayFont) { DotTypography.default(useDotDisplayFont) }
    val systemNoAnim = rememberSystemAnimationsDisabled()
    val material = remember(colors) { colors.toMaterial() }
    MaterialTheme(colorScheme = material) {
        CompositionLocalProvider(
            LocalDotColors provides colors,
            LocalDotTypography provides typography,
            LocalReduceMotion provides (reduceMotion || systemNoAnim),
            LocalHapticsEnabled provides hapticsEnabled,
            LocalIndication provides ripple(color = colors.onSurface),
            content = content,
        )
    }
}

private fun DotColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = onSurface,
        onPrimary = background,
        primaryContainer = surfaceRaised,
        onPrimaryContainer = onSurface,
        secondary = accent,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = onSurfaceMuted,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceRaised,
        outline = hairline,
        outlineVariant = hairline,
        error = DotPalette.SignalRed,
    )
}
