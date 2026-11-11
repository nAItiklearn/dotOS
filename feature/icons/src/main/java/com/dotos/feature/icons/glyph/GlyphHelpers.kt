package com.dotos.feature.icons.glyph

import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

// Small shared building blocks for the bundled glyph sets. Everything here is a pure extension of
// the public GlyphScope / Path API so the drawing DSL itself stays untouched.

/** Fills [build] and then strokes the same path, giving a solid shape with the usual rounded 2-unit edge. */
internal fun GlyphScope.fillStroke(build: Path.() -> Unit) {
    path(filled = true, build = build)
    path(filled = false, build = build)
}

/**
 * Wide cloud outline spanning x 4..20. The stroke centre line runs from [top] to [top] + [h] (default 11.2);
 * used by the weather, cloud storage and backup glyphs so every cloud shares one silhouette.
 */
internal fun Path.cloud(top: Float, h: Float = 11.2f) {
    cloudAt(4f, top, 16f, h)
}

/** The same cloud silhouette fitted into a box whose stroke centre line starts at ([left], [top]) and is [w] x [h]. */
internal fun Path.cloudAt(left: Float, top: Float, w: Float, h: Float) {
    val kx = w / 16f
    val ky = h / 11.2f
    moveTo(left + 4f * kx, top + 11.2f * ky)
    cubicTo(left + 1.8f * kx, top + 11.2f * ky, left, top + 9.5f * ky, left, top + 7.5f * ky)
    cubicTo(left, top + 5.7f * ky, left + 1.3f * kx, top + 4.3f * ky, left + 3f * kx, top + 4f * ky)
    cubicTo(left + 3.4f * kx, top + 1.7f * ky, left + 5.4f * kx, top, left + 7.8f * kx, top)
    cubicTo(left + 10.3f * kx, top, left + 12.3f * kx, top + 1.6f * ky, left + 12.8f * kx, top + 3.9f * ky)
    cubicTo(left + 14.6f * kx, top + 4.2f * ky, left + 16f * kx, top + 5.7f * ky, left + 16f * kx, top + 7.5f * ky)
    cubicTo(left + 16f * kx, top + 9.5f * ky, left + 14.3f * kx, top + 11.2f * ky, left + 12.3f * kx, top + 11.2f * ky)
    close()
}

/** Closed heart outline centred on ([cx], [cy]) with half-width [s]. */
internal fun Path.heart(cx: Float, cy: Float, s: Float) {
    moveTo(cx, cy + s * 0.95f)
    cubicTo(cx - s * 0.5f, cy + s * 0.55f, cx - s, cy + s * 0.1f, cx - s, cy - s * 0.35f)
    cubicTo(cx - s, cy - s * 0.85f, cx - s * 0.65f, cy - s * 1.05f, cx - s * 0.35f, cy - s * 1.05f)
    cubicTo(cx - s * 0.15f, cy - s * 1.05f, cx, cy - s * 0.9f, cx, cy - s * 0.7f)
    cubicTo(cx, cy - s * 0.9f, cx + s * 0.15f, cy - s * 1.05f, cx + s * 0.35f, cy - s * 1.05f)
    cubicTo(cx + s * 0.65f, cy - s * 1.05f, cx + s, cy - s * 0.85f, cx + s, cy - s * 0.35f)
    cubicTo(cx + s, cy + s * 0.1f, cx + s * 0.5f, cy + s * 0.55f, cx, cy + s * 0.95f)
    close()
}

/**
 * Four-point sparkle with concave sides: tips at distance [r] from ([cx], [cy]), waist at distance [waist]
 * on the diagonals.
 */
internal fun Path.sparkle(cx: Float, cy: Float, r: Float, waist: Float) {
    moveTo(cx, cy - r)
    quadTo(cx + waist, cy - waist, cx + r, cy)
    quadTo(cx + waist, cy + waist, cx, cy + r)
    quadTo(cx - waist, cy + waist, cx - r, cy)
    quadTo(cx - waist, cy - waist, cx, cy - r)
    close()
}

/** Closed gear outline with [teeth] teeth, tooth tips at [rTip] and valleys at [rRoot]. */
internal fun Path.gear(cx: Float, cy: Float, rTip: Float, rRoot: Float, teeth: Int, tipHalf: Float, rootHalf: Float) {
    val step = 360f / teeth
    val k = 0.017453292f
    moveTo(cx + rRoot * cos((-90f - rootHalf) * k), cy + rRoot * sin((-90f - rootHalf) * k))
    for (i in 0 until teeth) {
        val a = i * step - 90f
        lineTo(cx + rRoot * cos((a - rootHalf) * k), cy + rRoot * sin((a - rootHalf) * k))
        lineTo(cx + rTip * cos((a - tipHalf) * k), cy + rTip * sin((a - tipHalf) * k))
        lineTo(cx + rTip * cos((a + tipHalf) * k), cy + rTip * sin((a + tipHalf) * k))
        lineTo(cx + rRoot * cos((a + rootHalf) * k), cy + rRoot * sin((a + rootHalf) * k))
    }
    close()
}
