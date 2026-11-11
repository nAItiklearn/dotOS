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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Glyph Burst: a radial starfield made only of dots, streaming slowly outward from a quiet centre. Space is cut into a
 * log-polar grid (rings that grow exponentially, equal angular sectors), which makes every cell roughly square, so a
 * star drifting outward through the grid grows in exact perspective: small and dim far away, large and bright up
 * close. A few stars are signal red. Each star lives entirely inside its own cell, so one cell lookup per pixel is
 * enough, and the pattern never repeats or seams.
 */
object GlyphBurst : DotWallpaper {
    override val id = "glyph_burst"
    override val name = "Glyph Burst"

    override val colorSlots = listOf(
        ColorSlot("bg", "Background"),
        ColorSlot("fg", "Stars"),
        ColorSlot("accent", "Red stars"),
    )

    override val params = listOf(
        WallpaperParam("density", "Density", 0.1f, 1f, 0.5f),
        WallpaperParam("speed", "Speed", 0f, 3f, 1f),
        WallpaperParam("size", "Star size", 0.3f, 1f, 0.75f),
        WallpaperParam("detail", "Detail", 32f, 96f, 56f, step = 8f),
        WallpaperParam("hole", "Clear centre", 0f, 1f, 0.2f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.45f),
    )

    override val presets = listOf(
        Palettes.midnight.preset(),
        Palettes.paper.preset(mapOf("density" to 0.42f)),
        Palettes.signal.preset(mapOf("size" to 0.7f)),
        Palettes.mono.preset(mapOf("density" to 0.6f)),
        Palettes.ember.preset(mapOf("speed" to 0.8f)),
        Palettes.frost.preset(mapOf("density" to 0.45f, "detail" to 48f)),
    )

    private class Geo {
        var sx = 0f
        var sy = 0f
        var cx = 0f
        var cy = 0f
        var n = 56f
        var nHalf = 28
        var du = 0.112f
        var sp = 0.28f
        var density = 0.5f
        var sizeP = 0.75f
        var hole = 40f
        var fadeW = 40f
        var ref = 400f
    }

    private fun geo(frame: WallpaperFrame): Geo {
        val g = frame.cache.getOrPut("glyph_burst_geo") { Geo() } as Geo
        val w = frame.width
        val h = frame.height
        val minDim = max(1f, min(w, h))
        g.sx = Dots.parallaxX(frame)
        g.sy = Dots.parallaxY(frame)
        g.cx = 0.5f * w
        g.cy = 0.44f * h
        // Even sector count, so the +-pi seam falls on a cell border.
        g.nHalf = max(8, (frame.param("detail") + 0.5f).toInt() / 2)
        g.n = (g.nHalf * 2).toFloat()
        g.du = TAU / g.n
        g.sp = 0.28f * frame.param("speed")
        g.density = frame.param("density")
        g.sizeP = frame.param("size")
        g.hole = frame.param("hole") * 0.30f * minDim
        g.fadeW = 0.12f * minDim
        g.ref = max(1f, 0.55f * max(w, h))
        return g
    }

    override val shaderSource = Agsl.COMMON + """
        uniform float iTime;
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cFg;
        layout(color) uniform half4 cAcc;
        uniform float2 pShift;
        uniform float2 pCenter;
        uniform float pDu;
        uniform float pN;
        uniform float pSp;
        uniform float pDensity;
        uniform float pSize;
        uniform float pHole;
        uniform float pFadeW;
        uniform float pRef;
        uniform float pGrain;

        half4 main(float2 frag) {
            float2 v = frag + pShift - pCenter;
            float rho = max(length(v), 1.0);

            // Log-polar cell of this pixel. u grows outward; time slides the grid outward (zoom).
            float u = log(rho) / pDu - iTime * pSp;
            float iu = floor(u);
            float ia = floor(atan(v.y, v.x + 0.0001) / 6.2831853 * pN);
            ia = ia > 0.5 * pN - 0.5 ? ia - pN : ia;

            float hp = hash21(float2(iu, ia));
            float h2 = hash21(float2(iu + 57.0, ia + 13.0));
            float h3 = hash21(float2(iu + 113.0, ia + 71.0));
            float h4 = hash21(float2(iu + 211.0, ia + 29.0));
            float h5 = hash21(float2(iu + 307.0, ia + 97.0));
            float h6 = hash21(float2(iu + 401.0, ia + 211.0));

            // The star's centre, kept well inside its cell.
            float lr = pDu * (iu + 0.5 + (h2 - 0.5) * 0.34 + iTime * pSp);
            float rs = exp(lr);
            float th = 6.2831853 * (ia + 0.5 + (h3 - 0.5) * 0.34) / pN;
            float2 spos = rs * float2(cos(th), sin(th));
            float d = length(v - spos);

            // A cell is about rs * pDu px across; stars are up to 30% of that, so they grow with distance.
            float sz = 0.35 + 0.65 * h4 * h4;
            float r = 0.30 * pSize * sz * rs * pDu;
            float depth = 0.30 + 0.70 * smoothstep(0.08, 0.9, rs / pRef);
            float inner = smoothstep(pHole, pHole + pFadeW, rs);
            float present = step(hp, pDensity);
            float a = present * depth * (0.55 + 0.45 * h5) * inner;
            float isRed = step(0.992, h6);

            float3 dotCol = mix(float3(cFg.rgb), float3(cAcc.rgb), isRed);
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
        shader.setFloatUniform("pDu", g.du)
        shader.setFloatUniform("pN", g.n)
        shader.setFloatUniform("pSp", g.sp)
        shader.setFloatUniform("pDensity", g.density)
        shader.setFloatUniform("pSize", g.sizeP)
        shader.setFloatUniform("pHole", g.hole)
        shader.setFloatUniform("pFadeW", g.fadeW)
        shader.setFloatUniform("pRef", g.ref)
        shader.setFloatUniform("pGrain", frame.param("grain"))
    }

    override fun DrawScope.drawFallback(frame: WallpaperFrame) {
        val g = geo(frame)
        val fg = frame.color("fg")
        val acc = frame.color("accent")
        drawRect(frame.color("bg"))
        val w = size.width
        val h = size.height
        val ccx = g.cx - g.sx
        val ccy = g.cy - g.sy
        val tsp = frame.time * g.sp
        // Visible radii run from the clear centre out to the farthest corner; star centres sit 0.33..0.67 cells into a ring.
        val fx = max(ccx, w - ccx)
        val fy = max(ccy, h - ccy)
        val rFar = sqrt(fx * fx + fy * fy) * 1.1f
        val lnLo = ln(max(g.hole, 2f))
        val lnHi = ln(max(rFar, 4f))
        val iuLo = floor(lnLo / g.du - 0.67f - tsp).toInt() - 1
        val iuHi = floor(lnHi / g.du - 0.33f - tsp).toInt() + 1
        val grainOn = frame.param("grain")
        for (iu in iuLo..iuHi) {
            val iuF = iu.toFloat()
            for (ia in -g.nHalf until g.nHalf) {
                val iaF = ia.toFloat()
                val hp = hash21(iuF, iaF)
                if (hp > g.density) continue
                val h2 = hash21(iuF + 57f, iaF + 13f)
                val h3 = hash21(iuF + 113f, iaF + 71f)
                val rs = exp(g.du * (iuF + 0.5f + (h2 - 0.5f) * 0.34f + tsp))
                val inner = smoothstep(g.hole, g.hole + g.fadeW, rs)
                if (inner <= 0.001f) continue
                val th = TAU * (iaF + 0.5f + (h3 - 0.5f) * 0.34f) / g.n
                val x = ccx + rs * cos(th)
                val y = ccy + rs * sin(th)
                val h4 = hash21(iuF + 211f, iaF + 29f)
                val sz = 0.35f + 0.65f * h4 * h4
                val r = 0.30f * g.sizeP * sz * rs * g.du
                if (x < -r || x > w + r || y < -r || y > h + r) continue
                val h5 = hash21(iuF + 307f, iaF + 97f)
                val depth = 0.30f + 0.70f * smoothstep(0.08f, 0.9f, rs / g.ref)
                val a = depth * (0.55f + 0.45f * h5) * inner
                val isRed = hash21(iuF + 401f, iaF + 211f) >= 0.992f
                dot(if (isRed) acc else fg, x, y, r, a)
            }
        }
        drawGrain(frame, grainOn)
    }
}
