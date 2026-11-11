package com.dotos.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Bundled OFL fonts:
 *  - Doto: dot-matrix display face (variable: wght + ROND). Round dots by default.
 *  - Space Grotesk: UI labels.
 *  - Space Mono: data readouts.
 */
object DotFonts {
    private fun doto(weight: Int) = Font(
        resId = R.font.doto,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("ROND", 100f),
        ),
    )

    private fun grotesk(weight: Int) = Font(
        resId = R.font.space_grotesk,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    val Doto = FontFamily(doto(400), doto(600), doto(700), doto(900))
    val Grotesk = FontFamily(grotesk(300), grotesk(400), grotesk(500), grotesk(700))
    val Mono = FontFamily(
        Font(R.font.space_mono, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )
}

@Immutable
data class DotTypography(
    val displayLarge: TextStyle,
    val display: TextStyle,
    val dotTitle: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val sectionLabel: TextStyle,
    val iconLabel: TextStyle,
    val mono: TextStyle,
) {
    companion object {
        fun default(useDotDisplay: Boolean = true): DotTypography {
            val displayFamily = if (useDotDisplay) DotFonts.Doto else DotFonts.Grotesk
            return DotTypography(
                displayLarge = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 72.sp),
                display = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 42.sp),
                dotTitle = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 24.sp),
                title = TextStyle(fontFamily = DotFonts.Grotesk, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
                body = TextStyle(fontFamily = DotFonts.Grotesk, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
                bodySmall = TextStyle(fontFamily = DotFonts.Grotesk, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp),
                sectionLabel = TextStyle(
                    fontFamily = DotFonts.Grotesk,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.16.em,
                ),
                iconLabel = TextStyle(fontFamily = DotFonts.Grotesk, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 14.sp),
                mono = TextStyle(fontFamily = DotFonts.Mono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 16.sp),
            )
        }
    }
}

val LocalDotTypography = staticCompositionLocalOf { DotTypography.default() }
