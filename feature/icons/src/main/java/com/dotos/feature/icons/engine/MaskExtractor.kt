package com.dotos.feature.icons.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns arbitrary app icons into a normalised single-colour alpha mask (white + alpha, [SIZE]²).
 * All functions are pure and safe to call off the main thread.
 */
internal object MaskExtractor {
    const val SIZE = 96

    /** Fraction of the mask canvas the glyph's longest side is normalised to. */
    private const val TARGET = 0.70f

    /** Android 13+ themed-icon layer. Best quality source for brand apps. */
    @RequiresApi(33)
    fun fromMonochrome(icon: Drawable): Bitmap? {
        val adaptive = icon as? AdaptiveIconDrawable ?: return null
        val mono = adaptive.monochrome ?: return null
        val raw = drawLayer(mono)
        return normalize(raw)
    }

    /** Fallback: separate the logo from its background by alpha and colour distance. */
    fun fromForeground(icon: Drawable): Bitmap? {
        val adaptive = icon as? AdaptiveIconDrawable
        val raw: Bitmap
        val bgColor: Int
        if (adaptive != null && adaptive.foreground != null) {
            raw = drawLayer(adaptive.foreground!!)
            bgColor = adaptive.background?.let { averageColor(drawLayer(it)) } ?: Color.TRANSPARENT
        } else {
            raw = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val c = Canvas(raw)
            icon.setBounds(0, 0, SIZE, SIZE)
            icon.draw(c)
            bgColor = edgeColor(raw)
        }
        val px = IntArray(SIZE * SIZE)
        raw.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val hasBg = Color.alpha(bgColor) > 40
        var lit = 0
        for (i in px.indices) {
            val p = px[i]
            val a = Color.alpha(p)
            var m = a / 255f
            if (hasBg && a > 0) {
                val d = colorDistance(p, bgColor)
                m *= smooth(0.10f, 0.28f, d)
            }
            val alpha = (m * 255).roundToInt().coerceIn(0, 255)
            if (alpha > 30) lit++
            px[i] = (alpha shl 24) or 0x00FFFFFF
        }
        // Logo indistinguishable from background (e.g. full-bleed photo icons): use luminance contrast.
        if (lit < SIZE * SIZE * 0.02f || lit > SIZE * SIZE * 0.85f) return luminanceMask(raw)
        raw.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        return normalize(raw)
    }

    private fun luminanceMask(src: Bitmap): Bitmap? {
        val px = IntArray(SIZE * SIZE)
        src.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        var sum = 0f
        for (p in px) sum += lum(p)
        val mean = sum / px.size
        for (i in px.indices) {
            val p = px[i]
            val contrast = kotlin.math.abs(lum(p) - mean)
            val alpha = (smooth(0.12f, 0.35f, contrast) * Color.alpha(p)).roundToInt().coerceIn(0, 255)
            px[i] = (alpha shl 24) or 0x00FFFFFF
        }
        src.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        return normalize(src)
    }

    /** Draws an adaptive layer so its 72/108 visible window fills [SIZE]. */
    private fun drawLayer(layer: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val full = (SIZE * 108f / 72f).roundToInt()
        val off = (full - SIZE) / 2
        layer.setBounds(-off, -off, full - off, full - off)
        layer.draw(c)
        return bmp
    }

    /** Crops to content bounds and rescales so every glyph has the same optical size. */
    fun normalize(src: Bitmap): Bitmap? {
        val px = IntArray(SIZE * SIZE)
        src.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        var minX = SIZE
        var minY = SIZE
        var maxX = -1
        var maxY = -1
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if ((px[y * SIZE + x] ushr 24) > 40) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null
        val w = (maxX - minX + 1).toFloat()
        val h = (maxY - minY + 1).toFloat()
        val scale = TARGET * SIZE / max(w, h)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        c.translate(SIZE / 2f, SIZE / 2f)
        c.scale(scale, scale)
        c.translate(-(minX + w / 2f), -(minY + h / 2f))
        c.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun averageColor(b: Bitmap): Int {
        var r = 0L
        var g = 0L
        var bl = 0L
        var n = 0L
        val step = 6
        for (y in 0 until b.height step step) for (x in 0 until b.width step step) {
            val p = b.getPixel(x, y)
            if (Color.alpha(p) > 128) {
                r += Color.red(p)
                g += Color.green(p)
                bl += Color.blue(p)
                n++
            }
        }
        if (n == 0L) return Color.TRANSPARENT
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    private fun edgeColor(b: Bitmap): Int {
        val inset = (SIZE * 0.08f).roundToInt()
        val samples = intArrayOf(
            b.getPixel(inset, SIZE / 2), b.getPixel(SIZE - 1 - inset, SIZE / 2),
            b.getPixel(SIZE / 2, inset), b.getPixel(SIZE / 2, SIZE - 1 - inset),
        )
        val opaque = samples.filter { Color.alpha(it) > 200 }
        if (opaque.size < 3) return Color.TRANSPARENT
        return Color.rgb(opaque.map { Color.red(it) }.average().toInt(), opaque.map { Color.green(it) }.average().toInt(), opaque.map { Color.blue(it) }.average().toInt())
    }

    private fun colorDistance(a: Int, b: Int): Float {
        val dr = (Color.red(a) - Color.red(b)) / 255f
        val dg = (Color.green(a) - Color.green(b)) / 255f
        val db = (Color.blue(a) - Color.blue(b)) / 255f
        return sqrt((dr * dr + dg * dg + db * db) / 3f)
    }

    private fun lum(p: Int) = (0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)) / 255f

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    internal fun clamp01(v: Float) = min(1f, max(0f, v))
}
