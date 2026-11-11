package com.dotos.feature.icons.glyph

/**
 * System / utility glyphs. Original geometry on the 24-unit grid: 2-unit round monoline, live area
 * 4..20, counters kept >= 3 units wide so they survive the dot-matrix resample.
 */
internal object SystemGlyphs {
    val list: List<Glyph> = listOf(
        Glyph("phone") {
            path {
                moveTo(7.73f, 4.4f)
                lineTo(10.1f, 4.4f)
                lineTo(11.53f, 8.2f)
                lineTo(9.62f, 9.62f)
                cubicTo(10.57f, 11.81f, 12.19f, 13.43f, 14.38f, 14.38f)
                lineTo(15.8f, 12.47f)
                lineTo(19.6f, 13.9f)
                lineTo(19.6f, 16.27f)
                cubicTo(19.6f, 18.08f, 18.08f, 19.6f, 16.27f, 19.6f)
                cubicTo(9.72f, 19.12f, 4.88f, 14.28f, 4.4f, 7.73f)
                cubicTo(4.4f, 5.92f, 5.92f, 4.4f, 7.73f, 4.4f)
                close()
            }
        },
        Glyph("phone_manager") {
            rect(7f, 4f, 17f, 20f, radius = 3f)
            polyline(9.6f, 12.4f, 11.4f, 14.2f, 14.6f, 10.2f)
        },
        Glyph("contacts") {
            circle(12f, 7.6f, 3f)
            path {
                moveTo(5f, 19.5f)
                cubicTo(5f, 16.6f, 8f, 15.2f, 12f, 15.2f)
                cubicTo(16f, 15.2f, 19f, 16.6f, 19f, 19.5f)
            }
        },
        Glyph("settings") {
            fillStroke { gear(12f, 12f, 7.6f, 6.1f, 8, 6.5f, 10.5f) }
            clearCircle(12f, 12f, 3f)
        },
        Glyph("clock") {
            circle(12f, 12f, 7.5f)
            polyline(12f, 8.6f, 12f, 12f, 14.8f, 13.6f)
        },
        Glyph("alarm") {
            circle(12f, 12.6f, 6.4f)
            polyline(12f, 9.6f, 12f, 12.6f, 14.4f, 14f)
            line(6.9f, 7.5f, 4.6f, 5.2f)
            line(17.1f, 7.5f, 19.4f, 5.2f)
        },
        Glyph("timer") {
            polyline(7.5f, 4.5f, 16.5f, 4.5f, 12f, 12f, 16.5f, 19.5f, 7.5f, 19.5f, 12f, 12f, closed = true)
        },
        Glyph("stopwatch") {
            circle(12f, 13.5f, 6.5f)
            line(10.2f, 4.2f, 13.8f, 4.2f)
            line(12f, 4.2f, 12f, 7f)
            line(17.2f, 8.3f, 18.8f, 6.7f)
            line(12f, 13.5f, 14.6f, 10.9f)
        },
        Glyph("world") {
            circle(12f, 12f, 7.5f)
            path {
                moveTo(12f, 4.5f)
                cubicTo(8.4f, 8f, 8.4f, 16f, 12f, 19.5f)
                moveTo(12f, 4.5f)
                cubicTo(15.6f, 8f, 15.6f, 16f, 12f, 19.5f)
            }
        },
        Glyph("calculator") {
            rect(4.5f, 4f, 19.5f, 20f, radius = 3f)
            line(8f, 8.2f, 16f, 8.2f)
            for (row in 0 until 2) {
                for (col in 0 until 3) {
                    dot(8f + col * 4f, 12.8f + row * 3.8f, 1f)
                }
            }
        },
        Glyph("calendar") {
            rect(5f, 6f, 19f, 19.5f, radius = 3f)
            line(8.8f, 3.6f, 8.8f, 7.6f)
            line(15.2f, 3.6f, 15.2f, 7.6f)
            line(5f, 10.4f, 19f, 10.4f)
            for (row in 0 until 2) {
                for (col in 0 until 3) {
                    dot(8.2f + col * 3.8f, 13.6f + row * 3.2f, 0.95f)
                }
            }
        },
        Glyph("files") {
            path {
                moveTo(4.5f, 8f)
                cubicTo(4.5f, 6.2f, 5.7f, 5.5f, 7f, 5.5f)
                lineTo(9.8f, 5.5f)
                lineTo(11.8f, 8f)
                lineTo(17.5f, 8f)
                cubicTo(18.8f, 8f, 19.5f, 8.9f, 19.5f, 10f)
                lineTo(19.5f, 17f)
                cubicTo(19.5f, 18.3f, 18.6f, 19f, 17.5f, 19f)
                lineTo(6.5f, 19f)
                cubicTo(5.3f, 19f, 4.5f, 18.2f, 4.5f, 17f)
                close()
            }
        },
        Glyph("downloads") {
            line(12f, 4.5f, 12f, 14.5f)
            polyline(7.8f, 10.5f, 12f, 14.7f, 16.2f, 10.5f)
            polyline(5f, 15.5f, 5f, 19.5f, 19f, 19.5f, 19f, 15.5f)
        },
        Glyph("security") {
            path {
                moveTo(12f, 4f)
                lineTo(18.5f, 6.6f)
                lineTo(18.5f, 11.6f)
                cubicTo(18.5f, 15.6f, 15.8f, 18.6f, 12f, 20f)
                cubicTo(8.2f, 18.6f, 5.5f, 15.6f, 5.5f, 11.6f)
                lineTo(5.5f, 6.6f)
                close()
            }
            polyline(9f, 11.8f, 11.2f, 14f, 15f, 9.6f)
        },
        Glyph("lock") {
            rect(6.5f, 10.5f, 17.5f, 20f, radius = 2.5f)
            path {
                moveTo(8.8f, 10.5f)
                lineTo(8.8f, 8.2f)
                cubicTo(8.8f, 6.4f, 10.2f, 5f, 12f, 5f)
                cubicTo(13.8f, 5f, 15.2f, 6.4f, 15.2f, 8.2f)
                lineTo(15.2f, 10.5f)
            }
            dot(12f, 14.4f, 1.3f)
            line(12f, 14.4f, 12f, 16.4f)
        },
        Glyph("key") {
            rotated(-45f) {
                circle(6.75f, 12f, 3.5f)
                line(10.25f, 12f, 20.75f, 12f)
                line(16f, 12f, 16f, 15.2f)
                line(20.75f, 12f, 20.75f, 15.2f)
            }
        },
        Glyph("vpn") {
            path {
                moveTo(12f, 4f)
                lineTo(18.5f, 6.6f)
                lineTo(18.5f, 11.6f)
                cubicTo(18.5f, 15.6f, 15.8f, 18.6f, 12f, 20f)
                cubicTo(8.2f, 18.6f, 5.5f, 15.6f, 5.5f, 11.6f)
                lineTo(5.5f, 6.6f)
                close()
            }
            dot(12f, 10.8f, 1.6f)
            line(12f, 10.8f, 12f, 14.8f)
        },
        Glyph("wifi") {
            dot(12f, 16.8f, 1.5f)
            arc(12f, 16.8f, 5.5f, 225f, 90f)
            arc(12f, 16.8f, 10f, 225f, 90f)
        },
        Glyph("bluetooth") {
            line(12f, 4f, 12f, 20f)
            polyline(12f, 4f, 17.6f, 8.1f, 6.4f, 15.9f)
            polyline(6.4f, 8.1f, 17.6f, 15.9f, 12f, 20f)
        },
        Glyph("battery") {
            rect(4f, 6.5f, 17.5f, 17.5f, radius = 3f)
            line(20f, 10.8f, 20f, 13.2f)
            rect(7.2f, 10f, 12.8f, 14f, radius = 1f, filled = true)
        },
        Glyph("home") {
            path {
                moveTo(4.5f, 19.5f)
                lineTo(9.5f, 19.5f)
                lineTo(9.5f, 14.5f)
                lineTo(14.5f, 14.5f)
                lineTo(14.5f, 19.5f)
                lineTo(19.5f, 19.5f)
                lineTo(19.5f, 11f)
                lineTo(12f, 4.5f)
                lineTo(4.5f, 11f)
                close()
            }
        },
        Glyph("sim") {
            path {
                moveTo(8f, 4f)
                lineTo(13.5f, 4f)
                lineTo(18f, 8.5f)
                lineTo(18f, 18f)
                cubicTo(18f, 19.1f, 17.1f, 20f, 16f, 20f)
                lineTo(8f, 20f)
                cubicTo(6.9f, 20f, 6f, 19.1f, 6f, 18f)
                lineTo(6f, 6f)
                cubicTo(6f, 4.9f, 6.9f, 4f, 8f, 4f)
                close()
            }
            rect(9.5f, 11.5f, 14.5f, 16.5f, radius = 1f, filled = true)
        },
        Glyph("app_generic") {
            rect(5f, 5f, 19f, 19f, radius = 4f)
            dot(12f, 12f, 1.6f)
        },
        Glyph("dot_grid") {
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    dot(6.5f + col * 5.5f, 6.5f + row * 5.5f, 1.5f)
                }
            }
        },
        Glyph("search") {
            circle(10.5f, 10.5f, 5.8f)
            line(14.8f, 14.8f, 19.5f, 19.5f)
        },
        Glyph("help") {
            path {
                moveTo(8.2f, 8.6f)
                cubicTo(8.2f, 5.3f, 15.8f, 5.3f, 15.8f, 8.6f)
                cubicTo(15.8f, 11.6f, 12f, 11.8f, 12f, 14.2f)
            }
            dot(12f, 18.6f, 1.4f)
        },
        Glyph("scanner") {
            polyline(5f, 9f, 5f, 5f, 9f, 5f)
            polyline(15f, 5f, 19f, 5f, 19f, 9f)
            polyline(19f, 15f, 19f, 19f, 15f, 19f)
            polyline(9f, 19f, 5f, 19f, 5f, 15f)
            line(8f, 12f, 16f, 12f)
        },
        Glyph("print") {
            rect(4.5f, 10.4f, 19.5f, 18.4f, radius = 2.5f)
            polyline(7f, 10.4f, 7f, 5f, 17f, 5f, 17f, 10.4f)
            dot(16.2f, 14.4f, 1.1f)
        },
        Glyph("keyboard") {
            rect(4f, 6.5f, 20f, 18f, radius = 2.5f)
            dotRow(10.2f, 8f, 12f, 16f, r = 1.1f)
            line(8.5f, 14.4f, 15.5f, 14.4f)
        },
        Glyph("backup") {
            path { cloud(5.2f, 13f) }
            line(12f, 9.4f, 12f, 14.6f)
            polyline(9.7f, 11.7f, 12f, 9.4f, 14.3f, 11.7f)
        },
        Glyph("cloud_storage") {
            path { cloud(6.4f) }
        },
        Glyph("app_store") {
            polyline(6.4f, 9.2f, 17.6f, 9.2f, 19f, 19.8f, 5f, 19.8f, closed = true)
            arc(12f, 7.2f, 3f, 180f, 180f)
            line(9f, 7.2f, 9f, 9.2f)
            line(15f, 7.2f, 15f, 9.2f)
            line(12f, 12.6f, 12f, 15.8f)
            polyline(10.2f, 14f, 12f, 15.8f, 13.8f, 14f)
        },
    )
}
