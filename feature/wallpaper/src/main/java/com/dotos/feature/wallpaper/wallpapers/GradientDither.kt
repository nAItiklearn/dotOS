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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Gradient Dither: a smooth two-tone gradient quantised onto a dot lattice with ordered dithering (Bayer 4x4, Bayer
 * 8x8 or a blue-noise-like interleaved-gradient pattern). Each lattice cell grows from a faint pin-prick to a full
 * dot as the gradient passes its threshold, so the dither breathes smoothly as the gradient slowly drifts and
 * undulates. One accent dot sits in the dense end.
 */
object GradientDither : DotWallpaper {
    override val id = "gradient_dither"
    override val name = "Gradient Dither"

    override val colorSlots = listOf(
        ColorSlot("bg", "Background"),
        ColorSlot("fg", "Dots"),
        ColorSlot("accent", "Accent dot"),
    )

    override val params = listOf(
        WallpaperParam("angle", "Gradient angle", 0f, 360f, 235f),
        WallpaperParam("spacing", "Dot spacing", 7f, 24f, 10f),
        WallpaperParam("size", "Dot size", 0.4f, 1f, 0.84f),
        WallpaperParam("pattern", "Pattern: Bayer 4, Bayer 8, Noise", 0f, 2f, 1f, step = 1f),
        WallpaperParam("balance", "Balance", -0.3f, 0.3f, 0f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.45f),
    )

    override val presets = listOf(
        Palettes.midnight.preset(),
        Palettes.paper.preset(mapOf("pattern" to 0f, "spacing" to 11f)),
        Palettes.signal.preset(mapOf("angle" to 200f)),
        Palettes.mono.preset(mapOf("pattern" to 2f)),
        Palettes.ember.preset(mapOf("angle" to 260f, "balance" to 0.05f)),
        Palettes.frost.preset(mapOf("angle" to 215f, "pattern" to 0f, "spacing" to 11f)),
    )

    private class Geo {
        var s = 10f
        var sx = 0f
        var sy = 0f
        var rMax = 5f
        var dirX = 0f
        var dirY = 1f
        var extent = 1f
        var midX = 0f
        var midY = 0f
        var bias = 0f
        var phW = 0f
        var phD = 0f
        var pattern = 1
        var ai = 0
        var aj = 0
    }

    private fun geo(frame: WallpaperFrame): Geo {
        val g = frame.cache.getOrPut("gradient_dither_geo") { Geo() } as Geo
        val w = frame.width
        val h = frame.height
        val s = Dots.spacing(frame, frame.param("spacing"))
        g.s = s
        g.sx = Dots.parallaxX(frame)
        g.sy = Dots.parallaxY(frame)
        g.rMax = 0.5f * s * frame.param("size")
        // The angle points toward the dense end of the gradient (screen coordinates, y down).
        val a = frame.param("angle") * (TAU / 360f)
        g.dirX = cos(a)
        g.dirY = sin(a)
        g.extent = maxOf(1f, abs(w * g.dirX) + abs(h * g.dirY))
        g.midX = 0.5f * w
        g.midY = 0.5f * h
        g.bias = frame.param("balance")
        g.phW = phase(frame.time, 0.02f)
        g.phD = phase(frame.time, 0.013f)
        g.pattern = (frame.param("pattern") + 0.5f).toInt().coerceIn(0, 2)
        // Accent dot: 80% of the way along the gradient, on the centre line.
        val ax = g.midX + g.dirX * 0.30f * g.extent
        val ay = g.midY + g.dirY * 0.30f * g.extent
        g.ai = floor(ax / s).toInt()
        g.aj = floor(ay / s).toInt()
        return g
    }

    private fun bayer2(x: Float, y: Float): Float {
        val fx = floor(x)
        val fy = floor(y)
        return fract(fx * 0.5f + fy * fy * 0.75f)
    }

    private fun bayer4(x: Float, y: Float): Float = bayer2(0.5f * x, 0.5f * y) * 0.25f + bayer2(x, y)

    private fun bayer8(x: Float, y: Float): Float = bayer4(0.5f * x, 0.5f * y) * 0.25f + bayer2(x, y)

    private fun ign(x: Float, y: Float): Float = fract(52.9829189f * fract(x * 0.06711056f + y * 0.00583715f))

    private fun threshold(pattern: Int, i: Int, j: Int): Float = when (pattern) {
        0 -> bayer4((i and 3).toFloat(), (j and 3).toFloat())
        1 -> bayer8((i and 7).toFloat(), (j and 7).toFloat())
        else -> ign(i.toFloat(), j.toFloat())
    }

    /** Gradient value 0..1 at a world position. Mirrors the AGSL. */
    private fun level(g: Geo, cx: Float, cy: Float): Float {
        val qx = cx - g.midX
        val qy = cy - g.midY
        var t = (qx * g.dirX + qy * g.dirY) / g.extent + 0.5f + g.bias
        val tp = (-qx * g.dirY + qy * g.dirX) / g.extent
        t += 0.045f * sin(TAU * 1.25f * tp + g.phW) + 0.03f * sin(g.phD)
        val v = t.coerceIn(0f, 1f)
        return v * v * (3f - 2f * v)
    }

    override val shaderSource = Agsl.COMMON + """
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cFg;
        layout(color) uniform half4 cAcc;
        uniform float2 pShift;
        uniform float pS;
        uniform float pRMax;
        uniform float2 pDir;
        uniform float pExtent;
        uniform float2 pMid;
        uniform float pBias;
        uniform float pPhW;
        uniform float pPhD;
        uniform float pPattern;
        uniform float2 pAccCell;
        uniform float pGrain;

        float bayer2(float2 a) {
            a = floor(a);
            return fract(a.x / 2.0 + a.y * a.y * 0.75);
        }

        float bayer4(float2 a) {
            return bayer2(0.5 * a) * 0.25 + bayer2(a);
        }

        float bayer8(float2 a) {
            return bayer4(0.5 * a) * 0.25 + bayer2(a);
        }

        float ign(float2 a) {
            return fract(52.9829189 * fract(dot(a, float2(0.06711056, 0.00583715))));
        }

        half4 main(float2 frag) {
            float2 wp = frag + pShift;
            float2 cell = floor(wp / pS);
            float2 c = (cell + 0.5) * pS;
            float d = length(wp - c);

            float2 q = c - pMid;
            float t = dot(q, pDir) / pExtent + 0.5 + pBias;
            float tp = dot(q, float2(-pDir.y, pDir.x)) / pExtent;
            t += 0.045 * sin(6.2831853 * 1.25 * tp + pPhW) + 0.03 * sin(pPhD);
            float v = clamp(t, 0.0, 1.0);
            v = v * v * (3.0 - 2.0 * v);

            float thr = ign(cell);
            if (pPattern < 0.5) {
                thr = bayer4(mod(cell, float2(4.0, 4.0)));
            } else if (pPattern < 1.5) {
                thr = bayer8(mod(cell, float2(8.0, 8.0)));
            }
            float wS = 0.035;
            float lit = smoothstep(thr - wS, thr + wS, v * (1.0 + 2.0 * wS) - wS);

            float isAcc = 1.0 - step(0.5, length(cell - pAccCell));
            float r = mix(pRMax * (0.16 + 0.84 * lit), pRMax, isAcc);
            float a = mix(0.20 + 0.80 * lit, 1.0, isAcc);
            float3 dotCol = mix(float3(cFg.rgb), float3(cAcc.rgb), isAcc);
            float3 col = mix(float3(cBg.rgb), dotCol, dotCover(d, r) * a);
            col += float3(grainAt(frag) * pGrain * 0.075);
            return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
        }
    """

    @RequiresApi(33)
    override fun bindUniforms(shader: RuntimeShader, frame: WallpaperFrame) {
        val g = geo(frame)
        shader.setColor("cBg", frame.color("bg"))
        shader.setColor("cFg", frame.color("fg"))
        shader.setColor("cAcc", frame.color("accent"))
        shader.setFloatUniform("pShift", g.sx, g.sy)
        shader.setFloatUniform("pS", g.s)
        shader.setFloatUniform("pRMax", g.rMax)
        shader.setFloatUniform("pDir", g.dirX, g.dirY)
        shader.setFloatUniform("pExtent", g.extent)
        shader.setFloatUniform("pMid", g.midX, g.midY)
        shader.setFloatUniform("pBias", g.bias)
        shader.setFloatUniform("pPhW", g.phW)
        shader.setFloatUniform("pPhD", g.phD)
        shader.setFloatUniform("pPattern", g.pattern.toFloat())
        shader.setFloatUniform("pAccCell", g.ai.toFloat(), g.aj.toFloat())
        shader.setFloatUniform("pGrain", frame.param("grain"))
    }

    override fun DrawScope.drawFallback(frame: WallpaperFrame) {
        val g = geo(frame)
        val fg = frame.color("fg")
        val acc = frame.color("accent")
        drawRect(frame.color("bg"))
        val s = g.s
        val i0 = floor(g.sx / s).toInt()
        val i1 = floor((g.sx + size.width) / s).toInt()
        val j0 = floor(g.sy / s).toInt()
        val j1 = floor((g.sy + size.height) / s).toInt()
        val wS = 0.035f
        for (j in j0..j1) {
            val cy = (j + 0.5f) * s
            for (i in i0..i1) {
                val cx = (i + 0.5f) * s
                if (i == g.ai && j == g.aj) {
                    dot(acc, cx - g.sx, cy - g.sy, g.rMax, 1f)
                } else {
                    val v = level(g, cx, cy)
                    val thr = threshold(g.pattern, i, j)
                    val lit = smoothstep(thr - wS, thr + wS, v * (1f + 2f * wS) - wS)
                    dot(fg, cx - g.sx, cy - g.sy, g.rMax * (0.16f + 0.84f * lit), 0.20f + 0.80f * lit)
                }
            }
        }
        drawGrain(frame, frame.param("grain"))
    }
}
