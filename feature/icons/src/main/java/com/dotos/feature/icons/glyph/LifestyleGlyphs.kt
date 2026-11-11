package com.dotos.feature.icons.glyph

/** Travel, health, money, home and leisure glyphs. */
internal object LifestyleGlyphs {
    val list: List<Glyph> = listOf(
        Glyph("maps") {
            path {
                moveTo(12f, 19.5f)
                cubicTo(12f, 19.5f, 6f, 14f, 6f, 9.8f)
                cubicTo(6f, 6.5f, 8.7f, 4.3f, 12f, 4.3f)
                cubicTo(15.3f, 4.3f, 18f, 6.5f, 18f, 9.8f)
                cubicTo(18f, 14f, 12f, 19.5f, 12f, 19.5f)
                close()
            }
            dot(12f, 9.8f, 2.2f)
        },
        Glyph("navigation") {
            fillStroke {
                moveTo(19.5f, 4.5f)
                lineTo(12.6f, 19.5f)
                lineTo(10.8f, 13.2f)
                lineTo(4.5f, 11.4f)
                close()
            }
        },
        Glyph("weather") {
            dot(17.4f, 7.4f, 2.4f)
            path { cloudAt(3.8f, 10.4f, 13f, 8.8f) }
        },
        Glyph("weather_sun") {
            circle(12f, 12f, 2.8f)
            for (i in 0 until 8) {
                rotated(i * 45f) {
                    line(12f, 5.7f, 12f, 4.3f)
                }
            }
        },
        Glyph("weather_rain") {
            path { cloudAt(4f, 4.2f, 16f, 8.4f) }
            line(8.2f, 16.4f, 7.2f, 19.2f)
            line(12.2f, 16.4f, 11.2f, 19.2f)
            line(16.2f, 16.4f, 15.2f, 19.2f)
        },
        Glyph("health") {
            path { heart(12f, 12f, 7.6f) }
            line(12f, 9.4f, 12f, 14.4f)
            line(9.5f, 11.9f, 14.5f, 11.9f)
        },
        Glyph("fitness") {
            dot(15.4f, 5.4f, 1.9f)
            polyline(14f, 8.8f, 11.2f, 13.4f)
            polyline(13.6f, 9.6f, 9.8f, 9.6f, 7.8f, 12.4f)
            polyline(13.8f, 9.8f, 17.2f, 11.2f, 19f, 9f)
            polyline(11.2f, 13.4f, 15.4f, 15f, 14.4f, 19.6f)
            polyline(11.2f, 13.4f, 8.2f, 17f, 4.8f, 16.2f)
        },
        Glyph("wallet") {
            rect(4f, 6.5f, 20f, 17.5f, radius = 3f)
            line(4f, 10.6f, 20f, 10.6f)
            line(7.2f, 14.5f, 11.2f, 14.5f)
        },
        Glyph("store") {
            polyline(4.2f, 10.5f, 5.6f, 5f, 18.4f, 5f, 19.8f, 10.5f, closed = true)
            polyline(5.8f, 10.5f, 5.8f, 19.5f, 9.8f, 19.5f, 9.8f, 14.6f, 14.2f, 14.6f, 14.2f, 19.5f, 18.2f, 19.5f, 18.2f, 10.5f)
        },
        Glyph("shop") {
            polyline(6.6f, 5.2f, 17.4f, 5.2f, 19.2f, 18.7f, 4.8f, 18.7f, closed = true)
            line(8.8f, 5.2f, 8.8f, 8.2f)
            line(15.2f, 5.2f, 15.2f, 8.2f)
            arc(12f, 8.2f, 3.2f, 0f, 180f)
        },
        Glyph("bank") {
            fillStroke {
                moveTo(4.5f, 9f)
                lineTo(12f, 4.6f)
                lineTo(19.5f, 9f)
                close()
            }
            line(7.6f, 12.6f, 7.6f, 16.2f)
            line(12f, 12.6f, 12f, 16.2f)
            line(16.4f, 12.6f, 16.4f, 16.2f)
            line(4.5f, 19.6f, 19.5f, 19.6f)
        },
        Glyph("payments") {
            circle(12f, 12f, 7.5f)
            path {
                moveTo(14.3f, 9.6f)
                cubicTo(14.3f, 8.1f, 9.7f, 8.1f, 9.7f, 10.3f)
                cubicTo(9.7f, 12.2f, 14.3f, 11.8f, 14.3f, 13.8f)
                cubicTo(14.3f, 16.1f, 9.7f, 15.9f, 9.7f, 14.4f)
            }
        },
        Glyph("smart_home") {
            path {
                moveTo(9.4f, 15.4f)
                cubicTo(7.5f, 14.1f, 6.5f, 12.3f, 6.5f, 10f)
                cubicTo(6.5f, 6.8f, 9f, 4.4f, 12f, 4.4f)
                cubicTo(15f, 4.4f, 17.5f, 6.8f, 17.5f, 10f)
                cubicTo(17.5f, 12.3f, 16.5f, 14.1f, 14.6f, 15.4f)
                close()
            }
            line(10f, 18.8f, 14f, 18.8f)
            polyline(10.2f, 9.6f, 12f, 11.8f, 13.8f, 9.6f)
            line(12f, 11.8f, 12f, 15.4f)
        },
        Glyph("car") {
            path {
                moveTo(4f, 14f)
                lineTo(4f, 11.4f)
                lineTo(6.2f, 7.8f)
                lineTo(15f, 7.8f)
                lineTo(17.8f, 10.6f)
                lineTo(20f, 11.2f)
                lineTo(20f, 14f)
                close()
            }
            clearCircle(7.4f, 14.4f, 3.7f)
            clearCircle(16.6f, 14.4f, 3.7f)
            circle(7.4f, 14.4f, 2.4f, filled = true)
            circle(16.6f, 14.4f, 2.4f, filled = true)
        },
        Glyph("train") {
            rect(6f, 4f, 18f, 17f, radius = 3.5f)
            line(6f, 10f, 18f, 10f)
            dot(9f, 13.6f, 1.1f)
            dot(15f, 13.6f, 1.1f)
            line(8.4f, 17f, 6.6f, 20f)
            line(15.6f, 17f, 17.4f, 20f)
        },
        Glyph("flight") {
            fillStroke {
                moveTo(12f, 4.57f)
                lineTo(13.33f, 8.76f)
                lineTo(19.6f, 12.94f)
                lineTo(19.6f, 14.8f)
                lineTo(13.33f, 12.94f)
                lineTo(12.95f, 17.13f)
                lineTo(15.32f, 18.8f)
                lineTo(15.32f, 19.92f)
                lineTo(12f, 19.08f)
                lineTo(8.68f, 19.92f)
                lineTo(8.68f, 18.8f)
                lineTo(11.05f, 17.13f)
                lineTo(10.67f, 12.94f)
                lineTo(4.4f, 14.8f)
                lineTo(4.4f, 12.94f)
                lineTo(10.67f, 8.76f)
                close()
            }
        },
        Glyph("food") {
            path {
                moveTo(4.5f, 11.5f)
                lineTo(19.5f, 11.5f)
                cubicTo(19.5f, 16.3f, 16f, 19.5f, 12f, 19.5f)
                cubicTo(8f, 19.5f, 4.5f, 16.3f, 4.5f, 11.5f)
                close()
            }
            path {
                moveTo(9.6f, 4.2f)
                cubicTo(8.2f, 5f, 10.8f, 6.2f, 9.6f, 7.8f)
            }
            path {
                moveTo(14.4f, 4.2f)
                cubicTo(13f, 5f, 15.6f, 6.2f, 14.4f, 7.8f)
            }
        },
        Glyph("ride") {
            polyline(7.2f, 10f, 8.6f, 5.2f, 15.4f, 5.2f, 16.8f, 10f)
            rect(4.5f, 10f, 19.5f, 17.8f, radius = 2.5f)
            dot(8.4f, 13.8f, 1.3f)
            dot(15.6f, 13.8f, 1.3f)
            line(7.8f, 17.8f, 7.8f, 19.8f)
            line(16.2f, 17.8f, 16.2f, 19.8f)
        },
        Glyph("gift") {
            rect(4f, 7.4f, 20f, 11.6f, radius = 1.5f)
            polyline(5.6f, 11.6f, 5.6f, 19.5f, 18.4f, 19.5f, 18.4f, 11.6f)
            line(12f, 7.4f, 12f, 19.5f)
            polyline(9f, 4.6f, 12f, 7.4f, 15f, 4.6f)
        },
        Glyph("star") {
            path {
                moveTo(12f, 4.2f)
                lineTo(14.29f, 9.44f)
                lineTo(19.99f, 10f)
                lineTo(15.71f, 13.81f)
                lineTo(16.94f, 19.4f)
                lineTo(12f, 16.5f)
                lineTo(7.06f, 19.4f)
                lineTo(8.29f, 13.81f)
                lineTo(4.01f, 10f)
                lineTo(9.71f, 9.44f)
                close()
            }
        },
        Glyph("ticket") {
            path {
                moveTo(6.5f, 7f)
                lineTo(17.5f, 7f)
                quadTo(20f, 7f, 20f, 9.5f)
                lineTo(20f, 10f)
                cubicTo(18.2f, 10f, 18.2f, 14f, 20f, 14f)
                lineTo(20f, 14.5f)
                quadTo(20f, 17f, 17.5f, 17f)
                lineTo(6.5f, 17f)
                quadTo(4f, 17f, 4f, 14.5f)
                lineTo(4f, 14f)
                cubicTo(5.8f, 14f, 5.8f, 10f, 4f, 10f)
                lineTo(4f, 9.5f)
                quadTo(4f, 7f, 6.5f, 7f)
                close()
            }
            dotRow(9.6f, 14.6f, r = 0.8f)
            dotRow(12f, 14.6f, r = 0.8f)
            dotRow(14.4f, 14.6f, r = 0.8f)
        },
        Glyph("sports") {
            circle(12f, 12f, 7.5f)
            arc(2.4f, 12f, 7f, -50.8f, 101.6f)
            arc(21.6f, 12f, 7f, 129.2f, 101.6f)
        },
        Glyph("weight") {
            line(8.5f, 12f, 15.5f, 12f)
            rect(5.2f, 6.5f, 8.5f, 17.5f, radius = 1.6f, filled = true)
            rect(15.5f, 6.5f, 18.8f, 17.5f, radius = 1.6f, filled = true)
            line(4.2f, 12f, 5.2f, 12f)
            line(18.8f, 12f, 19.8f, 12f)
        },
        Glyph("sleep") {
            arc(11.6f, 12.4f, 7.6f, 17.5f, 256.5f)
            arc(16f, 9.4f, 6f, -130.2f, -168.2f)
            dot(17.6f, 9f, 1.1f)
        },
        Glyph("meditate") {
            path {
                moveTo(12f, 4.8f)
                cubicTo(14.8f, 7.6f, 14.8f, 12.4f, 12f, 15.4f)
                cubicTo(9.2f, 12.4f, 9.2f, 7.6f, 12f, 4.8f)
                close()
            }
            path {
                moveTo(10.6f, 16.8f)
                cubicTo(7f, 16.6f, 4.4f, 14.2f, 4.2f, 10.4f)
                cubicTo(7.6f, 10.4f, 9.9f, 11.8f, 10.6f, 14.4f)
            }
            path {
                moveTo(13.4f, 16.8f)
                cubicTo(17f, 16.6f, 19.6f, 14.2f, 19.8f, 10.4f)
                cubicTo(16.4f, 10.4f, 14.1f, 11.8f, 13.4f, 14.4f)
            }
            line(8f, 20f, 16f, 20f)
        },
    )
}
