package com.dotos.feature.icons.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.toArgb
import com.dotos.core.data.settings.DotShape
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Final styling: mask → line or dot-matrix glyph bitmap of [px]². Transparent background. */
internal object IconRenderer {
    /** Mask canvas mapped onto this fraction of the plate (mask content is ~70% → glyph ≈ 52% of plate). */
    private const val GLYPH_BOX = 0.74f

    fun line(mask: Bitmap, spec: IconSpec, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val box = px * GLYPH_BOX * spec.glyphScale
        val left = (px - box) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(spec.glyphColor.toArgb(), PorterDuff.Mode.SRC_IN)
        }
        c.drawBitmap(mask, null, RectF(left, left, left + box, left + box), paint)
        return out
    }

    /**
     * Dot-matrix: the glyph box is divided into an n×n grid; each cell's mask coverage (box-filtered)
     * sets the dot radius. Weak coverage fades out smoothly so outlines stay clean, not noisy.
     */
    fun dots(mask: Bitmap, spec: IconSpec, px: Int): Bitmap {
        val n = spec.dotDensity.coerceIn(10, 28)
        val size = MaskExtractor.SIZE
        val alpha = IntArray(size * size)
        mask.getPixels(alpha, 0, size, 0, 0, size, size)
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val box = px * GLYPH_BOX * spec.glyphScale
        val origin = (px - box) / 2f
        val pitch = box / n
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.glyphColor.toArgb()
            style = if (spec.dotShape == DotShape.RING) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = pitch * 0.18f
        }
        val cell = size.toFloat() / n
        for (gy in 0 until n) for (gx in 0 until n) {
            val x0 = (gx * cell).toInt()
            val y0 = (gy * cell).toInt()
            val x1 = ((gx + 1) * cell).toInt().coerceAtMost(size)
            val y1 = ((gy + 1) * cell).toInt().coerceAtMost(size)
            var sum = 0
            var count = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                sum += alpha[y * size + x] ushr 24
                count++
            }
            if (count == 0) continue
            val coverage = sum / (255f * count)
            if (coverage < 0.14f) continue
            val t = ((coverage - 0.14f) / 0.46f).coerceIn(0f, 1f)
            val f = sqrt(t * t * (3 - 2 * t))
            val r = pitch * 0.42f * (0.55f + 0.45f * f)
            val cx = origin + (gx + 0.5f) * pitch
            val cy = origin + (gy + 0.5f) * pitch
            when (spec.dotShape) {
                DotShape.SQUARE -> c.drawRect(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f, paint)
                DotShape.RING -> c.drawCircle(cx, cy, r * 0.8f, paint)
                DotShape.ROUND -> c.drawCircle(cx, cy, r, paint)
            }
        }
        return out
    }

    /** Original artwork (full adaptive icon with system mask), filling the plate area. */
    fun original(icon: Drawable, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val inset = (px * 0.02f).roundToInt()
        icon.setBounds(inset, inset, px - inset, px - inset)
        icon.draw(c)
        return out
    }
}
