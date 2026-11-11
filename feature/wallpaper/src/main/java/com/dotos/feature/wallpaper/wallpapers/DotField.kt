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
import kotlin.math.min
import kotlin.math.sin

/**
 * Dot Field: a quiet, uniform lattice of dots. Size and brightness fall off smoothly (Gaussian) from a focal
 * point that sits on the one signal-red dot, and the falloff slowly breathes. Matte, grainy, restrained.
 */
object DotField : DotWallpaper {
    override val id = "dot_field"
    override val name = "Dot Field"

    override val colorSlots = listOf(
        ColorSlot("bg", "Background"),
        ColorSlot("fg", "Dots"),
        ColorSlot("accent", "Accent dot"),
    )

    override val params = listOf(
        WallpaperParam("spacing", "Dot spacing", 10f, 36f, 17f),
        WallpaperParam("size", "Dot size", 0.3f, 0.95f, 0.66f),
        WallpaperParam("reach", "Falloff reach", 0.25f, 1.3f, 0.55f),
        WallpaperParam("edge", "Edge dots", 0f, 0.6f, 0.14f),
        WallpaperParam("breathe", "Breathing", 0f, 2f, 1f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.45f),
    )

    override val presets = listOf(
        Palettes.midnight.preset(),
        Palettes.paper.preset(mapOf("size" to 0.6f, "edge" to 0.1f)),
        Palettes.signal.preset(mapOf("reach" to 0.5f)),
        Palettes.mono.preset(mapOf("reach" to 0.7f, "edge" to 0.2f)),
        Palettes.ember.preset(mapOf("reach" to 0.48f)),
        Palettes.frost.preset(mapOf("spacing" to 19f, "size" to 0.62f)),
    )

    /** Everything both render paths need for the current frame, computed once so they cannot disagree. */
    private class Geo {
        var s = 16f
        var sx = 0f
        var sy = 0f
        var rMax = 5f
        var accR = 5f
        var fx = 0f
        var fy = 0f
        var ax = 0f
        var ay = 0f
        var ai = 0
        var aj = 0
        var reach = 100f
        var edge = 0.14f
    }

    private fun geo(frame: WallpaperFrame): Geo {
        val g = frame.cache.getOrPut("dot_field_geo") { Geo() } as Geo
        val w = frame.width
        val h = frame.height
        val t = frame.time
        val s = Dots.spacing(frame, frame.param("spacing"))
        val breathe = frame.param("breathe")
        g.s = s
        g.sx = Dots.parallaxX(frame)
        g.sy = Dots.parallaxY(frame)
        g.rMax = 0.5f * s * frame.param("size")
        g.accR = min(g.rMax * (1f + 0.07f * breathe * sin(phase(t, 0.11f))), 0.49f * s)
        // The single accent dot lives on a fixed lattice cell; the focal point drifts a little around it.
        g.ai = floor(0.5f * w / s).toInt()
        g.aj = floor(0.40f * h / s).toInt()
        g.ax = (g.ai + 0.5f) * s
        g.ay = (g.aj + 0.5f) * s
        g.fx = g.ax + 1.6f * s * sin(phase(t, 0.031f))
        g.fy = g.ay + 1.2f * s * cos(phase(t, 0.023f))
        g.reach = max(1f, frame.param("reach") * max(w, h) * (1f + 0.10f * breathe * sin(phase(t, 0.07f))))
        g.edge = frame.param("edge")
        return g
    }

    override val shaderSource = Agsl.COMMON + """
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cFg;
        layout(color) uniform half4 cAcc;
        uniform float2 pShift;
        uniform float pS;
        uniform float pRMax;
        uniform float pAccR;
        uniform float2 pFocal;
        uniform float2 pAccent;
        uniform float pReach;
        uniform float pEdge;
        uniform float pGrain;

        half4 main(float2 frag) {
            float2 wp = frag + pShift;
            float2 c = (floor(wp / pS) + 0.5) * pS;
            float d = length(wp - c);
            float2 q = (c - pFocal) / pReach;
            float f = exp(-2.6 * dot(q, q));
            float isAcc = 1.0 - step(0.5, length(c - pAccent));
            float r = mix(pRMax * (pEdge + (1.0 - pEdge) * f), pAccR, isAcc);
            float a = mix(0.28 + 0.72 * f, 1.0, isAcc);
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
        shader.setFloatUniform("pAccR", g.accR)
        shader.setFloatUniform("pFocal", g.fx, g.fy)
        shader.setFloatUniform("pAccent", g.ax, g.ay)
        shader.setFloatUniform("pReach", g.reach)
        shader.setFloatUniform("pEdge", g.edge)
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
        val invReach = 1f / g.reach
        for (j in j0..j1) {
            val cy = (j + 0.5f) * s
            val dy = (cy - g.fy) * invReach
            for (i in i0..i1) {
                val cx = (i + 0.5f) * s
                val dx = (cx - g.fx) * invReach
                if (i == g.ai && j == g.aj) {
                    dot(acc, cx - g.sx, cy - g.sy, g.accR, 1f)
                } else {
                    val f = exp(-2.6f * (dx * dx + dy * dy))
                    dot(fg, cx - g.sx, cy - g.sy, g.rMax * (g.edge + (1f - g.edge) * f), 0.28f + 0.72f * f)
                }
            }
        }
        drawGrain(frame, frame.param("grain"))
    }
}
