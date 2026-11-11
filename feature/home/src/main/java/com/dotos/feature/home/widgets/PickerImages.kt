package com.dotos.feature.home.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest edge of a decoded widget preview. Cards are ~175 dp, so this is plenty and keeps memory low. */
private const val PREVIEW_IMAGE_MAX_PX = 360

/**
 * Loads the provider's preview image (falling back to its icon) as a bitmap capped in size.
 * Blocking: call from a background dispatcher. Never throws.
 */
internal fun loadWidgetPreview(context: Context, info: AppWidgetProviderInfo, densityDpi: Int): ImageBitmap? {
    val preview = runCatching { info.loadPreviewImage(context, densityDpi)?.toImageBitmap(PREVIEW_IMAGE_MAX_PX) }
        .getOrNull()
    if (preview != null) return preview
    return runCatching { info.loadIcon(context, densityDpi)?.toImageBitmap(PREVIEW_IMAGE_MAX_PX) }.getOrNull()
}

/**
 * Loads the icon of the app that ships [info] (badged for work-profile providers) at [sizePx].
 * Blocking: call from a background dispatcher. Never throws.
 */
internal fun loadAppIcon(context: Context, info: AppWidgetProviderInfo, sizePx: Int): ImageBitmap? {
    val pm = context.packageManager
    val raw = runCatching { pm.getApplicationInfo(info.provider.packageName, 0).loadIcon(pm) }.getOrNull() ?: return null
    val badged = runCatching { pm.getUserBadgedIcon(raw, info.profile) }.getOrNull() ?: raw
    return runCatching { badged.toImageBitmap(sizePx) }.getOrNull()
}

/**
 * Rasterises a drawable into a software bitmap no larger than [maxPx] on its longest edge, keeping its
 * aspect ratio. Hardware-backed bitmap drawables are copied first, because drawing them onto a software
 * canvas throws.
 */
private fun Drawable.toImageBitmap(maxPx: Int): ImageBitmap? {
    var w = intrinsicWidth
    var h = intrinsicHeight
    if (w <= 0 || h <= 0) {
        w = maxPx
        h = maxPx
    }
    val longest = max(w, h)
    if (longest > maxPx) {
        val factor = maxPx.toFloat() / longest
        w = (w * factor).roundToInt().coerceAtLeast(1)
        h = (h * factor).roundToInt().coerceAtLeast(1)
    }

    if (this is BitmapDrawable) {
        val source = bitmap
        if (source != null && source.config == Bitmap.Config.HARDWARE) {
            val software = source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            return Bitmap.createScaledBitmap(software, w, h, true).asImageBitmap()
        }
    }

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val savedBounds = copyBounds()
    setBounds(0, 0, w, h)
    try {
        draw(Canvas(out))
    } finally {
        bounds = savedBounds
    }
    return out.asImageBitmap()
}
