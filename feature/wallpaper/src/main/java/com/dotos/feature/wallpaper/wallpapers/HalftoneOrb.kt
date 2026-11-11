package com.dotos.feature.wallpaper.wallpapers

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
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
import kotlin.math.sqrt

/**
 * Halftone Orb: a sphere built from nothing but variable-radius dots on a hex lattice. Lambert shading drives the
 * dot area (classic halftone: radius follows the square root of the tone). The light circles the sphere very
 * slowly and the dot nearest the specular highlight picks up the accent colour. On a light background the tone is
 * inverted so the dots read as ink on paper: small where lit, large in shadow.
 */
object HalftoneOrb : DotWallpaper {
    override val id = "halftone_orb"
    override val name = "Halftone Orb"

    override val colorSlots = listOf(
        ColorSlot("bg", "Background"),
        ColorSlot("fg", "Dots"),
        ColorSlot("accent", "Highlight dot"),
    )

    override val params = listOf(
        WallpaperParam("spacing", "Dot spacing", 8f, 30f, 11f),
        WallpaperParam("size", "Dot size", 0.4f, 0.98f, 0.9f),
        WallpaperParam("orb", "Orb size", 0.4f, 1.15f, 0.82f),
        WallpaperParam("light", "Light direction", 0f, 360f, 225f),
        WallpaperParam("backdrop", "Background dots", 0f, 1f, 0.2f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.45f),
    )

    override val presets = listOf(
        Palettes.midnight.preset(),
        Palettes.paper.preset(mapOf("light" to 315f)),
        Palettes.signal.preset(mapOf("backdrop" to 0.12f)),
        Palettes.mono.preset(mapOf("light" to 200f)),
        Palettes.ember.preset(mapOf("light" to 250f, "orb" to 0.9f)),
        Palettes.frost.preset(mapOf("light" to 300f)),
    )

    private class Geo {
        var s = 16f
        var rh = 14f
        var sx = 0f
        var sy = 0f
        var rMax = 5f
        var ox = 0f
        var oy = 0f
        var rad = 100f
        var lx = 0f
        var ly = 0f
        var lz = 1f
        var hotX = 0f
        var hotY = 0f
        var inv = 0f
        var backdrop = 0.2f
    }

    private fun geo(frame: WallpaperFrame): Geo {
        val g = frame.cache.getOrPut("halftone_orb_geo") { Geo() } as Geo
        val w = frame.width
        val h = frame.height
        val t = frame.time
        val s = Dots.spacing(frame, frame.param("spacing"))
        g.s = s
        g.rh = 0.8660254f * s
        g.sx = Dots.parallaxX(frame)
        g.sy = Dots.parallaxY(frame)
        g.rMax = 0.5f * s * frame.param("size")
        g.rad = max(1f, 0.5f * min(w, h) * frame.param("orb"))
        g.ox = 0.5f * w
        g.oy = 0.43f * h + 0.008f * h * sin(phase(t, 0.05f))
        // Light: fixed elevation, azimuth = user angle plus a very slow full turn (about two minutes).
        val az = frame.param("light") * (TAU / 360f) + phase(t, 0.008f)
        val el = 0.62f
        val ce = cos(el)
        g.lx = cos(az) * ce
        g.ly = sin(az) * ce
        g.lz = sin(el)
        // Specular highlight sits where the surface normal is halfway between the light and the viewer.
        val hx = g.lx
        val hy = g.ly
        val hz = g.lz + 1f
        val hl = sqrt(hx * hx + hy * hy + hz * hz)
        g.hotX = g.ox + g.rad * hx / hl
        g.hotY = g.oy + g.rad * hy / hl
        g.inv = if (frame.color("fg").luminance() < frame.color("bg").luminance()) 1f else 0f
        g.backdrop = frame.param("backdrop")
        return g
    }

    override val shaderSource = Agsl.COMMON + """
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cFg;
        layout(color) uniform half4 cAcc;
        uniform float2 pShift;
        uniform float pS;
        uniform float pRMax;
        uniform float2 pOrb;
        uniform float pRad;
        uniform float3 pLight;
        uniform float2 pHot;
        uniform float pInv;
        uniform float pBackdrop;
        uniform float pGrain;

        // Offset from p to the nearest centre of a hex lattice with horizontal pitch s.
        float2 hexOffset(float2 p, float s) {
            float2 r = float2(s, 1.7320508 * s);
            float2 h = r * 0.5;
            float2 a = mod(p, r) - h;
            float2 b = mod(p - h, r) - h;
            return dot(a, a) < dot(b, b) ? a : b;
        }

        half4 main(float2 frag) {
            float2 wp = frag + pShift;
            float2 g = hexOffset(wp, pS);
            float2 c = wp - g;
            float d = length(g);

            float2 v = (c - pOrb) / pRad;
            float rho = length(v);
            float2 vv = rho > 1.0 ? v / rho : v;
            float vz = sqrt(max(0.0, 1.0 - dot(vv, vv)));
            float ndl = max(0.0, dot(float3(vv, vz), pLight));
            float tone = 0.07 + 0.93 * ndl;
            float covS = mix(tone, 1.0 - tone, pInv);
            float brS = 0.6 + 0.4 * covS;

            // Soft silhouette: blend sphere shading into the backdrop over about one dot.
            float edgeW = 0.6 * pS / pRad;
            float m = 1.0 - smoothstep(1.0 - edgeW, 1.0 + edgeW, rho);
            float cov = mix(0.05, covS, m);
            float br = mix(pBackdrop, brS, m);
            float r = pRMax * sqrt(cov);

            float2 hd = c - pHot;
            float hot = exp(-dot(hd, hd) / (0.64 * pS * pS));
            r = max(r, pRMax * 0.55 * hot);
            br = max(br, hot);

            float3 dotCol = mix(float3(cFg.rgb), float3(cAcc.rgb), hot);
            float3 col = mix(float3(cBg.rgb), dotCol, dotCover(d, r) * br);
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
        shader.setFloatUniform("pOrb", g.ox, g.oy)
        shader.setFloatUniform("pRad", g.rad)
        shader.setFloatUniform("pLight", g.lx, g.ly, g.lz)
        shader.setFloatUniform("pHot", g.hotX, g.hotY)
        shader.setFloatUniform("pInv", g.inv)
        shader.setFloatUniform("pBackdrop", g.backdrop)
        shader.setFloatUniform("pGrain", frame.param("grain"))
    }

    override fun DrawScope.drawFallback(frame: WallpaperFrame) {
        val g = geo(frame)
        val fg = frame.color("fg")
        val acc = frame.color("accent")
        drawRect(frame.color("bg"))
        val s = g.s
        val rh = g.rh
        val m0 = floor(g.sy / rh).toInt()
        val m1 = floor((g.sy + size.height) / rh).toInt() + 1
        val i0 = floor(g.sx / s).toInt() - 1
        val i1 = floor((g.sx + size.width) / s).toInt() + 1
        val edgeW = 0.6f * s / g.rad
        for (row in m0..m1) {
            val cy = row * rh
            val off = if ((row and 1) == 1) 0.5f else 0f
            val vy = (cy - g.oy) / g.rad
            for (i in i0..i1) {
                val cx = (i + off) * s
                val vx = (cx - g.ox) / g.rad
                val rho = sqrt(vx * vx + vy * vy)
                var nx = vx
                var ny = vy
                if (rho > 1f) {
                    nx /= rho
                    ny /= rho
                }
                val nz = sqrt(max(0f, 1f - (nx * nx + ny * ny)))
                val ndl = max(0f, nx * g.lx + ny * g.ly + nz * g.lz)
                val tone = 0.07f + 0.93f * ndl
                val covS = tone + (1f - 2f * tone) * g.inv
                val brS = 0.6f + 0.4f * covS
                val inside = 1f - smoothstep(1f - edgeW, 1f + edgeW, rho)
                val cov = 0.05f + (covS - 0.05f) * inside
                var br = g.backdrop + (brS - g.backdrop) * inside
                var r = g.rMax * sqrt(cov)
                val hdx = cx - g.hotX
                val hdy = cy - g.hotY
                val hot = exp(-(hdx * hdx + hdy * hdy) / (0.64f * s * s))
                r = max(r, g.rMax * 0.55f * hot)
                br = max(br, hot)
                dot(if (hot > 0.02f) mixColor(fg, acc, hot) else fg, cx - g.sx, cy - g.sy, r, br)
            }
        }
        drawGrain(frame, frame.param("grain"))
    }
}
