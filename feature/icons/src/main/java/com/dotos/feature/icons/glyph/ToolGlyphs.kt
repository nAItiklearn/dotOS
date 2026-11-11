package com.dotos.feature.icons.glyph

/** Productivity and utility glyphs. */
internal object ToolGlyphs {
    val list: List<Glyph> = listOf(
        Glyph("notes") {
            rect(5.5f, 4f, 18.5f, 20f, radius = 2.5f)
            line(9f, 9f, 15f, 9f)
            line(9f, 12.8f, 15f, 12.8f)
            line(9f, 16.6f, 12.5f, 16.6f)
        },
        Glyph("tasks") {
            rect(4.5f, 4.5f, 19.5f, 19.5f, radius = 4f)
            polyline(8.5f, 12.2f, 11f, 14.7f, 15.8f, 9.6f)
        },
        Glyph("browser") {
            circle(12f, 12f, 7.5f)
            path {
                moveTo(12f, 4.5f)
                cubicTo(8.4f, 8f, 8.4f, 16f, 12f, 19.5f)
                moveTo(12f, 4.5f)
                cubicTo(15.6f, 8f, 15.6f, 16f, 12f, 19.5f)
            }
            line(4.5f, 12f, 19.5f, 12f)
        },
        Glyph("flashlight") {
            path {
                moveTo(7f, 4.5f)
                lineTo(17f, 4.5f)
                lineTo(17f, 8f)
                lineTo(15f, 10.5f)
                lineTo(15f, 19.5f)
                lineTo(9f, 19.5f)
                lineTo(9f, 10.5f)
                lineTo(7f, 8f)
                close()
            }
        },
        Glyph("compass") {
            circle(12f, 12f, 7.5f)
            fillStroke {
                moveTo(14.7f, 9.3f)
                lineTo(13.5f, 13.5f)
                lineTo(9.3f, 14.7f)
                lineTo(10.5f, 10.5f)
                close()
            }
            clearCircle(12f, 12f, 0.9f)
        },
        Glyph("translate") {
            polyline(7.4f, 12.4f, 12f, 4.8f, 16.6f, 12.4f)
            line(9.2f, 10f, 14.8f, 10f)
            line(5f, 18.2f, 19f, 18.2f)
            polyline(7.2f, 16.2f, 5f, 18.2f, 7.2f, 20.2f)
            polyline(16.8f, 16.2f, 19f, 18.2f, 16.8f, 20.2f)
        },
        Glyph("ai") {
            fillStroke { sparkle(10.6f, 13.4f, 6.6f, 1.7f) }
            fillStroke { sparkle(17.2f, 6.8f, 2.8f, 0.8f) }
        },
    )
}
