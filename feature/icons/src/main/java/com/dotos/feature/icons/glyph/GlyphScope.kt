package com.dotos.feature.icons.glyph

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Tiny drawing DSL for bundled DotOS glyphs.
 *
 * Coordinates are on a 24 x 24 grid (like Material symbols). Keep artwork inside 2..22 and
 * optically centred; the live area for a "full" glyph is roughly 3..21. Default stroke is 2 grid
 * units with round caps/joins, which gives the uniform monoline look. Glyphs are rendered into an
 * alpha mask and then styled (line, dot-matrix, tinted), so draw in a single colour only and never
 * rely on colour or transparency for meaning.
 */
class GlyphScope internal constructor(private val canvas: Canvas) {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
        color = 0xFFFFFFFF.toInt()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    }
    private val rect = RectF()

    /** Stroke width in grid units. */
    var strokeWidth: Float
        get() = strokePaint.strokeWidth
        set(value) {
            strokePaint.strokeWidth = value
        }

    /** Butt caps for crisp geometric ends (e.g. bars); default is round. */
    var roundCaps: Boolean = true
        set(value) {
            field = value
            strokePaint.strokeCap = if (value) Paint.Cap.ROUND else Paint.Cap.BUTT
        }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, y1, x2, y2, strokePaint)

    fun polyline(vararg xy: Float, closed: Boolean = false) {
        require(xy.size >= 4 && xy.size % 2 == 0) { "polyline needs x,y pairs" }
        val p = Path()
        p.moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) {
            p.lineTo(xy[i], xy[i + 1])
            i += 2
        }
        if (closed) p.close()
        canvas.drawPath(p, strokePaint)
    }

    fun circle(cx: Float, cy: Float, r: Float, filled: Boolean = false) =
        canvas.drawCircle(cx, cy, r, if (filled) fillPaint else strokePaint)

    /** Filled dot; the basic unit of dot-matrix glyphs. */
    fun dot(cx: Float, cy: Float, r: Float = 1.2f) = canvas.drawCircle(cx, cy, r, fillPaint)

    fun rect(left: Float, top: Float, right: Float, bottom: Float, radius: Float = 0f, filled: Boolean = false) {
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, radius, radius, if (filled) fillPaint else strokePaint)
    }

    /** Arc on the circle (cx, cy, r). Angles in degrees, 0 = 3 o'clock, clockwise. */
    fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, filled: Boolean = false) {
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, startDeg, sweepDeg, filled, if (filled) fillPaint else strokePaint)
    }

    /** Arbitrary path in grid units. */
    fun path(filled: Boolean = false, build: Path.() -> Unit) {
        val p = Path().apply(build)
        canvas.drawPath(p, if (filled) fillPaint else strokePaint)
    }

    /** Punch a hole (e.g. negative space inside a filled shape). */
    fun clearCircle(cx: Float, cy: Float, r: Float) = canvas.drawCircle(cx, cy, r, clearPaint)

    fun clearRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float = 0f) {
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, radius, radius, clearPaint)
    }

    /** Draw a run of dots along a grid row: helper for dot-matrix lettering. */
    fun dotRow(y: Float, vararg xs: Float, r: Float = 1.2f) = xs.forEach { dot(it, y, r) }

    /** Rotate subsequent drawing in [block] around (px, py). */
    fun rotated(degrees: Float, px: Float = 12f, py: Float = 12f, block: GlyphScope.() -> Unit) {
        canvas.save()
        canvas.rotate(degrees, px, py)
        block()
        canvas.restore()
    }
}

/** A bundled glyph. [id] is referenced from assets/icon_map.json. */
class Glyph(val id: String, val draw: GlyphScope.() -> Unit)

/** Renders [glyph] into [canvas] scaled so the 24-unit grid fills [sizePx]. */
fun renderGlyph(glyph: Glyph, canvas: Canvas, sizePx: Float) {
    canvas.save()
    canvas.scale(sizePx / 24f, sizePx / 24f)
    GlyphScope(canvas).apply(glyph.draw)
    canvas.restore()
}
