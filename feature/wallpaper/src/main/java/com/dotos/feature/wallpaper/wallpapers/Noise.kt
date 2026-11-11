package com.dotos.feature.wallpaper.wallpapers

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dotos.feature.wallpaper.engine.WallpaperFrame
import kotlin.random.Random

/** Shared Canvas-fallback helpers. */
internal object Noise {
    /** 128x128 grey film-grain tile (static, so it never shimmers). */
    fun grainTile(): ImageBitmap {
        val n = 128
        val px = IntArray(n * n)
        val rnd = Random(7)
        for (i in px.indices) {
            val v = rnd.nextInt(256)
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Overlays film grain using the tile cached in [frame]. */
    fun DrawScope.drawGrain(frame: WallpaperFrame, amount: Float) {
        if (amount <= 0.001f) return
        val brush = frame.cache.getOrPut("grain_brush") {
            ShaderBrush(ImageShader(grainTile(), TileMode.Repeated, TileMode.Repeated))
        } as ShaderBrush
        drawRect(brush, alpha = (amount * 0.10f).coerceIn(0f, 0.2f), blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
    }

    /** Deterministic hash in 0..1 for jitter. */
    fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777215f
    }
}
