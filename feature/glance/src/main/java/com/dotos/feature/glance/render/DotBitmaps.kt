package com.dotos.feature.glance.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Software renderers for the Glance widgets. Everything is laid out in dp on a [Canvas] that is scaled to
 * pixels once, so the same geometry works at any density. Bitmaps are tiny (a few hundred circles) and
 * are drawn at most once per widget update.
 */
internal object DotBitmaps {
    private const val ON = 0xFFFFFFFF.toInt()
    private const val ON_SOFT = 0xE6FFFFFF.toInt()
    private const val OFF = 0x2EFFFFFF.toInt()
    private const val MUTED = 0x8CFFFFFF.toInt()
    private const val ACCENT = 0xFFD71921.toInt()

    /** Below this height (dp) the battery widget uses its single-row layout. */
    private const val COMPACT_MAX_HEIGHT_DP = 72f

    /** At or below this level (and not charging) the battery dots turn signal red. */
    private const val LOW_LEVEL = 15

    private const val BAR_DOT_FILL = 0.74f
    private const val YEAR_DOT_FILL = 0.7f

    /**
     * Battery widget art for a [widthDp] x [heightDp] layout: dot-matrix percentage, a dot bar and a
     * caption. Charging shows a signal-red dot; a low level turns the lit dots red.
     */
    fun battery(
        widthDp: Float,
        heightDp: Float,
        scale: Float,
        level: Int,
        charging: Boolean,
        caption: String,
    ): Bitmap {
        val bitmap = newBitmap(widthDp, heightDp, scale)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)

        val safeLevel = level.coerceIn(0, 100)
        val low = !charging && safeLevel <= LOW_LEVEL
        val on = paintOf(if (low) ACCENT else ON)
        val off = paintOf(OFF)
        val text = "$safeLevel%"
        val fraction = safeLevel / 100f

        if (heightDp < COMPACT_MAX_HEIGHT_DP) {
            drawBatteryCompact(canvas, widthDp, heightDp, text, fraction, charging, on, off)
        } else {
            drawBatteryRegular(canvas, widthDp, heightDp, text, caption, fraction, charging, on, off)
        }
        return bitmap
    }

    private fun drawBatteryCompact(
        canvas: Canvas,
        w: Float,
        h: Float,
        text: String,
        fraction: Float,
        charging: Boolean,
        on: Paint,
        off: Paint,
    ) {
        val padX = 12f
        val padY = 8f
        val innerW = w - 2 * padX
        val innerH = h - 2 * padY

        val textCols = DotFont.columns(text)
        val textPitch = min(min(innerH / DotFont.GLYPH_ROWS, 3.4f), innerW * 0.5f / textCols)
        val textW = textCols * textPitch
        val textH = DotFont.GLYPH_ROWS * textPitch
        DotFont.draw(canvas, text, padX, padY + (innerH - textH) / 2f, textPitch, on, off)

        val barCols = if (innerW > 160f) 10 else 5
        val barRows = 2
        val barAreaW = innerW - textW - 10f
        val barPitch = min(min(barAreaW / barCols, innerH / barRows), 9f).coerceAtLeast(1f)
        val barW = barCols * barPitch
        val barH = barRows * barPitch
        drawDotBar(canvas, padX + innerW - barW, padY + (innerH - barH) / 2f, barCols, barRows, barPitch, fraction, on, off)

        if (charging) canvas.drawCircle(w - 7f, 7f, 2.2f, paintOf(ACCENT))
    }

    private fun drawBatteryRegular(
        canvas: Canvas,
        w: Float,
        h: Float,
        text: String,
        caption: String,
        fraction: Float,
        charging: Boolean,
        on: Paint,
        off: Paint,
    ) {
        val pad = 14f
        val innerW = w - 2 * pad
        val innerH = h - 2 * pad

        // Caption, top-left, muted.
        val capCols = DotFont.columns(caption).coerceAtLeast(1)
        val capPitch = min(1.7f, innerW / capCols)
        val capH = DotFont.GLYPH_ROWS * capPitch
        DotFont.draw(canvas, caption, pad, pad, capPitch, paintOf(MUTED))
        if (charging) canvas.drawCircle(pad + innerW - 3f, pad + capH / 2f, 2.6f, paintOf(ACCENT))

        // Bar, bottom, one row.
        val barCols = if (innerW > 150f) 20 else 10
        val barPitch = min(innerW / barCols, 10f)
        val barY = pad + innerH - barPitch
        drawDotBar(canvas, pad, barY, barCols, 1, barPitch, fraction, on, off)

        // Percentage fills the space in between.
        val areaTop = pad + capH + 6f
        val areaH = (barY - 8f) - areaTop
        val textCols = DotFont.columns(text)
        val textPitch = min(min(innerW / textCols, areaH / DotFont.GLYPH_ROWS), 6.5f).coerceAtLeast(1f)
        val textH = DotFont.GLYPH_ROWS * textPitch
        DotFont.draw(canvas, text, pad, areaTop + (areaH - textH) / 2f, textPitch, on, off)
    }

    /**
     * Year progress art: header ("32% OF 2026" when wide enough, else "32%") above a dot grid with one dot
     * per day. Elapsed days are lit, today is signal red, the rest are dim.
     */
    fun yearProgress(
        widthDp: Float,
        heightDp: Float,
        scale: Float,
        dayOfYear: Int,
        totalDays: Int,
        ofLabel: String,
    ): Bitmap {
        val bitmap = newBitmap(widthDp, heightDp, scale)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)

        val total = totalDays.coerceAtLeast(1)
        val day = dayOfYear.coerceIn(1, total)
        val percent = day * 100 / total
        val pad = if (min(widthDp, heightDp) < 120f) 12f else 14f
        val innerW = widthDp - 2 * pad
        val innerH = heightDp - 2 * pad

        // Header.
        val percentText = "$percent%"
        val wide = innerW >= 140f
        val percentCols = DotFont.columns(percentText)
        val labelCols = if (wide) DotFont.columns(ofLabel) else 0
        val headerCols = if (wide) percentCols + 3 + labelCols else percentCols
        val headerPitch = min(3f, innerW / headerCols)
        val headerH = DotFont.GLYPH_ROWS * headerPitch
        DotFont.draw(canvas, percentText, pad, pad, headerPitch, paintOf(ON))
        if (wide) {
            DotFont.draw(canvas, ofLabel, pad + (percentCols + 3) * headerPitch, pad, headerPitch, paintOf(MUTED))
        }

        // Grid: pick the column count that maximises the dot pitch for the remaining box.
        val gridTop = pad + headerH + 10f
        val gridH = pad + innerH - gridTop
        if (gridH < 8f) return bitmap
        var bestCols = 1
        var bestPitch = 0f
        for (cols in 1..total) {
            val rows = (total + cols - 1) / cols
            val pitch = min(innerW / cols, gridH / rows)
            if (pitch > bestPitch) {
                bestPitch = pitch
                bestCols = cols
            }
        }
        val rows = (total + bestCols - 1) / bestCols
        val gridW = bestCols * bestPitch
        val left = pad + (innerW - gridW) / 2f
        val top = gridTop + (gridH - rows * bestPitch) / 2f
        val radius = bestPitch * YEAR_DOT_FILL / 2f

        val past = paintOf(ON_SOFT)
        val today = paintOf(ACCENT)
        val future = paintOf(OFF)
        for (i in 0 until total) {
            val cx = left + (i % bestCols + 0.5f) * bestPitch
            val cy = top + (i / bestCols + 0.5f) * bestPitch
            val paint = when {
                i < day - 1 -> past
                i == day - 1 -> today
                else -> future
            }
            canvas.drawCircle(cx, cy, radius, paint)
        }
        return bitmap
    }

    /**
     * Column-major dot bar: fills left to right, each column bottom to top, with a fractional last dot
     * drawn at partial alpha.
     */
    private fun drawDotBar(
        canvas: Canvas,
        left: Float,
        top: Float,
        cols: Int,
        rows: Int,
        pitch: Float,
        fraction: Float,
        on: Paint,
        off: Paint,
    ) {
        val total = cols * rows
        val lit = fraction.coerceIn(0f, 1f) * total
        val radius = pitch * BAR_DOT_FILL / 2f
        val partial = Paint(on)
        val baseAlpha = on.alpha
        for (i in 0 until total) {
            val col = i / rows
            val rowFromBottom = i % rows
            val cx = left + (col + 0.5f) * pitch
            val cy = top + (rows - 1 - rowFromBottom + 0.5f) * pitch
            val coverage = (lit - i).coerceIn(0f, 1f)
            when {
                coverage >= 1f -> canvas.drawCircle(cx, cy, radius, on)
                coverage > 0f -> {
                    canvas.drawCircle(cx, cy, radius, off)
                    partial.alpha = (baseAlpha * coverage).roundToInt()
                    canvas.drawCircle(cx, cy, radius, partial)
                }
                else -> canvas.drawCircle(cx, cy, radius, off)
            }
        }
    }

    private fun newBitmap(widthDp: Float, heightDp: Float, scale: Float): Bitmap = Bitmap.createBitmap(
        (widthDp * scale).roundToInt().coerceAtLeast(1),
        (heightDp * scale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )

    private fun paintOf(argb: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = argb
    }
}
