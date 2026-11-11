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
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Contour Map: a topographic map whose isolines are drawn in dots. A smooth three-octave value-noise terrain is sampled
 * at every lattice cell; the dot grows when the cell sits close to a contour level, with the distance measured in
 * screen pixels (via the analytic noise gradient) so every line keeps the same dotted width. Every fifth line is an
 * index contour, one level is picked out in the accent colour, and the terrain slowly drifts along closed paths so the
 * loop never shows a seam.
 */
object ContourMap : DotWallpaper {
    override val id = "contour_map"
    override val name = "Contour Map"

    override val colorSlots = listOf(
        ColorSlot("bg", "Background"),
        ColorSlot("fg", "Contours"),
        ColorSlot("accent", "Accent contour"),
    )

    override val params = listOf(
        WallpaperParam("lines", "Contour lines", 4f, 24f, 12f, step = 1f),
        WallpaperParam("scale", "Terrain scale", 0.5f, 2f, 1f),
        WallpaperParam("spacing", "Dot spacing", 9f, 28f, 13f),
        WallpaperParam("size", "Dot size", 0.3f, 0.95f, 0.78f),
        WallpaperParam("ghost", "Ghost dots", 0f, 1f, 0.3f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.45f),
    )

    override val presets = listOf(
        Palettes.midnight.preset(),
        Palettes.paper.preset(mapOf("lines" to 14f)),
        Palettes.signal.preset(mapOf("ghost" to 0.2f)),
        Palettes.mono.preset(mapOf("scale" to 1.3f)),
        Palettes.ember.preset(mapOf("lines" to 10f)),
        Palettes.frost.preset(mapOf("lines" to 14f, "spacing" to 12f)),
    )

    private class Geo {
        var s = 13f
        var sx = 0f
        var sy = 0f
        var rMax = 5f
        var scale = 300f
        var levels = 12f
        var accLevel = 6f
        var ghost = 0.3f
        var o1x = 0f
        var o1y = 0f
        var o2x = 0f
        var o2y = 0f
        var o3x = 0f
        var o3y = 0f
        val n1 = FloatArray(3)
        val n2 = FloatArray(3)
        val n3 = FloatArray(3)
    }

    private fun geo(frame: WallpaperFrame): Geo {
        val g = frame.cache.getOrPut("contour_map_geo") { Geo() } as Geo
        val t = frame.time
        val s = Dots.spacing(frame, frame.param("spacing"))
        g.s = s
        g.sx = Dots.parallaxX(frame)
        g.sy = Dots.parallaxY(frame)
        g.rMax = 0.5f * s * frame.param("size")
        g.scale = 300f * Dots.unit(frame) * frame.param("scale")
        g.levels = floor(frame.param("lines") + 0.5f)
        g.accLevel = floor(g.levels * 0.5f)
        g.ghost = frame.param("ghost")
        // Each octave drifts around its own closed loop (periods 420 s, 260 s, 180 s), so the terrain slowly morphs and
        // the animation repeats seamlessly. Constant offsets decorrelate the octaves.
        val a1 = phase(t, 1f / 420f)
        val a2 = phase(t, 1f / 260f)
        val a3 = phase(t, 1f / 180f)
        g.o1x = 1.2f * cos(a1)
        g.o1y = 1.2f * sin(a1)
        g.o2x = 17.1f + 1.6f * cos(-a2)
        g.o2y = 9.2f + 1.6f * sin(-a2)
        g.o3x = 31.7f + 2.2f * cos(a3)
        g.o3y = 23.5f + 2.2f * sin(a3)
        return g
    }

    /** Quintic value noise with analytic gradient: res = (value, d/dx, d/dy). Same maths as AGSL `vnoise`. */
    private fun vnoise(px: Float, py: Float, res: FloatArray) {
        val ix = floor(px)
        val iy = floor(py)
        val fx = px - ix
        val fy = py - iy
        val ux = fx * fx * fx * (fx * (fx * 6f - 15f) + 10f)
        val uy = fy * fy * fy * (fy * (fy * 6f - 15f) + 10f)
        val dux = 30f * fx * fx * (fx * (fx - 2f) + 1f)
        val duy = 30f * fy * fy * (fy * (fy - 2f) + 1f)
        val a = hash21(ix, iy)
        val b = hash21(ix + 1f, iy)
        val c = hash21(ix, iy + 1f)
        val d = hash21(ix + 1f, iy + 1f)
        val k1 = b - a
        val k2 = c - a
        val k4 = a - b - c + d
        res[0] = a + k1 * ux + k2 * uy + k4 * ux * uy
        res[1] = dux * (k1 + k4 * uy)
        res[2] = duy * (k2 + k4 * ux)
    }

    override val shaderSource = Agsl.COMMON + """
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cFg;
        layout(color) uniform half4 cAcc;
        uniform float2 pShift;
        uniform float pS;
        uniform float pRMax;
        uniform float pScale;
        uniform float pLevels;
        uniform float pAccLevel;
        uniform float pGhost;
        uniform float2 pO1;
        uniform float2 pO2;
        uniform float2 pO3;
        uniform float pGrain;

        // Quintic value noise: x = value, yz = gradient.
        float3 vnoise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
            float2 du = 30.0 * f * f * (f * (f - 2.0) + 1.0);
            float a = hash21(i);
            float b = hash21(i + float2(1.0, 0.0));
            float c = hash21(i + float2(0.0, 1.0));
            float d = hash21(i + float2(1.0, 1.0));
            float k1 = b - a;
            float k2 = c - a;
            float k4 = a - b - c + d;
            float v = a + k1 * u.x + k2 * u.y + k4 * u.x * u.y;
            float2 g = du * float2(k1 + k4 * u.y, k2 + k4 * u.x);
            return float3(v, g.x, g.y);
        }

        half4 main(float2 frag) {
            float2 wp = frag + pShift;
            float2 c = (floor(wp / pS) + 0.5) * pS;
            float d = length(wp - c);

            float r = pRMax * 0.18 * step(0.01, pGhost);
            float a = 0.30 * pGhost;
            float isAcc = 0.0;

            // The terrain is only needed where a dot can actually be.
            if (d < pRMax + 1.0) {
                float2 p = c / pScale;
                float3 n1 = vnoise(p + pO1);
                float3 n2 = vnoise(p * 2.03 + pO2);
                float3 n3 = vnoise(p * 4.11 + pO3);
                float terrain = 0.58 * n1.x + 0.28 * n2.x + 0.14 * n3.x;
                float2 grad = 0.58 * n1.yz + 0.5684 * n2.yz + 0.5754 * n3.yz;
                float height = (terrain - 0.5) * 1.9 + 0.5;
                float ph = height * pLevels;
                float nl = floor(ph + 0.5);
                // Distance in px from this cell centre to the nearest contour line.
                float dpx = abs(ph - nl) / pLevels * pScale / (1.9 * length(grad) + 0.02);
                float idx = mod(nl, 5.0) < 0.5 ? 1.0 : 0.0;
                float lineAmt = 1.0 - smoothstep(0.12 * pS, mix(0.62, 0.95, idx) * pS, dpx);
                r = max(r, pRMax * lineAmt * mix(0.85, 1.0, idx));
                a = max(a, lineAmt * mix(0.75, 1.0, idx));
                isAcc = abs(nl - pAccLevel) < 0.5 ? 1.0 : 0.0;
            }

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
        shader.setFloatUniform("pScale", g.scale)
        shader.setFloatUniform("pLevels", g.levels)
        shader.setFloatUniform("pAccLevel", g.accLevel)
        shader.setFloatUniform("pGhost", g.ghost)
        shader.setFloatUniform("pO1", g.o1x, g.o1y)
        shader.setFloatUniform("pO2", g.o2x, g.o2y)
        shader.setFloatUniform("pO3", g.o3x, g.o3y)
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
        val ghostR = if (g.ghost >= 0.01f) g.rMax * 0.18f else 0f
        val ghostA = 0.30f * g.ghost
        val invScale = 1f / g.scale
        for (j in j0..j1) {
            val cy = (j + 0.5f) * s
            val py = cy * invScale
            for (i in i0..i1) {
                val cx = (i + 0.5f) * s
                val px = cx * invScale
                vnoise(px + g.o1x, py + g.o1y, g.n1)
                vnoise(px * 2.03f + g.o2x, py * 2.03f + g.o2y, g.n2)
                vnoise(px * 4.11f + g.o3x, py * 4.11f + g.o3y, g.n3)
                val v = 0.58f * g.n1[0] + 0.28f * g.n2[0] + 0.14f * g.n3[0]
                val gx = 0.58f * g.n1[1] + 0.5684f * g.n2[1] + 0.5754f * g.n3[1]
                val gy = 0.58f * g.n1[2] + 0.5684f * g.n2[2] + 0.5754f * g.n3[2]
                val height = (v - 0.5f) * 1.9f + 0.5f
                val ph = height * g.levels
                val nl = floor(ph + 0.5f)
                val dpx = abs(ph - nl) / g.levels * g.scale / (1.9f * sqrt(gx * gx + gy * gy) + 0.02f)
                val md = nl - 5f * floor(nl / 5f)
                val idx = if (md < 0.5f) 1f else 0f
                val lineAmt = 1f - smoothstep(0.12f * s, (0.62f + 0.33f * idx) * s, dpx)
                val r = max(ghostR, g.rMax * lineAmt * (0.85f + 0.15f * idx))
                val a = max(ghostA, lineAmt * (0.75f + 0.25f * idx))
                dot(if (abs(nl - g.accLevel) < 0.5f) acc else fg, cx - g.sx, cy - g.sy, r, a)
            }
        }
        drawGrain(frame, frame.param("grain"))
    }
}
