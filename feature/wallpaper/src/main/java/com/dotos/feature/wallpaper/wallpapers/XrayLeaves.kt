package com.dotos.feature.wallpaper.wallpapers

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
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
import com.dotos.feature.wallpaper.wallpapers.Noise.drawGrain
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * X-ray Leaves: a soft-focus, backlit eucalyptus branch. Large round translucent leaves are layered
 * as light-transmission discs (each one multiplies the light, so overlaps go darker) with a thin
 * bright rim; fine black branches (guarded polynomial curves) carry small fuzzy bud clusters; a few
 * wide glows stand in for the bright gaps between leaves. Everything drifts very slowly.
 */
object XrayLeaves : DotWallpaper {
    override val id = "xray_leaves"
    override val name = "X-ray Leaves"

    override val colorSlots = listOf(
        ColorSlot("bg", "Backlight"),
        ColorSlot("mid", "Leaf tone"),
        ColorSlot("dark", "Overlap tone"),
        ColorSlot("stem", "Branches"),
    )

    override val params = listOf(
        WallpaperParam("density", "Leaf density", 0.4f, 1.6f, 1f),
        WallpaperParam("softness", "Softness", 0.3f, 2f, 1f),
        WallpaperParam("stems", "Branches", 0f, 1.5f, 1f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.3f),
        WallpaperParam("drift", "Drift", 0f, 2f, 1f),
    )

    private fun c(hex: Long) = Color(hex)

    override val presets = listOf(
        WallpaperPreset(
            "silver", "Silver",
            mapOf("bg" to c(0xFFF2F3F5), "mid" to c(0xFFA9ABAF), "dark" to c(0xFF4E5055), "stem" to c(0xFF09090A)),
        ),
        WallpaperPreset(
            "night", "Night",
            mapOf("bg" to c(0xFF0C0D10), "mid" to c(0xFF45474D), "dark" to c(0xFFA3A5AB), "stem" to c(0xFFEDEEF1)),
            mapOf("grain" to 0.4f),
        ),
        WallpaperPreset(
            "sepia", "Sepia",
            mapOf("bg" to c(0xFFF6ECD8), "mid" to c(0xFFB39C7C), "dark" to c(0xFF5D4733), "stem" to c(0xFF1D1308)),
        ),
        WallpaperPreset(
            "sage", "Sage",
            mapOf("bg" to c(0xFFEBF0E2), "mid" to c(0xFF9DAA8D), "dark" to c(0xFF4A5841), "stem" to c(0xFF101A0C)),
        ),
        WallpaperPreset(
            "blueprint", "Blueprint",
            mapOf("bg" to c(0xFFE6EEF9), "mid" to c(0xFF7F9CC8), "dark" to c(0xFF22407A), "stem" to c(0xFF07142E)),
            mapOf("density" to 1.15f),
        ),
    )

    override val shaderSource = """
        uniform float2 iResolution;
        uniform float iTime;
        uniform float iScroll;
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 cMid;
        layout(color) uniform half4 cDark;
        layout(color) uniform half4 cStem;
        uniform float pDens;
        uniform float pSoft;
        uniform float pStems;
        uniform float pGrain;
        uniform float pDrift;

        float hash12(float2 p) {
            float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
            p3 += dot(p3, float3(p3.y, p3.z, p3.x) + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        // One translucent leaf: x = light multiplier, y = rim highlight. Lit from the upper right, so
        // the lower-left side of each leaf is thicker (darker).
        float2 leafD(float2 p, float cx, float cy, float R, float dens, float soft, float ph) {
            float t = iTime * pDrift;
            float2 c = float2(cx + 9.0 * sin(t * 0.07 + ph) - (iScroll - 0.5) * 16.0, cy + 7.0 * cos(t * 0.06 + ph * 1.7));
            float rad = R * (1.0 + 0.012 * sin(t * 0.09 + ph * 2.3));
            float2 dv = p - c;
            float d = length(dv);
            if (d > rad + soft * 1.6) return float2(1.0, 0.0);
            float m = 1.0 - smoothstep(rad - soft, rad + soft * 0.5, d);
            float i = clamp(1.0 - d / rad, 0.0, 1.0);
            float side = dot(dv / max(d, 1.0), float2(-0.6, 0.8));
            float body = (0.62 + 0.38 * sqrt(i)) * (1.0 + 0.45 * side * (1.0 - i));
            float rim = smoothstep(rad - soft * 2.4, rad - soft * 0.5, d) * (1.0 - smoothstep(rad - soft * 0.5, rad + soft * 0.6, d));
            return float2(1.0 - dens * m * body, rim * (0.22 - 0.2 * side) * dens);
        }

        // Lagrange quadratic through three (v, w) samples: returns (w, dw/dv).
        float2 quad3(float v, float3 vs, float3 ws) {
            float da = (vs.x - vs.y) * (vs.x - vs.z);
            float db = (vs.y - vs.x) * (vs.y - vs.z);
            float dc = (vs.z - vs.x) * (vs.z - vs.y);
            float w = ws.x * (v - vs.y) * (v - vs.z) / da + ws.y * (v - vs.x) * (v - vs.z) / db + ws.z * (v - vs.x) * (v - vs.y) / dc;
            float dw = ws.x * ((v - vs.y) + (v - vs.z)) / da + ws.y * ((v - vs.x) + (v - vs.z)) / db
                + ws.z * ((v - vs.x) + (v - vs.y)) / dc;
            return float2(w, dw);
        }

        // Thin branch as a quadratic curve: vert = 1 means x = f(y), else y = f(x). Returns (core, halo).
        float2 stemC(float2 p, float vert, float3 vs, float3 ws, float w0, float w1, float soft) {
            float v = mix(p.x, p.y, vert);
            float u = mix(p.y, p.x, vert);
            float t = (v - vs.x) / (vs.z - vs.x);
            if (t < -0.02 || t > 1.02) return float2(0.0);
            float2 q = quad3(v, vs, ws);
            float d = abs(u - q.x) / sqrt(1.0 + q.y * q.y);
            float w = mix(w0, w1, clamp(t, 0.0, 1.0)) * 0.5;
            return float2(1.0 - smoothstep(w - 0.5, w + soft, d), 1.0 - smoothstep(w, w + 26.0, d));
        }

        // Fuzzy bud cluster: a dark core with short ragged spikes.
        float bud(float2 p, float2 c, float s) {
            float2 q = p - c;
            float r2 = dot(q, q);
            if (r2 > 81.0 * s * s) return 0.0;
            float r = sqrt(r2) / s;
            float spikes = 0.5 + 0.5 * cos(atan(q.y, q.x) * 5.0 + c.x);
            float len = 0.5 + 1.4 * spikes * spikes;
            return 1.0 - smoothstep(len * 0.5, len, r);
        }

        half4 main(float2 frag) {
            float pxk = 1380.0 / iResolution.y;
            float2 q = float2(frag.x - 0.5 * iResolution.x, frag.y) * pxk + float2(310.5, 0.0);
            float tm = iTime * pDrift;
            float2 pw = q + 12.0 * float2(sin(q.y * 0.012 + 0.7 + tm * 0.04), sin(q.x * 0.011 + 1.9 - tm * 0.03));
            float soft = 18.0 * pSoft;
            float k = pDens;

            float T = 0.88;
            float rim = 0.0;
            float2 a;
            a = leafD(pw, 440.0, 255.0, 215.0, 0.5 * k, soft * 0.9, 0.0);
            T *= a.x; rim += a.y;
            a = leafD(pw, 70.0, 140.0, 185.0, 0.32 * k, soft * 1.8, 1.3);
            T *= a.x; rim += a.y;
            a = leafD(pw, 100.0, 480.0, 205.0, 0.30 * k, soft * 1.8, 2.1);
            T *= a.x; rim += a.y;
            a = leafD(pw, 320.0, 455.0, 170.0, 0.24 * k, soft * 2.2, 3.4);
            T *= a.x; rim += a.y;
            a = leafD(pw, 560.0, 570.0, 170.0, 0.36 * k, soft * 1.0, 4.2);
            T *= a.x; rim += a.y;
            a = leafD(pw, 95.0, 780.0, 165.0, 0.34 * k, soft * 1.5, 5.0);
            T *= a.x; rim += a.y;
            a = leafD(pw, 235.0, 760.0, 105.0, 0.20 * k, soft * 2.0, 5.9);
            T *= a.x; rim += a.y;
            a = leafD(pw, 545.0, 905.0, 140.0, 0.32 * k, soft * 1.0, 6.6);
            T *= a.x; rim += a.y;
            a = leafD(pw, 470.0, 1135.0, 175.0, 0.32 * k, soft * 1.4, 7.4);
            T *= a.x; rim += a.y;
            a = leafD(pw, 85.0, 1125.0, 165.0, 0.30 * k, soft * 1.6, 8.3);
            T *= a.x; rim += a.y;
            a = leafD(pw, 180.0, 1280.0, 125.0, 0.28 * k, soft * 1.6, 9.1);
            T *= a.x; rim += a.y;
            a = leafD(pw, 330.0, 1345.0, 110.0, 0.22 * k, soft * 1.8, 10.2);
            T *= a.x; rim += a.y;
            a = leafD(pw, 590.0, 1330.0, 110.0, 0.24 * k, soft * 1.4, 11.0);
            T *= a.x; rim += a.y;
            a = leafD(pw, 300.0, 115.0, 120.0, 0.22 * k, soft * 2.0, 12.3);
            T *= a.x; rim += a.y;
            a = leafD(pw, 565.0, 35.0, 100.0, 0.24 * k, soft * 1.2, 13.1);
            T *= a.x; rim += a.y;

            // bright pockets between the leaves
            float2 g1 = q - float2(385.0, 830.0);
            float2 g2 = q - float2(300.0, 585.0);
            float2 g3 = q - float2(40.0, 1000.0);
            T += 0.10 * exp(-dot(g1, g1) / 9000.0) + 0.07 * exp(-dot(g2, g2) / 8000.0) + 0.06 * exp(-dot(g3, g3) / 10000.0);
            T = clamp(T + rim, 0.0, 1.0);

            // branches
            float ss = 0.8 + 0.5 * pSoft;
            float2 s = float2(0.0);
            float2 b;
            b = stemC(q, 1.0, float3(1385.0, 1250.0, 1100.0), float3(325.0, 322.0, 300.0), 13.0, 10.0, ss);
            s = max(s, b);
            b = stemC(q, 1.0, float3(1100.0, 950.0, 790.0), float3(300.0, 293.0, 303.0), 10.0, 7.0, ss);
            s = max(s, b);
            b = stemC(q, 1.0, float3(790.0, 590.0, 335.0), float3(303.0, 238.0, 158.0), 6.5, 3.6, ss);
            s = max(s, b);
            b = stemC(q, 0.0, float3(160.0, 62.0, -25.0), float3(335.0, 220.0, 130.0), 3.2, 2.2, ss);
            s = max(s, b);
            b = stemC(q, 1.0, float3(785.0, 500.0, 262.0), float3(308.0, 378.0, 428.0), 6.5, 3.4, ss);
            s = max(s, b);
            b = stemC(q, 1.0, float3(262.0, 150.0, -10.0), float3(428.0, 455.0, 500.0), 3.0, 1.8, ss);
            s = max(s, b);
            b = stemC(q, 1.0, float3(688.0, 860.0, 1040.0), float3(238.0, 250.0, 290.0), 1.0, 4.0, ss);
            s = max(s, b);
            b = stemC(q, 1.0, float3(-10.0, 100.0, 195.0), float3(205.0, 228.0, 250.0), 2.6, 0.6, ss);
            s = max(s, b);
            b = stemC(q, 0.0, float3(305.0, 440.0, 625.0), float3(852.0, 738.0, 600.0), 5.0, 2.6, ss);
            s = max(s, b);
            b = stemC(q, 0.0, float3(298.0, 370.0, 436.0), float3(936.0, 916.0, 894.0), 4.0, 0.8, ss);
            s = max(s, b);
            b = stemC(q, 0.0, float3(300.0, 440.0, 625.0), float3(1020.0, 940.0, 840.0), 3.6, 2.4, ss);
            s = max(s, b);
            b = stemC(q, 0.0, float3(328.0, 450.0, 545.0), float3(1276.0, 1160.0, 1100.0), 3.4, 2.2, ss);
            s = max(s, b);
            b = stemC(q, 0.0, float3(292.0, 120.0, -10.0), float3(912.0, 822.0, 762.0), 3.6, 2.2, ss);
            s = max(s, b);
            b = stemC(q, 0.0, float3(293.0, 100.0, 20.0), float3(1096.0, 1050.0, 1020.0), 3.6, 2.2, ss);
            s = max(s, b);
            b = stemC(q, 1.0, float3(1190.0, 1262.0, 1360.0), float3(160.0, 210.0, 255.0), 1.0, 3.6, ss);
            s = max(s, b);
            b = stemC(q, 0.0, float3(-10.0, 60.0, 130.0), float3(1290.0, 1340.0, 1385.0), 3.2, 2.4, ss);
            s = max(s, b);

            float buds = 0.0;
            buds = max(buds, bud(q, float2(251.0, 197.0), 9.0));
            buds = max(buds, bud(q, float2(433.0, 268.0), 13.6));
            buds = max(buds, bud(q, float2(385.0, 500.0), 12.9));
            buds = max(buds, bud(q, float2(205.0, 422.0), 11.6));
            buds = max(buds, bud(q, float2(212.0, 464.0), 9.7));
            buds = max(buds, bud(q, float2(245.0, 690.0), 11.0));
            buds = max(buds, bud(q, float2(548.0, 632.0), 14.3));
            buds = max(buds, bud(q, float2(578.0, 884.0), 11.6));
            buds = max(buds, bud(q, float2(484.0, 1132.0), 12.9));
            buds = max(buds, bud(q, float2(66.0, 1088.0), 11.6));
            buds = max(buds, bud(q, float2(214.0, 1262.0), 12.2));
            buds = max(buds, bud(q, float2(130.0, 1084.0), 9.7));

            float stem = clamp(max(s.x, buds * 0.9) * pStems, 0.0, 1.0);
            T *= 1.0 - 0.26 * s.y * clamp(pStems, 0.0, 1.0);

            float3 col = mix(float3(cDark.rgb), float3(cMid.rgb), smoothstep(0.30, 0.66, T));
            col = mix(col, float3(cBg.rgb), smoothstep(0.62, 0.97, T));
            col = mix(col, float3(cStem.rgb), stem);
            col += (hash12(floor(frag)) - 0.5) * pGrain * 0.045;
            return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
        }
    """.trimIndent()

    @RequiresApi(33)
    override fun bindUniforms(shader: RuntimeShader, frame: WallpaperFrame) {
        shader.setStandard(frame)
        shader.setFloatUniform("iScroll", frame.scroll)
        shader.setColor("cBg", frame.color("bg"))
        shader.setColor("cMid", frame.color("mid"))
        shader.setColor("cDark", frame.color("dark"))
        shader.setColor("cStem", frame.color("stem"))
        shader.setFloatUniform("pDens", frame.param("density"))
        shader.setFloatUniform("pSoft", frame.param("softness"))
        shader.setFloatUniform("pStems", frame.param("stems"))
        shader.setFloatUniform("pGrain", frame.param("grain"))
        shader.setFloatUniform("pDrift", frame.param("drift"))
    }

    // ---------------------------------------------------------------------------------------------
    // Canvas fallback (API 29-32 or shader failure): the translucent leaves are baked once into a
    // low-res ImageBitmap (CPU port of leafD, drift frozen) and stretched with bilinear filtering -
    // they are soft-focus anyway - while the branches and buds are drawn as crisp vector lines on
    // top. Tables below are generated from the shader's own numbers.
    // ---------------------------------------------------------------------------------------------

    private const val REF_H = 1380f

    /** cx, cy, radius, density, softness multiplier, phase. */
    private val LEAVES = floatArrayOf(
        440.0f, 255.0f, 215.0f, 0.5f, 0.9f, 0.0f,
        70.0f, 140.0f, 185.0f, 0.32f, 1.8f, 1.3f,
        100.0f, 480.0f, 205.0f, 0.30f, 1.8f, 2.1f,
        320.0f, 455.0f, 170.0f, 0.24f, 2.2f, 3.4f,
        560.0f, 570.0f, 170.0f, 0.36f, 1.0f, 4.2f,
        95.0f, 780.0f, 165.0f, 0.34f, 1.5f, 5.0f,
        235.0f, 760.0f, 105.0f, 0.20f, 2.0f, 5.9f,
        545.0f, 905.0f, 140.0f, 0.32f, 1.0f, 6.6f,
        470.0f, 1135.0f, 175.0f, 0.32f, 1.4f, 7.4f,
        85.0f, 1125.0f, 165.0f, 0.30f, 1.6f, 8.3f,
        180.0f, 1280.0f, 125.0f, 0.28f, 1.6f, 9.1f,
        330.0f, 1345.0f, 110.0f, 0.22f, 1.8f, 10.2f,
        590.0f, 1330.0f, 110.0f, 0.24f, 1.4f, 11.0f,
        300.0f, 115.0f, 120.0f, 0.22f, 2.0f, 12.3f,
        565.0f, 35.0f, 100.0f, 0.24f, 1.2f, 13.1f,
    )

    /** vert, v0, v1, v2, w0, w1, w2, width start, width end. */
    private val STEMS = floatArrayOf(
        1.0f, 1385.0f, 1250.0f, 1100.0f, 325.0f, 322.0f, 300.0f, 13.0f, 10.0f,
        1.0f, 1100.0f, 950.0f, 790.0f, 300.0f, 293.0f, 303.0f, 10.0f, 7.0f,
        1.0f, 790.0f, 590.0f, 335.0f, 303.0f, 238.0f, 158.0f, 6.5f, 3.6f,
        0.0f, 160.0f, 62.0f, -25.0f, 335.0f, 220.0f, 130.0f, 3.2f, 2.2f,
        1.0f, 785.0f, 500.0f, 262.0f, 308.0f, 378.0f, 428.0f, 6.5f, 3.4f,
        1.0f, 262.0f, 150.0f, -10.0f, 428.0f, 455.0f, 500.0f, 3.0f, 1.8f,
        1.0f, 688.0f, 860.0f, 1040.0f, 238.0f, 250.0f, 290.0f, 1.0f, 4.0f,
        1.0f, -10.0f, 100.0f, 195.0f, 205.0f, 228.0f, 250.0f, 2.6f, 0.6f,
        0.0f, 305.0f, 440.0f, 625.0f, 852.0f, 738.0f, 600.0f, 5.0f, 2.6f,
        0.0f, 298.0f, 370.0f, 436.0f, 936.0f, 916.0f, 894.0f, 4.0f, 0.8f,
        0.0f, 300.0f, 440.0f, 625.0f, 1020.0f, 940.0f, 840.0f, 3.6f, 2.4f,
        0.0f, 328.0f, 450.0f, 545.0f, 1276.0f, 1160.0f, 1100.0f, 3.4f, 2.2f,
        0.0f, 292.0f, 120.0f, -10.0f, 912.0f, 822.0f, 762.0f, 3.6f, 2.2f,
        0.0f, 293.0f, 100.0f, 20.0f, 1096.0f, 1050.0f, 1020.0f, 3.6f, 2.2f,
        1.0f, 1190.0f, 1262.0f, 1360.0f, 160.0f, 210.0f, 255.0f, 1.0f, 3.6f,
        0.0f, -10.0f, 60.0f, 130.0f, 1290.0f, 1340.0f, 1385.0f, 3.2f, 2.4f,
    )

    /** x, y, size. */
    private val BUDS = floatArrayOf(
        251.0f, 197.0f, 9.0f,
        433.0f, 268.0f, 13.6f,
        385.0f, 500.0f, 12.9f,
        205.0f, 422.0f, 11.6f,
        212.0f, 464.0f, 9.7f,
        245.0f, 690.0f, 11.0f,
        548.0f, 632.0f, 14.3f,
        578.0f, 884.0f, 11.6f,
        484.0f, 1132.0f, 12.9f,
        66.0f, 1088.0f, 11.6f,
        214.0f, 1262.0f, 12.2f,
        130.0f, 1084.0f, 9.7f,
    )

    private class Baked(val key: Int, val image: ImageBitmap)

    override fun DrawScope.drawFallback(frame: WallpaperFrame) {
        val w = size.width
        val h = size.height
        if (w < 2f || h < 2f) return
        val key = frame.layoutKey()
        var baked = frame.cache["xray_bake"] as? Baked
        if (baked == null || baked.key != key) {
            baked = Baked(key, bakeLeaves(frame, w, h))
            frame.cache["xray_bake"] = baked
        }
        drawImage(baked.image, dstSize = IntSize(w.roundToInt(), h.roundToInt()))
        val stems = frame.param("stems")
        if (stems > 0.01f) drawBranches(frame, w, h, stems)
        drawGrain(frame, frame.param("grain") * 0.5f)
    }

    private fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = clamp01((x - e0) / (e1 - e0))
        return t * t * (3f - 2f * t)
    }

    private fun lagrange(v: Float, v0: Float, v1: Float, v2: Float, w0: Float, w1: Float, w2: Float): Float {
        val da = (v0 - v1) * (v0 - v2)
        val db = (v1 - v0) * (v1 - v2)
        val dc = (v2 - v0) * (v2 - v1)
        return w0 * (v - v1) * (v - v2) / da + w1 * (v - v0) * (v - v2) / db + w2 * (v - v0) * (v - v1) / dc
    }

    private fun bakeLeaves(frame: WallpaperFrame, w: Float, h: Float): ImageBitmap {
        val bw = min(w.roundToInt(), 180)
        val bh = max(2, (bw * h / w).roundToInt())
        val out = IntArray(bw * bh)
        val bg = frame.color("bg")
        val mid = frame.color("mid")
        val dark = frame.color("dark")
        val soft = 18f * frame.param("softness")
        val k = frame.param("density")
        val pxk = REF_H / bh
        val nLeaves = LEAVES.size / 6
        for (py in 0 until bh) {
            for (pxi in 0 until bw) {
                val qx0 = (pxi + 0.5f - 0.5f * bw) * pxk + 310.5f
                val qy0 = (py + 0.5f) * pxk
                val qx = qx0 + 12f * sin(qy0 * 0.012f + 0.7f)
                val qy = qy0 + 12f * sin(qx0 * 0.011f + 1.9f)
                var t = 0.88f
                var rim = 0f
                for (i in 0 until nLeaves) {
                    val o = i * 6
                    val dxv = qx - LEAVES[o]
                    val dyv = qy - LEAVES[o + 1]
                    val rad = LEAVES[o + 2]
                    val sf = soft * LEAVES[o + 4]
                    val d = sqrt(dxv * dxv + dyv * dyv)
                    if (d > rad + sf * 1.6f) continue
                    val dens = LEAVES[o + 3] * k
                    val m = 1f - smoothstep(rad - sf, rad + sf * 0.5f, d)
                    val ii = clamp01(1f - d / rad)
                    val side = (dxv * -0.6f + dyv * 0.8f) / max(d, 1f)
                    val body = (0.62f + 0.38f * sqrt(ii)) * (1f + 0.45f * side * (1f - ii))
                    val rm = smoothstep(rad - sf * 2.4f, rad - sf * 0.5f, d) * (1f - smoothstep(rad - sf * 0.5f, rad + sf * 0.6f, d))
                    t *= 1f - dens * m * body
                    rim += rm * (0.22f - 0.2f * side) * dens
                }
                val g1x = qx0 - 385f
                val g1y = qy0 - 830f
                val g2x = qx0 - 300f
                val g2y = qy0 - 585f
                val g3x = qx0 - 40f
                val g3y = qy0 - 1000f
                t += 0.10f * exp(-(g1x * g1x + g1y * g1y) / 9000f) + 0.07f * exp(-(g2x * g2x + g2y * g2y) / 8000f) +
                    0.06f * exp(-(g3x * g3x + g3y * g3y) / 10000f)
                t = clamp01(t + rim)
                val a = smoothstep(0.30f, 0.66f, t)
                val b = smoothstep(0.62f, 0.97f, t)
                var r = dark.red + (mid.red - dark.red) * a
                var g = dark.green + (mid.green - dark.green) * a
                var bl = dark.blue + (mid.blue - dark.blue) * a
                r += (bg.red - r) * b
                g += (bg.green - g) * b
                bl += (bg.blue - bl) * b
                val ri = (clamp01(r) * 255f + 0.5f).toInt()
                val gi = (clamp01(g) * 255f + 0.5f).toInt()
                val bi = (clamp01(bl) * 255f + 0.5f).toInt()
                out[py * bw + pxi] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        return android.graphics.Bitmap
            .createBitmap(out, bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }

    private fun DrawScope.drawBranches(frame: WallpaperFrame, w: Float, h: Float, strength: Float) {
        val pxk = REF_H / h
        val ink = frame.color("stem").copy(alpha = clamp01(strength))
        val halo = frame.color("stem").copy(alpha = 0.05f * clamp01(strength))
        val segs = 14
        val nStems = STEMS.size / 9
        for (i in 0 until nStems) {
            val o = i * 9
            val vert = STEMS[o] > 0.5f
            val v0 = STEMS[o + 1]
            val v2 = STEMS[o + 3]
            var px = 0f
            var py = 0f
            for (s in 0..segs) {
                val t = s.toFloat() / segs
                val v = v0 + (v2 - v0) * t
                val u = lagrange(v, v0, STEMS[o + 2], v2, STEMS[o + 4], STEMS[o + 5], STEMS[o + 6])
                val qx = if (vert) u else v
                val qy = if (vert) v else u
                val x = (qx - 310.5f) / pxk + 0.5f * w
                val y = qy / pxk
                if (s > 0) {
                    val wid = (STEMS[o + 7] + (STEMS[o + 8] - STEMS[o + 7]) * t) / pxk
                    drawLine(halo, Offset(px, py), Offset(x, y), wid * 7f)
                    drawLine(ink, Offset(px, py), Offset(x, y), max(1f, wid), StrokeCap.Round)
                }
                px = x
                py = y
            }
        }
        val nBuds = BUDS.size / 3
        for (i in 0 until nBuds) {
            val cx = (BUDS[i * 3] - 310.5f) / pxk + 0.5f * w
            val cy = BUDS[i * 3 + 1] / pxk
            val r = BUDS[i * 3 + 2] * 0.9f / pxk
            drawCircle(ink, r * 0.55f, Offset(cx, cy))
            for (j in 0 until 5) {
                val a = j * 1.2566371f + BUDS[i * 3]
                val len = r * (1.1f + 0.6f * ((j * 7 + i * 3) % 4) / 3f)
                drawLine(ink, Offset(cx, cy), Offset(cx + cos(a) * len, cy + sin(a) * len), max(1f, r * 0.14f), StrokeCap.Round)
            }
        }
    }
}
