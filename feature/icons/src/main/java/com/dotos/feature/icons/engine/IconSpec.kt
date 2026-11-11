package com.dotos.feature.icons.engine

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.dotos.core.data.settings.DotShape
import com.dotos.core.data.settings.IconStyle
import com.dotos.core.data.settings.PlateShape

/** Everything that affects how an icon looks. Part of every cache key. */
@Immutable
data class IconSpec(
    val style: IconStyle = IconStyle.LINE,
    val plateShape: PlateShape = PlateShape.CIRCLE,
    val plateColor: Color = Color(0xF21B1B1B),
    val glyphColor: Color = Color.White,
    val dotDensity: Int = 18,
    val dotShape: DotShape = DotShape.ROUND,
    /** Glyph size relative to the plate (1 = default). */
    val glyphScale: Float = 1f,
    val iconPack: String? = null,
) {
    /** Only the parts that change the rendered glyph bitmap (plate is drawn live by Compose). */
    val renderKey: Int
        get() = arrayOf(style, glyphColor.value, dotDensity, dotShape, glyphScale, iconPack, plateShape).contentHashCode()
}

val LocalIconSpec = staticCompositionLocalOf { IconSpec() }
val LocalIconEngine = staticCompositionLocalOf<IconEngine?> { null }
