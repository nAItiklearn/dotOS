package com.dotos.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Raw palette. UI code should read [LocalDotColors] instead of these constants. */
object DotPalette {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Grey950 = Color(0xFF0E0E0E)
    val Grey900 = Color(0xFF1A1A1A)
    val Grey850 = Color(0xFF222222)
    val Grey800 = Color(0xFF2A2A2A)
    val Grey600 = Color(0xFF5C5C5C)
    val Grey500 = Color(0xFF8A8A8A)
    val Grey200 = Color(0xFFD9D9D9)
    val Paper = Color(0xFFF2F2F2)
    val SignalRed = Color(0xFFD71921)
}

/** User-selectable accent. NONE falls back to the content colour. */
enum class DotAccent(val color: Color) {
    RED(DotPalette.SignalRed),
    WHITE(Color(0xFFFFFFFF)),
    GREEN(Color(0xFF3DDC84)),
    BLUE(Color(0xFF4C8DFF)),
    AMBER(Color(0xFFFFB020)),
    NONE(Color.Unspecified),
}

@Immutable
data class DotColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val hairline: Color,
    val accent: Color,
    val plate: Color,
    val onPlate: Color,
    val scrim: Color,
) {
    companion object {
        fun dark(accent: Color = DotPalette.SignalRed) = DotColors(
            isDark = true,
            background = DotPalette.Black,
            surface = DotPalette.Grey900,
            surfaceRaised = DotPalette.Grey800,
            onSurface = DotPalette.White,
            onSurfaceMuted = DotPalette.Grey500,
            hairline = Color(0x1FFFFFFF),
            accent = if (accent != Color.Unspecified) accent else DotPalette.White,
            plate = Color(0xF21B1B1B),
            onPlate = DotPalette.White,
            scrim = Color(0x99000000),
        )

        fun light(accent: Color = DotPalette.SignalRed) = DotColors(
            isDark = false,
            background = DotPalette.Paper,
            surface = DotPalette.White,
            surfaceRaised = Color(0xFFE8E8E8),
            onSurface = DotPalette.Black,
            onSurfaceMuted = DotPalette.Grey600,
            hairline = Color(0x1F000000),
            accent = if (accent != Color.Unspecified) accent else DotPalette.Black,
            plate = Color(0xF21B1B1B),
            onPlate = DotPalette.White,
            scrim = Color(0x66000000),
        )
    }
}

val LocalDotColors = staticCompositionLocalOf { DotColors.dark() }
