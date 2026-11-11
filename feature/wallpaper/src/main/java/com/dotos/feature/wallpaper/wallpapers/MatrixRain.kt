package com.dotos.feature.wallpaper.wallpapers

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dotos.feature.wallpaper.engine.ColorSlot
import com.dotos.feature.wallpaper.engine.DotWallpaper
import com.dotos.feature.wallpaper.engine.WallpaperFrame
import com.dotos.feature.wallpaper.engine.WallpaperParam
import com.dotos.feature.wallpaper.engine.setColor
import com.dotos.feature.wallpaper.wallpapers.Noise.drawGrain
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/**
 * Matrix Rain, minus the hacker green: sparse columns of dots fall at their own gentle pace, each with a long fading
 * tail of shrinking, dimming dots. White on black by default; the occasional column leads with a signal-red head.
 * A faint ghost lattice keeps the matrix readable where nothing is falling. Streams enter and leave off-screen, so
 * the loop never shows a seam.
 */
object MatrixRain : DotWallpaper {
    override val id = "matrix_rain"
    override val name = "Matrix Rain"

    override val colorSlots = listOf(
        ColorSlot("bg", "Background"),
        ColorSlot("fg", "Dots"),
        ColorSlot("accent", "Red heads"),
    )

    override val params = listOf(
        WallpaperParam("density", "Density", 0f, 1f, 0.55f),
        WallpaperParam("speed", "Fall speed", 0f, 3f, 1f),
        WallpaperParam("spacing", "Dot spacing", 10f, 32f, 16f),
        WallpaperParam("size", "Dot size", 0.3f, 0.95f, 0.66f),
        WallpaperParam("ghost", "Ghost dots", 0f, 1f, 0.35f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.45f),
    )

    override val presets = listOf(
        Palettes.midnight.preset(),
        Palettes.paper.preset(mapOf("density" to 0.5f, "ghost" to 0.25f)),
        Palettes.signal.preset(mapOf("density" to 0.45f)),
        Palettes.mono.preset(mapOf("speed" to 0.8f)),
        Palettes.ember.preset(mapOf("density" to 0.6f)),
        Palettes.frost.preset(mapOf("ghost" to 0.3f)),
    )

    private class Geo {
        var s = 16f
        var sx = 0f
        var sy = 0f
        var rMax = 5f
        var rows = 60f
        var density = 0.55f
        var speed = 1f
        var ghost = 0.35f
    }

    private fun geo(frame: WallpaperFrame): Geo {
        val g = frame.cache.getOrPut("matrix_rain_geo") { Geo() } as Geo
        val s = Dots.spacing(frame, frame.param("spacing"))
        g.s = s
        g.sx = Dots.parallaxX(frame)
        g.sy = Dots.parallaxY(frame)
        g.rMax = 0.5f * s * frame.param("size")
        g.rows = ceil(frame.height / s) + 2f
        g.density = frame.param("density")
        g.speed = frame.param("speed")
        g.ghost = frame.param("ghost")
        return g
    }

    override val shaderSource = Agsl.COMMON + """
        uniform float iTime;
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cFg;
        layout(color) uniform half4 cAcc;
        uniform float2 pShift;
        uniform float pS;
        uniform float pRMax;
        uniform float pRows;
        uniform float pDensity;
        uniform float pSpeed;
        uniform float pGhost;
        uniform float pGrain;

        half4 main(float2 frag) {
            float2 wp = frag + pShift;
            float2 cell = floor(wp / pS);
            float2 c = (cell + 0.5) * pS;
            float d = length(wp - c);

            // Per-column randomness.
            float ci = cell.x;
            float h1 = hash11(ci + 0.5);
            float h2 = hash11(ci + 19.5);
            float h3 = hash11(ci + 38.5);
            float h4 = hash11(ci + 57.5);
            float h5 = hash11(ci + 76.5);
            float hr = hash11(ci + 95.5);
            float isOn = step(h1, pDensity);
            float tail = 9.0 + 15.0 * h2;
            float rate = (0.7 + 1.9 * h3) * pSpeed;
            float period = (pRows + tail) * (1.2 + 1.3 * h4);
            float head = fract(iTime * rate / period + h5) * period - 2.0;

            // m = rows behind the head (negative = still ahead of it).
            float m = head - (cell.y + 0.5);
            float lead = smoothstep(-1.0, 0.0, m);
            float body = pow(max(1.0 - clamp(m / tail, 0.0, 1.0), 0.0001), 1.7);
            float inten = lead * body * isOn;
            float isRed = isOn * (1.0 - step(0.16, hr));
            float tint = isRed * (1.0 - smoothstep(0.0, 2.2, m)) * lead;

            float rS = pRMax * (0.32 + 0.68 * pow(max(inten, 0.0001), 0.55)) * smoothstep(0.0, 0.08, inten);
            float rG = pRMax * 0.2 * step(0.01, pGhost);
            float aG = 0.34 * pGhost;
            float r = max(rG, rS);
            float a = max(aG, inten);

            float3 dotCol = mix(float3(cFg.rgb), float3(cAcc.rgb), tint);
            float3 col = mix(float3(cBg.rgb), dotCol, dotCover(d, r) * a);
            col += float3(grainAt(frag) * pGrain * 0.075);
            return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
        }
    """

    @RequiresApi(33)
    override fun bindUniforms(shader: RuntimeShader, frame: WallpaperFrame) {
        val g = geo(frame)
        shader.setFloatUniform("iTime", frame.time)
        shader.setColor("cBg", frame.color("bg"))
        shader.setColor("cFg", frame.color("fg"))
        shader.setColor("cAcc", frame.color("accent"))
        shader.setFloatUniform("pShift", g.sx, g.sy)
        shader.setFloatUniform("pS", g.s)
        shader.setFloatUniform("pRMax", g.rMax)
        shader.setFloatUniform("pRows", g.rows)
        shader.setFloatUniform("pDensity", g.density)
        shader.setFloatUniform("pSpeed", g.speed)
        shader.setFloatUniform("pGhost", g.ghost)
        shader.setFloatUniform("pGrain", frame.param("grain"))
    }

    override fun DrawScope.drawFallback(frame: WallpaperFrame) {
        val g = geo(frame)
        val fg = frame.color("fg")
        val acc = frame.color("accent")
        drawRect(frame.color("bg"))
        val s = g.s
        val t = frame.time
        val i0 = floor(g.sx / s).toInt()
        val i1 = floor((g.sx + size.width) / s).toInt()
        val j0 = floor(g.sy / s).toInt()
        val j1 = floor((g.sy + size.height) / s).toInt()
        val ghostR = if (g.ghost >= 0.01f) g.rMax * 0.2f else 0f
        val ghostA = 0.34f * g.ghost
        for (i in i0..i1) {
            val ci = i.toFloat()
            val isOn = g.density >= hash11(ci + 0.5f)
            val tail = 9f + 15f * hash11(ci + 19.5f)
            val rate = (0.7f + 1.9f * hash11(ci + 38.5f)) * g.speed
            val period = (g.rows + tail) * (1.2f + 1.3f * hash11(ci + 57.5f))
            val head = fract(t * rate / period + hash11(ci + 76.5f)) * period - 2f
            val isRed = isOn && hash11(ci + 95.5f) < 0.16f
            val cx = (i + 0.5f) * s - g.sx
            for (j in j0..j1) {
                val cy = (j + 0.5f) * s - g.sy
                var r = ghostR
                var a = ghostA
                var color = fg
                val m = head - (j + 0.5f)
                if (isOn && m > -1f && m < tail) {
                    val lead = smoothstep(-1f, 0f, m)
                    val body = max(1f - (m / tail).coerceIn(0f, 1f), 0.0001f).pow(1.7f)
                    val inten = lead * body
                    val rS = g.rMax * (0.32f + 0.68f * max(inten, 0.0001f).pow(0.55f)) * smoothstep(0f, 0.08f, inten)
                    r = max(r, rS)
                    a = max(a, inten)
                    if (isRed) {
                        val tint = (1f - smoothstep(0f, 2.2f, m)) * lead
                        if (tint > 0.02f) color = mixColor(fg, acc, tint)
                    }
                }
                dot(color, cx, cy, r, a)
            }
        }
        drawGrain(frame, frame.param("grain"))
    }
}
