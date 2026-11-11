package com.dotos.feature.icons.glyph

/** Camera, gallery, audio and video glyphs. */
internal object MediaGlyphs {
    val list: List<Glyph> = listOf(
        Glyph("camera") {
            rect(4f, 7.2f, 20f, 19.8f, radius = 3.5f)
            fillStroke {
                moveTo(8.6f, 7.2f)
                lineTo(9.9f, 4.6f)
                lineTo(14.1f, 4.6f)
                lineTo(15.4f, 7.2f)
                close()
            }
            circle(12f, 13.5f, 3.2f)
        },
        Glyph("camera_alt") {
            circle(12f, 12f, 7.5f)
            circle(12f, 12f, 3.3f)
        },
        Glyph("gallery") {
            rect(4f, 5f, 20f, 19f, radius = 3f)
            dot(15.8f, 9.6f, 1.4f)
            polyline(5f, 18f, 9.6f, 12.6f, 14f, 17.2f, 16.2f, 15f, 19f, 17.8f)
        },
        Glyph("video") {
            rect(4.5f, 11.5f, 19.5f, 19.5f, radius = 2f)
            rotated(-7f, 4.5f, 8.8f) {
                rect(4.5f, 5f, 19.5f, 8.8f, radius = 1f, filled = true)
                for (i in 0 until 3) {
                    rotated(28f, 8.5f + i * 4.4f, 6.9f) {
                        clearRect(7.9f + i * 4.4f, 4f, 9.1f + i * 4.4f, 9.8f)
                    }
                }
            }
        },
        Glyph("music") {
            circle(7.6f, 17.4f, 2.4f, filled = true)
            circle(16.4f, 15.4f, 2.4f, filled = true)
            line(10f, 17.4f, 10f, 6.6f)
            line(18.8f, 15.4f, 18.8f, 4.8f)
            line(10f, 6.6f, 18.8f, 4.8f)
        },
        Glyph("podcast") {
            arc(12f, 12.5f, 7.5f, 180f, 180f)
            rect(3.5f, 12.5f, 7.5f, 19.5f, radius = 2f, filled = true)
            rect(16.5f, 12.5f, 20.5f, 19.5f, radius = 2f, filled = true)
        },
        Glyph("radio") {
            dot(12f, 9.5f, 1.8f)
            arc(12f, 9.5f, 6.6f, 140f, 80f)
            arc(12f, 9.5f, 6.6f, -40f, 80f)
            line(12f, 11.2f, 12f, 20f)
        },
        Glyph("recorder") {
            rect(9.5f, 4.5f, 14.5f, 14.5f, radius = 2.5f)
            arc(12f, 11f, 6.5f, 0f, 180f)
            line(12f, 17.5f, 12f, 19.2f)
        },
        Glyph("voice") {
            line(4.4f, 9.5f, 4.4f, 14.5f)
            line(8.2f, 7f, 8.2f, 17f)
            line(12f, 4.5f, 12f, 19.5f)
            line(15.8f, 8f, 15.8f, 16f)
            line(19.6f, 10f, 19.6f, 14f)
        },
        Glyph("stream") {
            rect(4f, 5.5f, 20f, 18.5f, radius = 4f)
            fillStroke {
                moveTo(10.4f, 9.4f)
                lineTo(15.6f, 12f)
                lineTo(10.4f, 14.6f)
                close()
            }
        },
        Glyph("tv") {
            rect(4f, 4.5f, 20f, 16f, radius = 2.5f)
            line(12f, 16f, 12f, 19.3f)
            line(8f, 19.3f, 16f, 19.3f)
        },
        Glyph("game") {
            rect(4f, 6.5f, 20f, 17.5f, radius = 5f)
            line(8.4f, 9.9f, 8.4f, 14.1f)
            line(6.3f, 12f, 10.5f, 12f)
            dot(14.8f, 10.6f, 1.1f)
            dot(16.6f, 13.6f, 1.1f)
        },
        Glyph("book") {
            path {
                moveTo(12f, 7f)
                cubicTo(10f, 5.4f, 7f, 5.2f, 4.5f, 6f)
                lineTo(4.5f, 17.6f)
                cubicTo(7f, 16.8f, 10f, 17.2f, 12f, 18.8f)
                cubicTo(14f, 17.2f, 17f, 16.8f, 19.5f, 17.6f)
                lineTo(19.5f, 6f)
                cubicTo(17f, 5.2f, 14f, 5.4f, 12f, 7f)
                close()
            }
            line(12f, 7f, 12f, 18.8f)
        },
        Glyph("news") {
            rect(4.5f, 5f, 19.5f, 19f, radius = 2.5f)
            line(7.8f, 9f, 16.2f, 9f)
            rect(7.8f, 12.4f, 11.4f, 15.8f, filled = true)
            line(14.4f, 14.1f, 16.2f, 14.1f)
        },
    )
}
