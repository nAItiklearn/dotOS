package com.dotos.feature.wallpaper.wallpapers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dotos.feature.wallpaper.engine.WallpaperFrame
import com.dotos.feature.wallpaper.engine.WallpaperPreset
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
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
        drawRect(brush, alpha = (amount * 0.07f).coerceIn(0f, 0.12f))
    }

    /** Deterministic hash in 0..1 for jitter. */
    fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777215f
    }
}

// ---------------------------------------------------------------------------------------------
// Scalar helpers shared by the dot wallpapers. Every function here has an AGSL twin in [Agsl.COMMON]
// (or in the wallpaper's own shader) so the shader path and the Canvas fallback agree.
// ---------------------------------------------------------------------------------------------

internal const val TAU = 6.2831855f

internal fun fract(x: Float): Float = x - floor(x)

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Sine-free hash, 1D. Same arithmetic as AGSL `hash11` so both paths pick the same random numbers. */
internal fun hash11(x: Float): Float {
    var p = fract(x * 0.1031f)
    p *= p + 33.33f
    p *= p + p
    return fract(p)
}

/** Sine-free hash, 2D. Same arithmetic as AGSL `hash21`. */
internal fun hash21(x: Float, y: Float): Float {
    var a = fract(x * 0.1031f)
    var b = fract(y * 0.1031f)
    var c = a
    val d = a * (b + 33.33f) + b * (c + 33.33f) + c * (a + 33.33f)
    a += d
    b += d
    c += d
    return fract((a + b) * c)
}

/**
 * Phase in radians (0..TAU) of a motion that repeats [cyclesPerSecond] times per second. Computed in
 * double precision and wrapped, so it stays accurate and has no seam however long the launcher runs.
 */
internal fun phase(time: Float, cyclesPerSecond: Float): Float {
    val x = time.toDouble() * cyclesPerSecond.toDouble()
    return (TAU * (x - floor(x))).toFloat()
}

/** sRGB mix of two opaque colours, allocation free. Channels are clamped because `Color` rejects values outside 0..1. */
internal fun mixColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        (a.red + (b.red - a.red) * k).coerceIn(0f, 1f),
        (a.green + (b.green - a.green) * k).coerceIn(0f, 1f),
        (a.blue + (b.blue - a.blue) * k).coerceIn(0f, 1f),
        1f,
    )
}

/** Lattice helpers. Sizes are expressed in "reference dp" (a 393 dp wide phone) and scale with the short screen side. */
internal object Dots {
    private const val MAX_DOTS = 4800f

    /**
     * Pixels per reference dp. Derived from the short side of the canvas instead of the display density, so the
     * 120 px wide live thumbnails in the picker draw the same picture as the full screen, just smaller.
     */
    fun unit(frame: WallpaperFrame): Float = max(1f, min(frame.width, frame.height)) / 393f

    /** Lattice pitch in px for a spacing given in reference dp, never so tight that the dot count explodes. */
    fun spacing(frame: WallpaperFrame, dp: Float): Float {
        val byCount = sqrt(max(1f, frame.width * frame.height) / MAX_DOTS)
        return max(max(2f, dp * unit(frame)), byCount)
    }

    /** Horizontal parallax of the whole pattern in px (page scroll plus gyro). */
    fun parallaxX(frame: WallpaperFrame): Float = (frame.scroll * 0.06f + frame.tiltX * 0.012f) * frame.width

    fun parallaxY(frame: WallpaperFrame): Float = frame.tiltY * 0.012f * frame.height
}

/**
 * One antialiased dot. Dots under 0.75 px fade out instead of popping, mirroring AGSL `dotCover`.
 * [amount] is the opacity (0..1) of [color] over the background.
 */
internal fun DrawScope.dot(color: Color, x: Float, y: Float, r: Float, amount: Float) {
    if (r < 0.25f || amount < 0.02f) return
    val fade = if (r < 0.75f) r / 0.75f else 1f
    drawCircle(color = color, radius = r, center = Offset(x, y), alpha = (amount * fade).coerceAtMost(1f))
}

/** AGSL building blocks. Declares no uniforms, so each wallpaper keeps full control of its own uniform set. */
internal object Agsl {
    const val COMMON = """
        float hash11(float p) {
            p = fract(p * 0.1031);
            p *= p + 33.33;
            p *= p + p;
            return fract(p);
        }

        float hash21(float2 p) {
            float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        // Static film grain in -0.5..0.5. Pixel locked, so it never shimmers; also dithers away banding.
        float grainAt(float2 frag) {
            return hash21(floor(frag)) - 0.5;
        }

        // Antialiased disc of radius r at distance d (px). Sub-pixel discs fade out instead of vanishing.
        float dotCover(float d, float r) {
            float c = 1.0 - smoothstep(r - 0.75, r + 0.75, d);
            return c * clamp(r / 0.75, 0.0, 1.0);
        }
    """
}

/** A named colour set shared by every dot wallpaper, so presets feel like one family (and match Atmosphere). */
internal class Pal(val id: String, val name: String, val bg: Long, val fg: Long, val accent: Long)

internal object Palettes {
    val midnight = Pal("midnight", "Midnight", 0xFF050506, 0xFFF2F1EC, 0xFFD71921)
    val paper = Pal("paper", "Paper", 0xFFEDEAE4, 0xFF1C1B19, 0xFFD71921)
    val signal = Pal("signal", "Signal Red", 0xFF0A0A0A, 0xFFD71921, 0xFFF2F1EC)
    val mono = Pal("mono", "Mono", 0xFF151515, 0xFF9C9C99, 0xFFFFFFFF)
    val ember = Pal("ember", "Ember", 0xFF140804, 0xFFEE7B3C, 0xFFFFEBD6)
    val frost = Pal("frost", "Frost", 0xFFC4D1DB, 0xFF2F4A61, 0xFFD71921)
}

internal fun Pal.preset(params: Map<String, Float> = emptyMap()): WallpaperPreset = WallpaperPreset(
    id = id,
    name = name,
    colors = mapOf("bg" to Color(bg), "fg" to Color(fg), "accent" to Color(accent)),
    params = params,
)
