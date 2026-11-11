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
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Dot Wave: two long sinusoidal swells cross a square lattice at different angles. Where a crest passes, dots grow,
 * brighten and sink slightly; troughs shrink and fade. One dot (the accent) rides the swell like all the others.
 */
object DotWave : DotWallpaper {
    override val id = "dot_wave"
    override val name = "Dot Wave"

    override val colorSlots = listOf(
        ColorSlot("bg", "Background"),
        ColorSlot("fg", "Dots"),
        ColorSlot("accent", "Accent dot"),
    )

    override val params = listOf(
        WallpaperParam("speed", "Wave speed", 0f, 3f, 1f),
        WallpaperParam("amplitude", "Amplitude", 0f, 0.95f, 0.7f),
        WallpaperParam("frequency", "Frequency", 0.4f, 4f, 1.6f),
        WallpaperParam("spacing", "Dot spacing", 10f, 36f, 15f),
        WallpaperParam("size", "Dot size", 0.3f, 0.95f, 0.72f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.45f),
    )

    override val presets = listOf(
        Palettes.midnight.preset(),
        Palettes.paper.preset(mapOf("frequency" to 1.3f, "size" to 0.68f)),
        Palettes.signal.preset(mapOf("amplitude" to 0.8f)),
        Palettes.mono.preset(mapOf("frequency" to 2.2f, "amplitude" to 0.6f)),
        Palettes.ember.preset(mapOf("speed" to 0.8f)),
        Palettes.frost.preset(mapOf("spacing" to 17f, "frequency" to 1.2f)),
    )

    private class Geo {
        var s = 16f
        var sx = 0f
        var sy = 0f
        var rMax = 5f
        var amp = 0.7f
        var k1 = 0.01f
        var k2 = 0.015f
        var ph1 = 0f
        var ph2 = 0f
        var ai = 0
        var aj = 0
    }

    private fun geo(frame: WallpaperFrame): Geo {
        val g = frame.cache.getOrPut("dot_wave_geo") { Geo() } as Geo
        val w = frame.width
        val h = frame.height
        val s = Dots.spacing(frame, frame.param("spacing"))
        val speed = frame.param("speed")
        g.s = s
        g.sx = Dots.parallaxX(frame)
        g.sy = Dots.parallaxY(frame)
        g.rMax = 0.5f * s * frame.param("size")
        g.amp = frame.param("amplitude")
        // Wave number: `frequency` swells across the short side of the screen.
        g.k1 = TAU * frame.param("frequency") / maxOf(1f, min(w, h))
        g.k2 = g.k1 * 1.55f
        g.ph1 = phase(frame.time, 0.07f * speed)
        g.ph2 = phase(frame.time, 0.045f * speed)
        g.ai = floor(0.5f * w / s).toInt()
        g.aj = floor(0.55f * h / s).toInt()
        return g
    }

    /** Swell height in -1..1 at a lattice position. Directions (0.342, 0.940) and (-0.800, 0.600) are mirrored in AGSL. */
    private fun swell(g: Geo, cx: Float, cy: Float): Float =
        0.65f * sin(g.k1 * (0.342f * cx + 0.940f * cy) - g.ph1) +
            0.35f * sin(g.k2 * (-0.800f * cx + 0.600f * cy) + g.ph2)

    override val shaderSource = Agsl.COMMON + """
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cFg;
        layout(color) uniform half4 cAcc;
        uniform float2 pShift;
        uniform float pS;
        uniform float pRMax;
        uniform float pAmp;
        uniform float pK1;
        uniform float pK2;
        uniform float pPh1;
        uniform float pPh2;
        uniform float2 pAccCell;
        uniform float pGrain;

        half4 main(float2 frag) {
            float2 wp = frag + pShift;
            float2 cell = floor(wp / pS);
            float2 d1 = float2(0.342, 0.940);
            float2 d2 = float2(-0.800, 0.600);
            float plain = 0.0;
            float accent = 0.0;
            // Dots sink and rise by up to 0.28 cells, so the neighbours above and below can overlap this pixel.
            for (int k = 0; k < 3; k++) {
                float2 cc = cell + float2(0.0, float(k) - 1.0);
                float2 c0 = (cc + 0.5) * pS;
                float wv = 0.65 * sin(pK1 * dot(d1, c0) - pPh1) + 0.35 * sin(pK2 * dot(d2, c0) + pPh2);
                float u = 0.5 - 0.5 * wv;
                float r = pRMax * (1.0 - pAmp * u);
                float a = 1.0 - 0.5 * pAmp * u;
                float2 c = c0 + float2(0.0, pAmp * 0.28 * pS * wv);
                float cov = dotCover(length(wp - c), r) * a;
                float isAcc = 1.0 - step(0.5, length(cc - pAccCell));
                accent = max(accent, cov * isAcc);
                plain = max(plain, cov * (1.0 - isAcc));
            }
            float3 col = mix(float3(cBg.rgb), float3(cFg.rgb), plain);
            col = mix(col, float3(cAcc.rgb), accent);
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
        shader.setFloatUniform("pAmp", g.amp)
        shader.setFloatUniform("pK1", g.k1)
        shader.setFloatUniform("pK2", g.k2)
        shader.setFloatUniform("pPh1", g.ph1)
        shader.setFloatUniform("pPh2", g.ph2)
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
        val j0 = floor(g.sy / s).toInt() - 1
        val j1 = floor((g.sy + size.height) / s).toInt() + 1
        for (j in j0..j1) {
            val c0y = (j + 0.5f) * s
            for (i in i0..i1) {
                val c0x = (i + 0.5f) * s
                val wv = swell(g, c0x, c0y)
                val u = 0.5f - 0.5f * wv
                val r = g.rMax * (1f - g.amp * u)
                val a = 1f - 0.5f * g.amp * u
                val y = c0y + g.amp * 0.28f * s * wv
                dot(if (i == g.ai && j == g.aj) acc else fg, c0x - g.sx, y - g.sy, r, a)
            }
        }
        drawGrain(frame, frame.param("grain"))
    }
}
