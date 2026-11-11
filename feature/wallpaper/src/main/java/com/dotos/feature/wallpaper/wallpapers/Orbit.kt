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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orbit: concentric rings of dots around a quiet centre. Every ring turns at its own slow speed and direction (the
 * linear speed is roughly constant, so inner rings spin faster than outer ones, like a dial). Every sixth ring is
 * a touch larger and brighter, brightness falls away from the centre, and one signal-red dot travels on one ring.
 */
object Orbit : DotWallpaper {
    override val id = "orbit"
    override val name = "Orbit"

    override val colorSlots = listOf(
        ColorSlot("bg", "Background"),
        ColorSlot("fg", "Dots"),
        ColorSlot("accent", "Accent dot"),
    )

    override val params = listOf(
        WallpaperParam("spacing", "Ring spacing", 10f, 36f, 17f),
        WallpaperParam("size", "Dot size", 0.3f, 0.95f, 0.62f),
        WallpaperParam("speed", "Rotation speed", 0f, 3f, 1f),
        WallpaperParam("fade", "Fade distance", 0.3f, 1.6f, 0.85f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.45f),
    )

    override val presets = listOf(
        Palettes.midnight.preset(),
        Palettes.paper.preset(mapOf("size" to 0.58f)),
        Palettes.signal.preset(mapOf("fade" to 0.7f)),
        Palettes.mono.preset(mapOf("fade" to 1.1f)),
        Palettes.ember.preset(mapOf("speed" to 0.8f)),
        Palettes.frost.preset(mapOf("spacing" to 19f)),
    )

    private class Geo {
        var s = 16f
        var sx = 0f
        var sy = 0f
        var rMax = 5f
        var accR = 5f
        var cx = 0f
        var cy = 0f
        var unit = 1f
        var speed = 1f
        var fadeR = 100f
        var accRing = 5
        var kMax = 0
        var key = Int.MIN_VALUE

        /** Per ring (index = ring number): signed rotation in turns per second, dot count, dot radius and opacity. */
        var cyc = FloatArray(0)
        var cnt = IntArray(0)
        var rr = FloatArray(0)
        var aa = FloatArray(0)
    }

    private fun geo(frame: WallpaperFrame): Geo {
        val g = frame.cache.getOrPut("orbit_geo") { Geo() } as Geo
        val w = frame.width
        val h = frame.height
        g.sx = Dots.parallaxX(frame)
        g.sy = Dots.parallaxY(frame)
        val key = frame.layoutKey()
        if (g.key == key && g.cyc.isNotEmpty()) return g
        g.key = key

        val unit = Dots.unit(frame)
        val spacingDp = frame.param("spacing")
        val s = Dots.spacing(frame, spacingDp)
        g.s = s
        g.unit = unit
        g.speed = frame.param("speed")
        g.rMax = 0.5f * s * frame.param("size")
        g.accR = min(g.rMax * 1.12f, 0.49f * s)
        g.cx = 0.5f * w
        g.cy = 0.42f * h
        g.fadeR = max(1f, frame.param("fade") * 0.5f * max(w, h))
        // Accent ring sits roughly 105 reference dp from the centre whatever the spacing.
        g.accRing = (105f / max(1f, spacingDp)).roundToInt().coerceIn(3, 12)

        // Farthest corner from the centre, plus slack for parallax.
        val fx = max(g.cx, w - g.cx) + 0.08f * w
        val fy = max(g.cy, h - g.cy) + 0.08f * h
        val kMax = ceil(sqrt(fx * fx + fy * fy) / s).toInt() + 2
        g.kMax = kMax
        if (g.cyc.size != kMax + 1) {
            g.cyc = FloatArray(kMax + 1)
            g.cnt = IntArray(kMax + 1)
            g.rr = FloatArray(kMax + 1)
            g.aa = FloatArray(kMax + 1)
        }
        for (k in 1..kMax) {
            val kf = k.toFloat()
            val vel = (3f + 8f * hash11(kf + 0.5f)) * unit * g.speed
            val dirS = if (hash11(kf + 41.5f) > 0.5f) 1f else -1f
            g.cyc[k] = dirS * vel / (TAU * kf * s)
            g.cnt[k] = floor(TAU * kf + 0.5f).toInt()
            val q = (kf * s) / g.fadeR
            val fade = exp(-q * q)
            val emph = if (k % 6 == 0) 1f else 0.8f
            g.rr[k] = g.rMax * (0.45f + 0.55f * fade) * emph
            g.aa[k] = (0.22f + 0.78f * fade) * emph
        }
        return g
    }

    override val shaderSource = Agsl.COMMON + """
        uniform float iTime;
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cFg;
        layout(color) uniform half4 cAcc;
        uniform float2 pShift;
        uniform float2 pCenter;
        uniform float pS;
        uniform float pRMax;
        uniform float pAccR;
        uniform float pUnit;
        uniform float pSpeed;
        uniform float pFadeR;
        uniform float pAccRing;
        uniform float pGrain;

        half4 main(float2 frag) {
            float2 v = frag + pShift - pCenter;
            float rho = length(v);
            float k = floor(rho / pS + 0.5);
            float d = rho;
            float r = pRMax;
            float a = 1.0;
            float isAcc = 0.0;
            if (k > 0.5) {
                float n = floor(6.2831853 * k + 0.5);
                float stepA = 6.2831853 / n;
                float vel = (3.0 + 8.0 * hash11(k + 0.5)) * pUnit * pSpeed;
                float dirS = hash11(k + 41.5) > 0.5 ? 1.0 : -1.0;
                float cyc = dirS * vel / (6.2831853 * k * pS);
                float ph = 6.2831853 * fract(iTime * cyc);
                float rel = mod(atan(v.y, v.x) - ph, 6.2831853);
                float j = floor(rel / stepA + 0.5);
                float ang = j * stepA + ph;
                d = length(v - (k * pS) * float2(cos(ang), sin(ang)));
                float q = (k * pS) / pFadeR;
                float fade = exp(-q * q);
                float emph = mod(k, 6.0) < 0.5 ? 1.0 : 0.8;
                r = pRMax * (0.45 + 0.55 * fade) * emph;
                a = (0.22 + 0.78 * fade) * emph;
                isAcc = (abs(k - pAccRing) < 0.5 && mod(j, n) < 0.5) ? 1.0 : 0.0;
            }
            r = mix(r, pAccR, isAcc);
            a = mix(a, 1.0, isAcc);
            float3 dotCol = mix(float3(cFg.rgb), float3(cAcc.rgb), isAcc);
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
        shader.setFloatUniform("pCenter", g.cx, g.cy)
        shader.setFloatUniform("pS", g.s)
        shader.setFloatUniform("pRMax", g.rMax)
        shader.setFloatUniform("pAccR", g.accR)
        shader.setFloatUniform("pUnit", g.unit)
        shader.setFloatUniform("pSpeed", g.speed)
        shader.setFloatUniform("pFadeR", g.fadeR)
        shader.setFloatUniform("pAccRing", g.accRing.toFloat())
        shader.setFloatUniform("pGrain", frame.param("grain"))
    }

    override fun DrawScope.drawFallback(frame: WallpaperFrame) {
        val g = geo(frame)
        val fg = frame.color("fg")
        val acc = frame.color("accent")
        drawRect(frame.color("bg"))
        val w = size.width
        val h = size.height
        val s = g.s
        val ccx = g.cx - g.sx
        val ccy = g.cy - g.sy
        val t = frame.time
        dot(fg, ccx, ccy, g.rMax, 1f)
        for (k in 1..g.kMax) {
            val n = g.cnt[k]
            val ringR = k * s
            val ph = TAU * fract(t * g.cyc[k])
            val stepA = TAU / n
            val r = g.rr[k]
            val a = g.aa[k]
            for (j in 0 until n) {
                val ang = j * stepA + ph
                val x = ccx + ringR * cos(ang)
                val y = ccy + ringR * sin(ang)
                if (x < -s || x > w + s || y < -s || y > h + s) continue
                if (k == g.accRing && j == 0) dot(acc, x, y, g.accR, 1f) else dot(fg, x, y, r, a)
            }
        }
        drawGrain(frame, frame.param("grain"))
    }
}
