package com.dotos.feature.icons.engine

import androidx.compose.ui.graphics.Color
import com.dotos.core.data.settings.Accent
import com.dotos.core.data.settings.GlyphTint
import com.dotos.core.data.settings.IconStyle
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.PlateColor

/** Accent enum → colour (shared by every screen so previews match the home screen exactly). */
fun Accent.color(): Color = when (this) {
    Accent.RED -> Color(0xFFD71921)
    Accent.WHITE -> Color(0xFFFFFFFF)
    Accent.GREEN -> Color(0xFF3DDC84)
    Accent.BLUE -> Color(0xFF4C8DFF)
    Accent.AMBER -> Color(0xFFFFB020)
    Accent.NONE -> Color(0xFFFFFFFF)
}

/** Single source of truth for turning settings into an [IconSpec]. */
fun LauncherSettings.toIconSpec(themedPlate: Color? = null, themedGlyph: Color? = null): IconSpec {
    val accent = accent.color()
    val plate = when (plateColor) {
        PlateColor.DARK -> Color(0xFF1A1A1A)
        PlateColor.BLACK -> Color(0xFF000000)
        PlateColor.WHITE -> Color(0xFFF2F2F2)
        PlateColor.ACCENT -> accent
        PlateColor.TRANSPARENT -> Color.Transparent
    }
    var glyph = when (glyphTint) {
        GlyphTint.WHITE -> Color.White
        GlyphTint.BLACK -> Color.Black
        GlyphTint.ACCENT -> accent
    }
    // Keep contrast automatic when the plate is light.
    if (plateColor == PlateColor.WHITE && glyphTint == GlyphTint.WHITE) glyph = Color.Black
    if (iconStyle == IconStyle.THEMED && themedPlate != null && themedGlyph != null) {
        return IconSpec(iconStyle, plateShape, themedPlate, themedGlyph, dotDensity, dotShape, iconScale, iconPackPackage)
    }
    return IconSpec(
        style = iconStyle,
        plateShape = plateShape,
        plateColor = plate,
        glyphColor = glyph,
        dotDensity = dotDensity,
        dotShape = dotShape,
        glyphScale = iconScale,
        iconPack = iconPackPackage,
    )
}
