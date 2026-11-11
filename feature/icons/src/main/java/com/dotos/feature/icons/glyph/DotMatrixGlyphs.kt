package com.dotos.feature.icons.glyph

import kotlin.math.abs

/**
 * Glyphs built purely from dots on a regular grid, in the spirit of dot-matrix lettering: dot
 * diameter is about 0.9 x the pitch so neighbouring dots almost touch.
 */
internal object DotMatrixGlyphs {
    val list: List<Glyph> = listOf(
        // Cloud on an 8 x 6 grid, pitch 2.2.
        Glyph("weather_dots") {
            dotRow(6.5f, 13.1f, 15.3f, r = 1f)
            dotRow(8.7f, 8.7f, 10.9f, 13.1f, 15.3f, 17.5f, r = 1f)
            dotRow(10.9f, 6.5f, 8.7f, 10.9f, 13.1f, 15.3f, 17.5f, 19.7f, r = 1f)
            dotRow(13.1f, 4.3f, 6.5f, 8.7f, 10.9f, 13.1f, 15.3f, 17.5f, 19.7f, r = 1f)
            dotRow(15.3f, 4.3f, 6.5f, 8.7f, 10.9f, 13.1f, 15.3f, 17.5f, 19.7f, r = 1f)
            dotRow(17.5f, 6.5f, 8.7f, 10.9f, 13.1f, 15.3f, 17.5f, r = 1f)
        },
        // A framed square with a solid 2 x 2 core.
        Glyph("grid_dots") {
            for (i in 0 until 6) {
                for (j in 0 until 6) {
                    val edge = (i == 0 || i == 5 || j == 0 || j == 5)
                    val core = ((i == 2 || i == 3) && (j == 2 || j == 3))
                    if (edge || core) {
                        dot(6f + i * 2.4f, 6f + j * 2.4f, 1.05f)
                    }
                }
            }
        },
        // A ring of twelve dots around a larger hub dot.
        Glyph("glyph_dots") {
            for (i in 0 until 12) {
                rotated(i * 30f) {
                    dot(12f, 4.8f, 1.05f)
                }
            }
            dot(12f, 12f, 2.4f)
        },
        // Four-point sparkle on a 9 x 9 grid, pitch 2.0.
        Glyph("assistant") {
            for (i in 0 until 9) {
                for (j in 0 until 9) {
                    val dx = abs(i - 4)
                    val dy = abs(j - 4)
                    if (dx * dy <= 1) {
                        dot(12f + (i - 4) * 2f, 12f + (j - 4) * 2f, 0.9f)
                    }
                }
            }
        },
        // Heart on an 8 x 7 grid, pitch 2.2.
        Glyph("heart_dots") {
            dotRow(5.4f, 6.5f, 8.7f, 15.3f, 17.5f, r = 1f)
            dotRow(7.6f, 4.3f, 6.5f, 8.7f, 10.9f, 13.1f, 15.3f, 17.5f, 19.7f, r = 1f)
            dotRow(9.8f, 4.3f, 6.5f, 8.7f, 10.9f, 13.1f, 15.3f, 17.5f, 19.7f, r = 1f)
            dotRow(12f, 4.3f, 6.5f, 8.7f, 10.9f, 13.1f, 15.3f, 17.5f, 19.7f, r = 1f)
            dotRow(14.2f, 6.5f, 8.7f, 10.9f, 13.1f, 15.3f, 17.5f, r = 1f)
            dotRow(16.4f, 8.7f, 10.9f, 13.1f, 15.3f, r = 1f)
            dotRow(18.6f, 10.9f, 13.1f, r = 1f)
        },
    )
}
