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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pulse: soft rings of swelling dots travel outward across a quiet lattice. Without a touch they emanate from the
 * screen centre at a pace set by the battery (slow when full, quicker as it drains, flowing inward while charging).
 * Touch the wallpaper and the origin glides to the last touch point and a burst of three echoing rings rolls out
 * from it. The dot at the origin glows in the accent colour.
 *
 * The ring phase is integrated frame by frame (kept in the frame cache), so changes in the battery level or charge
 * state change the speed smoothly instead of making the rings jump.
 */
object Pulse : DotWallpaper {
    override val id = "pulse"
    override val name = "Pulse"

    override val colorSlots = listOf(
        ColorSlot("bg", "Background"),
        ColorSlot("fg", "Dots"),
        ColorSlot("accent", "Origin dot"),
    )

    override val params = listOf(
        WallpaperParam("spacing", "Dot spacing", 10f, 34f, 15f),
        WallpaperParam("size", "Dot size", 0.3f, 0.95f, 0.72f),
        WallpaperParam("ring", "Ring spacing", 50f, 220f, 120f),
        WallpaperParam("rate", "Pulse rate", 0f, 2.5f, 1f),
        WallpaperParam("touch", "Touch response", 0f, 1.5f, 1f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.45f),
    )

    override val presets = listOf(
        Palettes.midnight.preset(),
        Palettes.paper.preset(mapOf("ring" to 140f)),
        Palettes.signal.preset(mapOf("rate" to 0.8f)),
        Palettes.mono.preset(mapOf("ring" to 100f)),
        Palettes.ember.preset(mapOf("rate" to 0.9f)),
        Palettes.frost.preset(mapOf("ring" to 150f, "spacing" to 17f)),
    )

    private class Geo {
        var s = 16f
        var sx = 0f
        var sy = 0f
        var rMax = 5f
        var lambda = 100f
        var env = 100f

        /** Eased origin of the ambient rings, in screen px. */
        var ox = 0f
        var oy = 0f

        /** Last touch point in screen px (far away when there was none). */
        var tx = -10000f
        var ty = -10000f
        var age = 1000f
        var touchAmt = 0f
        var fade = 0f

        /** Accumulated ring phase in turns, kept in 0..1. */
        var cycles = 0f
        var lastTime = 0f
        var inited = false
    }

    /** Advances the pulse state by one frame. Called exactly once per frame by whichever render path is active. */
    private fun geo(frame: WallpaperFrame): Geo {
        val g = frame.cache.getOrPut("pulse_geo") { Geo() } as Geo
        val w = frame.width
        val h = frame.height
        val s = Dots.spacing(frame, frame.param("spacing"))
        g.s = s
        g.sx = Dots.parallaxX(frame)
        g.sy = Dots.parallaxY(frame)
        g.rMax = 0.5f * s * frame.param("size")
        g.lambda = max(8f, frame.param("ring") * Dots.unit(frame))
        g.env = max(1f, 0.55f * max(w, h))

        val hasTouch = frame.touchX >= 0f && frame.touchY >= 0f
        val targetX = if (hasTouch) frame.touchX else 0.5f * w
        val targetY = if (hasTouch) frame.touchY else 0.42f * h
        val dt = frame.time - g.lastTime
        val step = if (g.inited && dt >= 0f && dt <= 0.5f) dt else 0f
        if (!g.inited) {
            g.ox = targetX
            g.oy = targetY
            g.inited = true
        } else {
            val k = 1f - exp(-step / 0.45f)
            g.ox += (targetX - g.ox) * k
            g.oy += (targetY - g.oy) * k
        }
        g.lastTime = frame.time

        val level = frame.battery.coerceIn(0f, 1f)
        val dir = if (frame.charging) -1f else 1f
        val rate = (0.08f + 0.16f * (1f - level)) * frame.param("rate") * dir
        g.cycles = fract(g.cycles + rate * step)

        g.tx = if (hasTouch) frame.touchX else -10000f
        g.ty = if (hasTouch) frame.touchY else -10000f
        g.age = frame.touchAge
        g.touchAmt = if (hasTouch) frame.param("touch") else 0f
        g.fade = exp(-g.age / 2.4f) * smoothstep(0f, 0.1f, g.age)
        return g
    }

    override val shaderSource = Agsl.COMMON + """
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cFg;
        layout(color) uniform half4 cAcc;
        uniform float2 pShift;
        uniform float pS;
        uniform float pRMax;
        uniform float2 pOrigin;
        uniform float2 pTouch;
        uniform float pLambda;
        uniform float pCycles;
        uniform float pAge;
        uniform float pTouchAmt;
        uniform float pEnv;
        uniform float pGrain;

        half4 main(float2 frag) {
            float2 wp = frag + pShift;
            float2 c = (floor(wp / pS) + 0.5) * pS;
            float d = length(wp - c);

            // Screen position of this dot (the pattern is anchored to the screen, the lattice slides with parallax).
            float2 sc = c - pShift;
            float2 od = sc - pOrigin;
            float dO = length(od);

            // Continuous train of rings.
            float ring = 0.5 + 0.5 * cos(6.2831853 * (dO / pLambda - pCycles));
            ring = ring * ring * ring;
            float env = 0.30 + 0.70 * exp(-(dO * dO) / (pEnv * pEnv));
            float inten = ring * env;

            // Touch burst: three echoes rolling out from the touch point, fading over a few seconds.
            float dT = length(sc - pTouch);
            float fade = exp(-pAge / 2.4) * smoothstep(0.0, 0.1, pAge);
            float burst = 0.0;
            for (int e = 0; e < 3; e++) {
                float te = pAge - float(e) * 0.45;
                float q = (dT - pLambda * 0.9 * te) / (0.32 * pLambda);
                burst += exp(-q * q) * (1.0 - 0.28 * float(e)) * step(0.0, te);
            }
            inten = max(inten, burst * fade * pTouchAmt);

            float r = pRMax * (0.20 + 0.80 * inten);
            float a = 0.18 + 0.82 * inten;

            // The dot nearest the origin glows in the accent colour (smooth, so it never hops between cells).
            float glow = exp(-dot(od, od) / (0.56 * pS * pS));
            r = max(r, pRMax * 0.95 * glow);
            a = max(a, glow);

            float3 dotCol = mix(float3(cFg.rgb), float3(cAcc.rgb), glow);
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
        shader.setFloatUniform("pOrigin", g.ox, g.oy)
        shader.setFloatUniform("pTouch", g.tx, g.ty)
        shader.setFloatUniform("pLambda", g.lambda)
        shader.setFloatUniform("pCycles", g.cycles)
        shader.setFloatUniform("pAge", g.age)
        shader.setFloatUniform("pTouchAmt", g.touchAmt)
        shader.setFloatUniform("pEnv", g.env)
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
        val burstOn = g.touchAmt > 0f && g.fade > 0.002f
        val invEnv2 = 1f / (g.env * g.env)
        val glowK = 1f / (0.56f * s * s)
        val burstW = 1f / (0.32f * g.lambda)
        for (j in j0..j1) {
            val cy = (j + 0.5f) * s
            val scy = cy - g.sy
            val ody = scy - g.oy
            for (i in i0..i1) {
                val scx = (i + 0.5f) * s - g.sx
                val odx = scx - g.ox
                val d2 = odx * odx + ody * ody
                val dO = sqrt(d2)
                var ring = 0.5f + 0.5f * cos(TAU * (dO / g.lambda - g.cycles))
                ring = ring * ring * ring
                var inten = ring * (0.30f + 0.70f * exp(-d2 * invEnv2))
                if (burstOn) {
                    val tdx = scx - g.tx
                    val tdy = scy - g.ty
                    val dT = sqrt(tdx * tdx + tdy * tdy)
                    var burst = 0f
                    for (e in 0..2) {
                        val te = g.age - e * 0.45f
                        if (te >= 0f) {
                            val q = (dT - g.lambda * 0.9f * te) * burstW
                            burst += exp(-q * q) * (1f - 0.28f * e)
                        }
                    }
                    inten = max(inten, burst * g.fade * g.touchAmt)
                }
                var r = g.rMax * (0.20f + 0.80f * inten)
                var a = 0.18f + 0.82f * inten
                val glow = exp(-d2 * glowK)
                r = max(r, g.rMax * 0.95f * glow)
                a = max(a, glow)
                dot(if (glow > 0.02f) mixColor(fg, acc, glow) else fg, scx, scy, r, a)
            }
        }
        drawGrain(frame, frame.param("grain"))
    }
}
