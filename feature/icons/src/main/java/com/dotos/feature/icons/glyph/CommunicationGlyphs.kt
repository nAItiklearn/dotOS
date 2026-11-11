package com.dotos.feature.icons.glyph

/** Messaging, mail and social glyphs. */
internal object CommunicationGlyphs {
    val list: List<Glyph> = listOf(
        Glyph("messages") {
            path {
                moveTo(4.5f, 19.5f)
                lineTo(4.5f, 8.5f)
                quadTo(4.5f, 5f, 8f, 5f)
                lineTo(16f, 5f)
                quadTo(19.5f, 5f, 19.5f, 8.5f)
                lineTo(19.5f, 12.5f)
                quadTo(19.5f, 16f, 16f, 16f)
                lineTo(9.5f, 16f)
                close()
            }
            dotRow(10.5f, 8.2f, 12f, 15.8f, r = 1.1f)
        },
        Glyph("chat_alt") {
            rect(9.5f, 4f, 19.5f, 13.5f, radius = 3f)
            clearRect(3.2f, 7.4f, 16.8f, 18.8f, radius = 4.6f)
            rect(4.5f, 8.5f, 15.5f, 17.5f, radius = 3f)
            polyline(6.8f, 17.5f, 6.8f, 20.2f, 9.8f, 17.5f)
        },
        Glyph("mail") {
            rect(4f, 6f, 20f, 18f, radius = 3f)
            polyline(5.4f, 8f, 12f, 13.2f, 18.6f, 8f)
        },
        Glyph("send") {
            path {
                moveTo(20f, 4.2f)
                lineTo(4f, 10.2f)
                lineTo(10.2f, 13.2f)
                lineTo(13.4f, 20f)
                close()
            }
            line(10.2f, 13.2f, 20f, 4.2f)
        },
        Glyph("video_call") {
            rect(4f, 7.2f, 14.6f, 16.8f, radius = 3f)
            fillStroke {
                moveTo(14.6f, 10.8f)
                lineTo(19.8f, 8.2f)
                lineTo(19.8f, 15.8f)
                lineTo(14.6f, 13.2f)
                close()
            }
        },
        Glyph("social") {
            circle(16f, 7.2f, 2.4f)
            path {
                moveTo(12f, 16.6f)
                cubicTo(12f, 14.4f, 13.8f, 13.4f, 16f, 13.4f)
                cubicTo(18.2f, 13.4f, 20f, 14.4f, 20f, 16.6f)
            }
            clearRect(2.2f, 14.4f, 15f, 22f, radius = 4f)
            circle(8.4f, 10.4f, 2.8f)
            path {
                moveTo(3.8f, 19.8f)
                cubicTo(3.8f, 17f, 5.8f, 15.6f, 8.4f, 15.6f)
                cubicTo(11f, 15.6f, 13f, 17f, 13f, 19.8f)
            }
        },
        Glyph("feedback") {
            line(6f, 4.5f, 6f, 19.5f)
            path {
                moveTo(6f, 5.5f)
                lineTo(18.5f, 5.5f)
                lineTo(15.5f, 9.5f)
                lineTo(18.5f, 13.5f)
                lineTo(6f, 13.5f)
            }
        },
    )
}
