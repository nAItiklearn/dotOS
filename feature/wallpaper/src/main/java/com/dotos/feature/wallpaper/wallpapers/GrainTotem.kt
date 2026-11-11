package com.dotos.feature.wallpaper.wallpapers

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import com.dotos.feature.wallpaper.engine.ColorSlot
import com.dotos.feature.wallpaper.engine.DotWallpaper
import com.dotos.feature.wallpaper.engine.WallpaperFrame
import com.dotos.feature.wallpaper.engine.WallpaperParam
import com.dotos.feature.wallpaper.engine.WallpaperPreset
import com.dotos.feature.wallpaper.engine.setColor
import com.dotos.feature.wallpaper.engine.setStandard
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Grain Totem: a centred vertical stack of flat shapes and "sand" - a band of dense film grain that
 * fades at three edges, a solid disc, a quarter-square of radial grain, a tall capsule of grain that
 * dies away diagonally, and a soft gradient orb - joined by three 1 px alignment crosshairs.
 *
 * The grain is a per-pixel hash: every pixel is either covered (probability = local density) or
 * bare, covered pixels get a random level around the ink colour. That reads as dithered sand, not
 * blur. All geometry is authored in a 621 x 1380 reference space (see [REF_H]) and scaled by the
 * screen height, so the composition keeps its proportions on any phone.
 */
object GrainTotem : DotWallpaper {
    override val id = "grain_totem"
    override val name = "Grain Totem"

    override val colorSlots = listOf(
        ColorSlot("bg", "Background"),
        ColorSlot("ink", "Grain"),
        ColorSlot("ink2", "Corner grain"),
        ColorSlot("disc", "Disc"),
        ColorSlot("orb", "Orb top"),
        ColorSlot("orb2", "Orb bottom"),
        ColorSlot("line", "Hairline"),
        ColorSlot("lineHi", "Hairline accent"),
    )

    override val params = listOf(
        WallpaperParam("grain", "Grain", 0f, 1.5f, 1f),
        WallpaperParam("softness", "Edge softness", 0.4f, 1.8f, 1f),
        WallpaperParam("scale", "Scale", 0.7f, 1.3f, 1f),
        WallpaperParam("lines", "Hairlines", 0f, 1f, 1f),
        WallpaperParam("motion", "Motion", 0f, 1f, 0.25f),
    )

    private fun c(hex: Long) = Color(hex)

    override val presets = listOf(
        WallpaperPreset(
            "light", "Light",
            mapOf(
                "bg" to c(0xFFB8B8B8), "ink" to c(0xFF464646), "ink2" to c(0xFF464646), "disc" to c(0xFF464646),
                "orb" to c(0xFF7B7B7B), "orb2" to c(0xFFA9A9A9), "line" to c(0xFF3A3A3A), "lineHi" to c(0xFFFFFFFF),
            ),
        ),
        WallpaperPreset(
            "dark", "Dark",
            mapOf(
                "bg" to c(0xFF141414), "ink" to c(0xFF464646), "ink2" to c(0xFF6A6A6A), "disc" to c(0xFF686868),
                "orb" to c(0xFF303030), "orb2" to c(0xFF1A1A1A), "line" to c(0xFF8E8E8E), "lineHi" to c(0xFFBEBEBE),
            ),
        ),
        WallpaperPreset(
            "bone", "Bone",
            mapOf(
                "bg" to c(0xFFD8D2C6), "ink" to c(0xFF3C3935), "ink2" to c(0xFF3C3935), "disc" to c(0xFF3C3935),
                "orb" to c(0xFF8A847A), "orb2" to c(0xFFBDB6AA), "line" to c(0xFF2E2C29), "lineHi" to c(0xFFFFFFFF),
            ),
        ),
        WallpaperPreset(
            "signal", "Signal",
            mapOf(
                "bg" to c(0xFF111111), "ink" to c(0xFF4A4A4A), "ink2" to c(0xFFD71921), "disc" to c(0xFFD71921),
                "orb" to c(0xFF2C2C2C), "orb2" to c(0xFF181818), "line" to c(0xFF8A8A8A), "lineHi" to c(0xFFE6E6E6),
            ),
        ),
        WallpaperPreset(
            "sage", "Sage",
            mapOf(
                "bg" to c(0xFFA7AE98), "ink" to c(0xFF3D4535), "ink2" to c(0xFF3D4535), "disc" to c(0xFF3D4535),
                "orb" to c(0xFF6F7862), "orb2" to c(0xFF9BA38C), "line" to c(0xFF2C3226), "lineHi" to c(0xFFF2F4EA),
            ),
        ),
    )

    override val shaderSource = """
        uniform float2 iResolution;
        uniform float iTime;
        uniform float iScroll;
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cInk;
        layout(color) uniform half4 cInk2;
        layout(color) uniform half4 cDisc;
        layout(color) uniform half4 cOrb;
        layout(color) uniform half4 cOrb2;
        layout(color) uniform half4 cLine;
        layout(color) uniform half4 cLineHi;
        uniform float pGrain;
        uniform float pSoft;
        uniform float pScale;
        uniform float pLines;
        uniform float pMotion;

        float hash12(float2 p) {
            float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
            p3 += dot(p3, float3(p3.y, p3.z, p3.x) + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        // Sand: per-pixel coverage c jitters around the local density a (zero-mean, amplitude shrinks
        // towards both 0 and 1, so feathered edges read as smooth haze and the core is solid), and the
        // ink level jitters around the ink colour.
        float3 sand(float3 base, float3 ink, float a, float2 cell) {
            float amp = (0.55 + 0.45 * smoothstep(0.1, 0.5, a)) * 2.0 * min(a, 1.0 - a);
            float c = a + (hash12(cell) - 0.5) * amp;
            // Light grain on a dark ground is a little more speckled than dark grain on light.
            float spread = 1.0 + 0.28 * step(dot(base, float3(0.333)), dot(ink, float3(0.333)));
            float lvl = max(0.0, 1.0 + (hash12(cell + 37.17) - 0.5) * 1.56 * spread);
            float3 smooth0 = mix(base, ink, a);
            float3 grainy = mix(base, ink * lvl, c);
            return smooth0 + (grainy - smooth0) * pGrain;
        }

        float cross1(float2 f, float2 cdev, float halfArm, float w) {
            float2 d = abs(f - cdev);
            float h = clamp(0.5 * w + 0.5 - d.y, 0.0, 1.0) * step(d.x, halfArm);
            float v = clamp(0.5 * w + 0.5 - d.x, 0.0, 1.0) * step(d.y, halfArm);
            return max(h, v);
        }

        // Reference-space (621 x 1380) point -> device pixel centre.
        float2 toDev(float2 q, float pxk, float s) {
            float2 d = float2((q.x - 310.5) * s / pxk + 0.5 * iResolution.x, ((q.y - 640.0) * s + 640.0) / pxk);
            return floor(d) + 0.5;
        }

        half4 main(float2 frag) {
            float pxk = 1380.0 / iResolution.y;
            float s = pScale;
            float2 q = float2(frag.x - 0.5 * iResolution.x, frag.y) * pxk + float2(310.5, 0.0);
            q = (q - float2(310.5, 640.0)) / s + float2(310.5, 640.0);
            float aa = pxk / s;
            float m = pMotion;
            float sc = iScroll - 0.5;
            float t = iTime;
            float breath = 1.0 + 0.07 * m * sin(t * 0.31);
            float2 cell = floor(frag);

            float3 bg = float3(cBg.rgb);
            float3 ink = float3(cInk.rgb);
            float3 col = bg;

            // Capsule of grain, dying away along a diagonal.
            float2 qp = q - float2(sc * 5.0 * m, 2.0 * m * sin(t * 0.21));
            float2 pa = qp - float2(310.5, 756.0);
            float2 ab = float2(0.0, 253.0);
            float hp = clamp(dot(pa, ab) / dot(ab, ab), 0.0, 1.0);
            float dP = length(pa - ab * hp) - 118.0;
            float covP = clamp(0.5 - dP / aa, 0.0, 1.0);
            float fadeP = smoothstep(880.0 * breath, (880.0 + 220.0 * pSoft) * breath, 0.5 * qp.x + 0.866 * qp.y);
            if (covP > 0.0) col = mix(col, sand(col, ink, 0.54 * (1.0 - fadeP), cell), covP);

            // Soft orb (opaque, over the capsule).
            float2 qo = q - float2(250.0 + sc * 3.0 * m, 875.0 + 2.0 * m * sin(t * 0.17 + 2.0));
            float covO = clamp(0.5 - (length(qo) - 95.0) / aa, 0.0, 1.0);
            float tt = dot(qo / 95.0, float2(-0.55, 0.85));
            float3 orbCol = mix(float3(cOrb.rgb), float3(cOrb2.rgb), smoothstep(-0.9, 0.7, tt));
            col = mix(col, orbCol, covO);

            // Band of dense grain: soft left / right / top, hard bottom.
            float2 qr = q - float2(sc * 9.0 * m, 2.5 * m * sin(t * 0.27 + 1.0));
            float fs = 32.0 * pSoft * breath;
            float ft = 40.0 * pSoft * breath;
            float aR = (1.0 - smoothstep(134.0 - fs, 134.0 + fs, abs(qr.x - 310.5))) * smoothstep(208.0 - ft, 208.0 + ft, qr.y);
            float covR = clamp(0.5 - (qr.y - 354.0) / aa, 0.0, 1.0);
            col = mix(col, sand(col, ink, aR, cell), covR);

            // Solid disc minus its lower-right quadrant, which the corner grain takes over.
            float2 qd = q - float2(310.5 + sc * 6.0 * m, 488.5 + 2.0 * m * sin(t * 0.23 + 0.5));
            float covD = clamp(0.5 - (length(qd) - 128.5) / aa, 0.0, 1.0);
            float quad = clamp(qd.x / aa + 0.5, 0.0, 1.0) * clamp(qd.y / aa + 0.5, 0.0, 1.0);
            col = mix(col, float3(cDisc.rgb), covD * (1.0 - quad));

            // Quarter-square of radial grain, hard on its two inner edges.
            float r = length(qd);
            float aQ = 1.0 - smoothstep(50.0 * breath, (50.0 + 145.0 * pSoft) * breath, r);
            if (quad > 0.0) col = mix(col, sand(col, float3(cInk2.rgb), aQ, cell), quad);

            // 1 px crosshairs, snapped to device pixels.
            float arm = 55.5 * s / pxk;
            float lw = max(1.0, s / pxk);
            float l1 = cross1(frag, toDev(float2(310.5, 200.0), pxk, s), arm, lw);
            float l2 = cross1(frag, toDev(float2(310.5, 628.0), pxk, s), arm, lw);
            float l3 = cross1(frag, toDev(float2(310.5, 872.0), pxk, s), arm, lw);
            col = mix(col, float3(cLine.rgb), max(l1, l3) * 0.85 * pLines);
            col = mix(col, float3(cLineHi.rgb), l2 * 0.9 * pLines);

            return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
        }
    """.trimIndent()

    @RequiresApi(33)
    override fun bindUniforms(shader: RuntimeShader, frame: WallpaperFrame) {
        shader.setStandard(frame)
        shader.setFloatUniform("iScroll", frame.scroll)
        shader.setColor("cBg", frame.color("bg"))
        shader.setColor("cInk", frame.color("ink"))
        shader.setColor("cInk2", frame.color("ink2"))
        shader.setColor("cDisc", frame.color("disc"))
        shader.setColor("cOrb", frame.color("orb"))
        shader.setColor("cOrb2", frame.color("orb2"))
        shader.setColor("cLine", frame.color("line"))
        shader.setColor("cLineHi", frame.color("lineHi"))
        shader.setFloatUniform("pGrain", frame.param("grain"))
        shader.setFloatUniform("pSoft", frame.param("softness"))
        shader.setFloatUniform("pScale", frame.param("scale"))
        shader.setFloatUniform("pLines", frame.param("lines"))
        shader.setFloatUniform("pMotion", frame.param("motion"))
    }

    // ---------------------------------------------------------------------------------------------
    // Canvas fallback (API 29-32 or shader failure): a CPU port of the shader, baked once into an
    // ImageBitmap (<= 720 px wide) and re-baked only when size / params / colours change. Static: no
    // breathing or parallax. Hairlines are drawn as vector lines on top, so they stay crisp.
    // ---------------------------------------------------------------------------------------------

    private class Baked(val key: Int, val image: ImageBitmap)

    /** Mutable colour accumulator, allocated once per bake. */
    private class Rgb {
        var r = 0f
        var g = 0f
        var b = 0f
    }

    override fun DrawScope.drawFallback(frame: WallpaperFrame) {
        val w = size.width
        val h = size.height
        if (w < 2f || h < 2f) return
        val key = frame.layoutKey()
        var baked = frame.cache["totem_bake"] as? Baked
        if (baked == null || baked.key != key) {
            baked = Baked(key, bake(frame, w, h))
            frame.cache["totem_bake"] = baked
        }
        drawImage(baked.image, dstSize = IntSize(w.roundToInt(), h.roundToInt()))
        drawCrosshairs(frame, w, h)
    }

    private fun DrawScope.drawCrosshairs(frame: WallpaperFrame, w: Float, h: Float) {
        val lines = frame.param("lines")
        if (lines <= 0.01f) return
        val s = frame.param("scale")
        val pxk = REF_H / h
        val arm = 55.5f * s / pxk
        val lw = max(1f, s / pxk)
        val dark = frame.color("line").copy(alpha = (0.85f * lines).coerceIn(0f, 1f))
        val light = frame.color("lineHi").copy(alpha = (0.9f * lines).coerceIn(0f, 1f))
        val cx = floor(0.5f * w) + 0.5f
        for (i in 0 until 3) {
            val refY = if (i == 0) 200f else if (i == 1) 628f else 872f
            val y = floor(((refY - 640f) * s + 640f) / pxk) + 0.5f
            val c = if (i == 1) light else dark
            drawLine(c, Offset(cx - arm, y), Offset(cx + arm, y), lw)
            drawLine(c, Offset(cx, y - arm), Offset(cx, y + arm), lw)
        }
    }

    private const val REF_H = 1380f

    private fun fract(x: Float): Float = x - floor(x)

    private fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = clamp01((x - e0) / (e1 - e0))
        return t * t * (3f - 2f * t)
    }

    private fun hash12(x: Float, y: Float): Float {
        var a = fract(x * 0.1031f)
        var b = fract(y * 0.1031f)
        var c = a
        val d = a * (b + 33.33f) + b * (c + 33.33f) + c * (a + 33.33f)
        a += d
        b += d
        c += d
        return fract((a + b) * c)
    }

    /** Port of the shader's sand(): blends grain of colour [ink] and density [a] over [px] by [cover]. */
    private fun sand(px: Rgb, ink: Color, a: Float, cx: Float, cy: Float, grain: Float, cover: Float) {
        val amp = (0.55f + 0.45f * smoothstep(0.1f, 0.5f, a)) * 2f * min(a, 1f - a)
        val c = a + (hash12(cx, cy) - 0.5f) * amp
        val inkSum = ink.red + ink.green + ink.blue
        val speckle = if (inkSum > px.r + px.g + px.b) 1.28f else 1f
        val lvl = max(0f, 1f + (hash12(cx + 37.17f, cy + 37.17f) - 0.5f) * 1.56f * speckle)
        val r = px.r + (ink.red - px.r) * a
        val g = px.g + (ink.green - px.g) * a
        val b = px.b + (ink.blue - px.b) * a
        val gr = px.r + (ink.red * lvl - px.r) * c
        val gg = px.g + (ink.green * lvl - px.g) * c
        val gb = px.b + (ink.blue * lvl - px.b) * c
        val rr = r + (gr - r) * grain
        val rg = g + (gg - g) * grain
        val rb = b + (gb - b) * grain
        px.r += (rr - px.r) * cover
        px.g += (rg - px.g) * cover
        px.b += (rb - px.b) * cover
    }

    private fun bake(frame: WallpaperFrame, w: Float, h: Float): ImageBitmap {
        val bw = min(w.roundToInt(), 720)
        val bh = max(2, (bw * h / w).roundToInt())
        val out = IntArray(bw * bh)
        val bg = frame.color("bg")
        val ink = frame.color("ink")
        val ink2 = frame.color("ink2")
        val disc = frame.color("disc")
        val orb = frame.color("orb")
        val orb2 = frame.color("orb2")
        val grain = frame.param("grain")
        val soft = frame.param("softness")
        val s = frame.param("scale")
        val pxk = REF_H / bh
        val aa = pxk / s
        val px = Rgb()
        for (py in 0 until bh) {
            for (pxi in 0 until bw) {
                val cx = pxi.toFloat()
                val cy = py.toFloat()
                var qx = (cx + 0.5f - 0.5f * bw) * pxk + 310.5f
                var qy = (cy + 0.5f) * pxk
                qx = (qx - 310.5f) / s + 310.5f
                qy = (qy - 640f) / s + 640f
                px.r = bg.red
                px.g = bg.green
                px.b = bg.blue

                // Capsule of grain, dying away along a diagonal.
                val pay = qy - 756f
                val hp = clamp01(pay / 253f)
                val ddx = qx - 310.5f
                val ddy = pay - 253f * hp
                val covP = clamp01(0.5f - (sqrt(ddx * ddx + ddy * ddy) - 118f) / aa)
                if (covP > 0f) {
                    val fade = smoothstep(880f, 880f + 220f * soft, 0.5f * qx + 0.866f * qy)
                    sand(px, ink, 0.54f * (1f - fade), cx, cy, grain, covP)
                }

                // Soft orb.
                val ox = qx - 250f
                val oy = qy - 875f
                val covO = clamp01(0.5f - (sqrt(ox * ox + oy * oy) - 95f) / aa)
                if (covO > 0f) {
                    val m = smoothstep(-0.9f, 0.7f, (ox * -0.55f + oy * 0.85f) / 95f)
                    px.r += (orb.red + (orb2.red - orb.red) * m - px.r) * covO
                    px.g += (orb.green + (orb2.green - orb.green) * m - px.g) * covO
                    px.b += (orb.blue + (orb2.blue - orb.blue) * m - px.b) * covO
                }

                // Dense band: soft left / right / top, hard bottom.
                val fs = 32f * soft
                val ft = 40f * soft
                val aR = (1f - smoothstep(134f - fs, 134f + fs, abs(qx - 310.5f))) * smoothstep(208f - ft, 208f + ft, qy)
                val covR = clamp01(0.5f - (qy - 354f) / aa)
                if (covR > 0f) sand(px, ink, aR, cx, cy, grain, covR)

                // Solid disc minus its lower-right quadrant, then the quarter of radial grain.
                val dx = qx - 310.5f
                val dy = qy - 488.5f
                val rad = sqrt(dx * dx + dy * dy)
                val quad = clamp01(dx / aa + 0.5f) * clamp01(dy / aa + 0.5f)
                val covD = clamp01(0.5f - (rad - 128.5f) / aa) * (1f - quad)
                if (covD > 0f) {
                    px.r += (disc.red - px.r) * covD
                    px.g += (disc.green - px.g) * covD
                    px.b += (disc.blue - px.b) * covD
                }
                if (quad > 0f) {
                    val aQ = 1f - smoothstep(50f, 50f + 145f * soft, rad)
                    sand(px, ink2, aQ, cx, cy, grain, quad)
                }

                val ri = (clamp01(px.r) * 255f + 0.5f).toInt()
                val gi = (clamp01(px.g) * 255f + 0.5f).toInt()
                val bi = (clamp01(px.b) * 255f + 0.5f).toInt()
                out[py * bw + pxi] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        return android.graphics.Bitmap
            .createBitmap(out, bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
}
