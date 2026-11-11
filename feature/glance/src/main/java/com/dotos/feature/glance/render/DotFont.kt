package com.dotos.feature.glance.render

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Tiny original 5x7 dot-matrix font (digits, Latin capitals, a few symbols) drawn as circles.
 * It lets the Glance widgets show crisp dot text inside bitmaps without depending on a typeface:
 * Glance `Text` cannot use bundled fonts, and RemoteViews bitmaps are the only way to keep the look
 * identical on every launcher.
 */
internal object DotFont {
    const val GLYPH_COLS = 5
    const val GLYPH_ROWS = 7

    /** Empty columns between two glyphs. */
    private const val GAP_COLS = 1

    /** Dot diameter relative to the pitch (the grid cell). */
    private const val DOT_FILL = 0.74f

    private val blank = IntArray(GLYPH_ROWS)

    /** One entry per glyph: 7 rows, each a 5-bit mask (bit 4 = leftmost column). */
    private val glyphs: Map<Char, IntArray> = mapOf(
        '0' to intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        '1' to intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
        '2' to intArrayOf(0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111),
        '3' to intArrayOf(0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110),
        '4' to intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010),
        '5' to intArrayOf(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110),
        '6' to intArrayOf(0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110),
        '7' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000),
        '8' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110),
        '9' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100),
        '%' to intArrayOf(0b11000, 0b11001, 0b00010, 0b00100, 0b01000, 0b10011, 0b00011),
        ':' to intArrayOf(0b00000, 0b01100, 0b01100, 0b00000, 0b01100, 0b01100, 0b00000),
        '.' to intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b01100, 0b01100),
        '-' to intArrayOf(0b00000, 0b00000, 0b00000, 0b11111, 0b00000, 0b00000, 0b00000),
        'A' to intArrayOf(0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001),
        'B' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110),
        'C' to intArrayOf(0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110),
        'D' to intArrayOf(0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110),
        'E' to intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111),
        'F' to intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000),
        'G' to intArrayOf(0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01111),
        'H' to intArrayOf(0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001),
        'I' to intArrayOf(0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
        'J' to intArrayOf(0b00111, 0b00010, 0b00010, 0b00010, 0b00010, 0b10010, 0b01100),
        'K' to intArrayOf(0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001),
        'L' to intArrayOf(0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111),
        'M' to intArrayOf(0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001),
        'N' to intArrayOf(0b10001, 0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001),
        'O' to intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        'P' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000),
        'Q' to intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101),
        'R' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001),
        'S' to intArrayOf(0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110),
        'T' to intArrayOf(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100),
        'U' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        'V' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100),
        'W' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b10101, 0b01010),
        'X' to intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001),
        'Y' to intArrayOf(0b10001, 0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100),
        'Z' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111),
    )

    /** Width of [text] in dot columns (glyphs plus the gaps between them). */
    fun columns(text: CharSequence): Int =
        if (text.isEmpty()) 0 else text.length * GLYPH_COLS + (text.length - 1) * GAP_COLS

    /**
     * Draws [text] with its top-left corner at ([left], [top]). [pitch] is the distance between dot
     * centres, so the text is `columns(text) * pitch` wide and `GLYPH_ROWS * pitch` tall. Unlit dots
     * of every glyph are drawn with [off] when it is non-null (faint LED-matrix look).
     */
    fun draw(
        canvas: Canvas,
        text: CharSequence,
        left: Float,
        top: Float,
        pitch: Float,
        on: Paint,
        off: Paint? = null,
    ) {
        val radius = pitch * DOT_FILL / 2f
        var originCol = 0
        for (ch in text) {
            val glyph = glyphs[ch.uppercaseChar()] ?: blank
            for (row in 0 until GLYPH_ROWS) {
                val bits = glyph[row]
                val cy = top + (row + 0.5f) * pitch
                for (col in 0 until GLYPH_COLS) {
                    val cx = left + (originCol + col + 0.5f) * pitch
                    val lit = ((bits shr (GLYPH_COLS - 1 - col)) and 1) == 1
                    if (lit) {
                        canvas.drawCircle(cx, cy, radius, on)
                    } else if (off != null) {
                        canvas.drawCircle(cx, cy, radius, off)
                    }
                }
            }
            originCol += GLYPH_COLS + GAP_COLS
        }
    }
}
