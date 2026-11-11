package com.dotos.feature.wallpaper.wallpapers

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.dotos.feature.wallpaper.engine.ColorSlot
import com.dotos.feature.wallpaper.engine.DotWallpaper
import com.dotos.feature.wallpaper.engine.WallpaperFrame
import com.dotos.feature.wallpaper.engine.WallpaperParam
import com.dotos.feature.wallpaper.engine.WallpaperPreset
import com.dotos.feature.wallpaper.engine.setColor
import com.dotos.feature.wallpaper.engine.setStandard
import com.dotos.feature.wallpaper.wallpapers.Noise.drawGrain
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fluted Bloom: a flower (or an ink silhouette) seen through reeded glass.
 *
 * The subject is defined analytically in the shader (no textures). The glass is a per-flute lens:
 * every flute shows its own upright window onto the subject, and the windows of neighbouring
 * flutes overlap, so the subject is cut, stepped and repeated flute by flute. Near one edge of each
 * flute the lens "stalls" and stretches the subject vertically into thin drips; a soft light-to-dark
 * ramp, a hairline at each rib and fine grain finish the glass.
 */
object FlutedBloom : DotWallpaper {
    override val id = "fluted_bloom"
    override val name = "Fluted Bloom"

    override val colorSlots = listOf(
        ColorSlot("bg", "Glass backdrop"),
        ColorSlot("c1", "Subject deep"),
        ColorSlot("c2", "Subject light"),
        ColorSlot("c3", "Subject core"),
        ColorSlot("stem", "Stem"),
    )

    override val params = listOf(
        WallpaperParam("subject", "Subject (0 anemone, 1 chrysanthemum, 2 silhouette)", 0f, 2f, 0f, 1f),
        WallpaperParam("flutes", "Flutes", 6f, 28f, 14f, 1f),
        WallpaperParam("refraction", "Refraction", 0f, 1.5f, 1f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.35f),
        WallpaperParam("sway", "Sway", 0f, 1f, 0.5f),
        WallpaperParam("drip", "Drip", 0f, 1.5f, 1f),
    )

    private fun c(hex: Long) = Color(hex)

    override val presets = listOf(
        WallpaperPreset(
            "violet", "Violet",
            mapOf(
                "bg" to c(0xFFD7D8D9), "c1" to c(0xFF2B1870), "c2" to c(0xFFB59BEA), "c3" to c(0xFF100D29),
                "stem" to c(0xFF30331E),
            ),
            mapOf("subject" to 0f, "flutes" to 14f, "refraction" to 1f, "drip" to 1f),
        ),
        WallpaperPreset(
            "cobalt", "Cobalt on Red",
            mapOf(
                "bg" to c(0xFFC20B1F), "c1" to c(0xFF2A4AA6), "c2" to c(0xFF68A0EC), "c3" to c(0xFF1B6B58),
                "stem" to c(0xFF0F4A35),
            ),
            mapOf("subject" to 1f, "flutes" to 9f, "refraction" to 1f, "drip" to 0f, "grain" to 0.25f),
        ),
        WallpaperPreset(
            "ink", "Ink",
            mapOf(
                "bg" to c(0xFFC9CAC5), "c1" to c(0xFF070707), "c2" to c(0xFF1C1C1B), "c3" to c(0xFF000000),
                "stem" to c(0xFF000000),
            ),
            mapOf("subject" to 2f, "flutes" to 17f, "refraction" to 1f, "drip" to 1f, "grain" to 0.2f),
        ),
        WallpaperPreset(
            "ember", "Ember",
            mapOf(
                "bg" to c(0xFFE8531C), "c1" to c(0xFF16257A), "c2" to c(0xFF4D8BE6), "c3" to c(0xFF1C6A5A),
                "stem" to c(0xFF10382B),
            ),
            mapOf("subject" to 1f, "flutes" to 10f, "refraction" to 1f, "drip" to 0f, "grain" to 0.25f),
        ),
        WallpaperPreset(
            "moss", "Moss",
            mapOf(
                "bg" to c(0xFFBCC4A8), "c1" to c(0xFF1F3A26), "c2" to c(0xFF7FA878), "c3" to c(0xFFF0E5A6),
                "stem" to c(0xFF1A2A1A),
            ),
            mapOf("subject" to 0f, "flutes" to 12f, "refraction" to 1f, "drip" to 0.8f),
        ),
    )

    override val shaderSource = """
        uniform float2 iResolution;
        uniform float iTime;
        uniform float iScroll;
        layout(color) uniform half4 cBg;
        layout(color) uniform half4 c1;
        layout(color) uniform half4 c2;
        layout(color) uniform half4 c3;
        layout(color) uniform half4 cStem;
        uniform float pSubject;
        uniform float pFlutes;
        uniform float pRefr;
        uniform float pGrain;
        uniform float pSway;
        uniform float pDrip;

        const float PI = 3.14159265;

        float hash11(float n) { return fract(sin(n * 12.9898) * 43758.5453); }

        float hash12(float2 p) {
            float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
            p3 += dot(p3, float3(p3.y, p3.z, p3.x) + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        float vnoise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            float a = hash12(i);
            float b = hash12(i + float2(1.0, 0.0));
            float c = hash12(i + float2(0.0, 1.0));
            float d = hash12(i + float2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }

        float smin(float a, float b, float k) {
            float h = max(k - abs(a - b), 0.0) / k;
            return min(a, b) - h * h * k * 0.25;
        }

        float ell(float2 p, float2 c, float2 r) {
            return (length((p - c) / r) - 1.0) * min(r.x, r.y);
        }

        // ---- subject 0: anemone (broad petals round a dark core) + thin stem ----
        float4 anemone(float2 p) {
            float sxm = 329.0 + 0.012 * (p.y - 765.0);
            float sd = p.x - sxm;
            float stemA = (1.0 - smoothstep(5.0, 7.0, abs(sd))) * smoothstep(640.0, 700.0, p.y);
            float3 stemC = float3(cStem.rgb) * (0.7 + 0.7 * smoothstep(-6.0, 6.0, sd));

            float2 d = p - float2(306.0, 492.0);
            float r = length(d);
            float ang = atan(d.y, d.x);
            float u = (ang + 0.32 * sin(ang * 2.0 + 1.1) + 0.18 * sin(ang * 3.0 + 4.0)) * (9.0 / 6.2831853) + 0.3;
            float n = floor(u + 0.5);
            float fu = u - n;
            float hn = hash11(n + 3.7);
            float hm = hash11(n * 1.7 + 11.3);
            float prof = 0.78 + 0.22 * sqrt(max(0.0, 1.0 - 4.0 * fu * fu));
            float wob = 0.94 + 0.12 * vnoise(p * 0.02 + 9.0);
            float rr = (232.0 + 70.0 * hn * hn + 20.0 * hm) * prof * wob * (1.0 + 0.1 * d.y / max(r, 1.0));
            float t = r / rr;
            float cover = 1.0 - smoothstep(rr - 2.0, rr + 2.0, r);

            // lilac light collects at the lower left / bottom; the upper petals stay deep indigo
            float lit = smoothstep(-20.0, 190.0, d.y - 0.45 * d.x) * (0.6 + 0.4 * hm);
            float mott = vnoise(p * 0.018 + 4.0);
            float3 deep = float3(c1.rgb);
            float3 petal = mix(deep * 0.3, deep, smoothstep(0.1, 0.65, t));
            petal = mix(petal, deep * float3(1.35, 1.2, 1.4), mott * 0.45 * smoothstep(0.35, 0.85, t));
            float3 pale = mix(float3(c2.rgb), float3(c2.rgb) * float3(1.22, 0.8, 1.06), smoothstep(30.0, 190.0, d.y));
            petal = mix(petal, pale, lit * 0.85 * smoothstep(0.38, 0.8, t));
            petal = mix(petal, float3(c2.rgb) * 0.7, smoothstep(0.9, 1.0, t) * (0.2 + 0.4 * hm));
            petal *= 1.0 + 0.1 * sin(fu * 22.0 + hn * 6.0) * smoothstep(0.2, 0.8, t);
            float gap = smoothstep(0.36, 0.5, abs(fu)) * smoothstep(70.0, 150.0, r);
            petal *= 1.0 - 0.6 * gap;

            float ring = smoothstep(44.0, 54.0, r) * (1.0 - smoothstep(78.0, 96.0, r));
            float dots = step(0.5, hash12(floor(float2(ang * 48.0, r / 7.0))));
            float3 ringC = mix(float3(c3.rgb) * 1.3, float3(0.74, 0.72, 0.86), dots * 0.85);
            ringC *= 0.55 + 0.45 * smoothstep(44.0, 70.0, r);
            float2 h = d - float2(-12.0, -16.0);
            float core = 1.0 - smoothstep(38.0, 50.0, length(d - float2(-4.0, -8.0)));
            float3 coreC = float3(c3.rgb) + float3(0.28, 0.22, 0.40) * exp(-dot(h, h) / 240.0);
            float3 fl = mix(petal, ringC, ring);
            fl = mix(fl, coreC, core);
            return float4(mix(stemC, fl, cover), max(stemA, cover));
        }

        // ---- subject 1: chrysanthemum head (layered pointed petals, wide dome) + curving stem ----
        float4 chrysanthemum(float2 p) {
            float yy = max(p.y - 548.0, 0.0);
            float s1x = 312.0 - 0.00048 * yy * yy - 0.04 * yy;
            float sl1 = -0.00096 * yy - 0.04;
            float w1 = 21.0 - 0.0045 * yy;
            float sdx = p.x - s1x;
            float stemA = (1.0 - smoothstep(w1 - 3.0, w1 + 1.5, abs(sdx) / sqrt(1.0 + sl1 * sl1))) * smoothstep(540.0, 556.0, p.y);
            float3 stemC = float3(cStem.rgb) * (0.55 + 0.9 * smoothstep(-w1, w1, sdx));

            float dx = (p.x - 312.0) / 300.0;
            float dy = (p.y - 560.0) / mix(350.0, 95.0, step(0.0, p.y - 560.0));
            float E = length(float2(dx, dy));
            float th = atan(dy, dx);
            // layer 1: 12 petals over the full circle of the ellipse space
            float u1 = th * (12.0 / 6.2831853);
            float n1 = floor(u1);
            float f1 = u1 - n1 - 0.5;
            float h1 = hash11(n1 + 5.3);
            float jag = 0.93 + 0.14 * vnoise(float2(th * 34.0, 3.0));
            float b1 = jag * (0.8 + 0.2 * h1) * (1.0 - 0.34 * abs(2.0 * f1));
            // layer 2: shifted half a sector, shorter, in front
            float u2 = th * (12.0 / 6.2831853) + 0.5;
            float n2 = floor(u2);
            float f2 = u2 - n2 - 0.5;
            float h2 = hash11(n2 + 9.1);
            float b2 = jag * (0.7 + 0.18 * h2) * (1.0 - 0.3 * abs(2.0 * f2));
            float cov1 = 1.0 - smoothstep(b1 - 0.006, b1 + 0.006, E);
            float cov2 = 1.0 - smoothstep(b2 - 0.006, b2 + 0.006, E);
            float cover = max(cov1, cov2);

            float front = step(0.001, cov2) * step(E, b2);
            float tt = E / mix(b1, b2, front);
            float fl = mix(f1, f2, front);
            float3 col = mix(float3(c1.rgb) * 0.35, float3(c1.rgb), smoothstep(0.04, 0.36, tt));
            col *= 0.85 + 0.3 * vnoise(float2(th * 46.0, tt * 7.0));
            float tipHi = smoothstep(0.28, 0.92, tt) * (0.55 + 0.45 * mix(h1, h2, front));
            col = mix(col, float3(c2.rgb), tipHi);
            float body = smoothstep(0.2, 0.5, tt);
            col *= 1.0 - 0.34 * body * pow(abs(2.0 * fl), 2.0);
            col *= 1.0 - 0.5 * body * smoothstep(0.38, 0.5, abs(fl));
            col *= mix(0.78, 1.0, front);
            float sun = smoothstep(80.0, 560.0, p.x);
            col *= 0.62 + 0.7 * sun;
            col = mix(col, float3(0.78, 0.88, 1.0), 0.3 * tipHi * sun * sun);
            col += float3(0.10, 0.16, 0.26) * sun * sun * sun * tipHi;
            col *= 1.0 - 0.45 * smoothstep(450.0, 575.0, p.y);
            col = mix(col, float3(c3.rgb), 0.55 * smoothstep(440.0, 560.0, p.y) * smoothstep(330.0, 500.0, p.x));
            return float4(mix(stemC, col, cover), max(stemA, cover));
        }

        // ---- subject 2: organic ink silhouette (smooth union of ellipses with a wobbling edge) ----
        float4 inkblob(float2 p, float tm) {
            float w = vnoise(p * 0.014 + float2(tm * 0.02, 3.0)) - 0.5;
            float2 q = p + 16.0 * float2(w, vnoise(p * 0.014 + 7.0) - 0.5);
            float dd = ell(q, float2(292.0, 478.0), float2(58.0, 92.0));
            dd = smin(dd, ell(q, float2(238.0, 576.0), float2(82.0, 50.0)), 52.0);
            dd = smin(dd, ell(q, float2(330.0, 700.0), float2(52.0, 150.0)), 40.0);
            dd = smin(dd, ell(q, float2(420.0, 1150.0), float2(150.0, 330.0)), 60.0);
            float a = 1.0 - smoothstep(-1.5, 2.5, dd);
            float3 c = mix(float3(c1.rgb), float3(c2.rgb), smoothstep(-90.0, 0.0, dd) * 0.5);
            return float4(c, a);
        }

        half4 main(float2 frag) {
            float pxk = 1380.0 / iResolution.y;
            float tm = iTime;
            float sub = floor(pSubject + 0.5);
            float Wf = iResolution.x / pFlutes;
            float xf = frag.x / Wf;
            float fi = floor(xf);
            float ff = xf - fi;
            float Wq = Wf * pxk;
            float qLeft = (0.0 - 0.5 * iResolution.x) * pxk + 310.5;
            float cq = qLeft + (fi + 0.5) * Wq;
            float qy = frag.y * pxk;

            // per-subject optics: adv = how far the window centre follows the flute, kw = window width
            // in flutes (squeeze), nl = lens nonlinearity, st/sadv = edge "stall" (the sliver pulls its
            // colour from a column nearer the middle), dp/dw = where in the flute it sits.
            float adv = 1.0;
            float kw = 1.0;
            float nl = 0.0;
            float shade = 0.05;
            float lines = 0.3;
            float dp = 0.08;
            float dw = 0.12;
            float st = 0.0;
            float sadv = 0.3;
            if (sub < 0.5) {
                adv = 0.98; kw = 1.05; nl = 0.35; shade = 0.06; lines = 0.9; dp = 0.07; dw = 0.17; st = 1.0; sadv = 0.28;
            } else if (sub < 1.5) {
                adv = 0.85; kw = 2.8; nl = 0.0; shade = 0.2; lines = 0.1; st = 0.0;
            } else {
                adv = 0.5; kw = 2.75; nl = 0.0; shade = -0.03; lines = 0.0; st = 0.0;
            }
            float r = pRefr;
            float e = clamp(1.0 - abs(ff - dp) / dw, 0.0, 1.0);
            e = e * e * (3.0 - 2.0 * e);

            float u = ff - 0.5;
            u = u * (1.0 + nl * r * 4.0 * u * u);
            float jit = (hash11(fi * 3.1 + 0.7) - 0.5) * 0.12 * r * Wq;
            float X0 = 310.0;
            float adr = mix(1.0, adv, r);
            float sx = X0 + (cq - X0) * adr + u * Wq * mix(1.0, kw, r) + jit;
            float sxs = X0 + (cq - X0) * sadv;
            sx = mix(sx, sxs, clamp(pDrip * st * e * r, 0.0, 1.0));
            float sy = qy;
            sx += pSway * (6.0 * sin(tm * 0.12) + (iScroll - 0.5) * 30.0);
            sy += pSway * 5.0 * sin(tm * 0.09 + 1.0);

            float4 sj = float4(0.0);
            if (sub < 0.5) sj = anemone(float2(sx, sy));
            else if (sub < 1.5) sj = chrysanthemum(float2(sx, sy));
            else sj = inkblob(float2(sx, sy), tm);
            float3 col = mix(float3(cBg.rgb), sj.rgb, sj.a);

            // glass: light -> dark ramp across each flute, hairlines at the ribs, fine grain
            col *= 1.0 - shade * (ff - 0.5);
            float lwd = max(1.0, 1.1 / pxk);
            float dark = 1.0 - smoothstep(0.0, lwd, ff * Wf);
            float light = 1.0 - smoothstep(0.0, lwd, (1.0 - ff) * Wf);
            col = mix(col, col * 0.5, dark * lines);
            col = mix(col, mix(col, float3(1.0), 0.5), light * lines);
            col += (hash12(floor(frag)) - 0.5) * pGrain * 0.05;
            return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
        }
    """.trimIndent()

    @RequiresApi(33)
    override fun bindUniforms(shader: RuntimeShader, frame: WallpaperFrame) {
        shader.setStandard(frame)
        shader.setFloatUniform("iScroll", frame.scroll)
        shader.setColor("cBg", frame.color("bg"))
        shader.setColor("c1", frame.color("c1"))
        shader.setColor("c2", frame.color("c2"))
        shader.setColor("c3", frame.color("c3"))
        shader.setColor("cStem", frame.color("stem"))
        shader.setFloatUniform("pSubject", frame.param("subject"))
        shader.setFloatUniform("pFlutes", frame.param("flutes"))
        shader.setFloatUniform("pRefr", frame.param("refraction"))
        shader.setFloatUniform("pGrain", frame.param("grain"))
        shader.setFloatUniform("pSway", frame.param("sway"))
        shader.setFloatUniform("pDrip", frame.param("drip"))
    }

    // ---------------------------------------------------------------------------------------------
    // Canvas fallback (API 29-32 or shader failure). The subject is baked once (CPU port of the
    // shader's subject functions, at 0.3 px per design px) into an ImageBitmap that is re-baked only
    // when size / params / colours change; every frame then just draws one upright window of it per
    // flute (the same overlapping-window lens as the shader), the light-to-dark ramp and the ribs.
    // Static: no sway; the vertical "drip" slivers are shader-only.
    // ---------------------------------------------------------------------------------------------

    private const val REF_H = 1380f
    private const val BX0 = -80f
    private const val BX1 = 700f
    private const val BSCALE = 0.3f

    private class Baked(val key: Int, val image: ImageBitmap, val ramp: Brush)

    private class Px {
        var r = 0f
        var g = 0f
        var b = 0f
        var a = 0f
    }

    override fun DrawScope.drawFallback(frame: WallpaperFrame) {
        val w = size.width
        val h = size.height
        if (w < 2f || h < 2f) return
        val sub = frame.param("subject").roundToInt().coerceIn(0, 2)
        val n = max(1, frame.param("flutes").roundToInt())
        val wf = w / n
        val shade = if (sub == 0) 0.06f else if (sub == 1) 0.2f else -0.03f
        val key = frame.layoutKey()
        var baked = frame.cache["fluted_bake"] as? Baked
        if (baked == null || baked.key != key) {
            val a = 0.5f * abs(shade)
            val left = if (shade >= 0f) Color.White.copy(alpha = a) else Color.Black.copy(alpha = a)
            val right = if (shade >= 0f) Color.Black.copy(alpha = a) else Color.White.copy(alpha = a)
            val ramp = Brush.horizontalGradient(0f to left, 1f to right, startX = 0f, endX = wf)
            baked = Baked(key, bakeSubject(frame, sub), ramp)
            frame.cache["fluted_bake"] = baked
        }
        val bk: Baked = baked
        drawRect(frame.color("bg"))

        val adv = if (sub == 0) 0.98f else if (sub == 1) 0.85f else 0.5f
        val kw = if (sub == 0) 1.05f else 2.8f
        val kwr = 1f + ((if (sub == 2) 2.75f else kw) - 1f) * frame.param("refraction")
        val adr = 1f + (adv - 1f) * frame.param("refraction")
        val pxk = REF_H / h
        val wq = wf * pxk
        val qLeft = (0f - 0.5f * w) * pxk + 310.5f
        val image = bk.image
        val hi = h.roundToInt()
        for (i in 0 until n) {
            val cq = qLeft + (i + 0.5f) * wq
            val centre = 310f + (cq - 310f) * adr
            val half = 0.5f * wq * kwr
            val bx0 = ((centre - half - BX0) * BSCALE).roundToInt().coerceIn(0, image.width - 2)
            val bx1 = ((centre + half - BX0) * BSCALE).roundToInt().coerceIn(bx0 + 1, image.width)
            val d0 = (i * wf).roundToInt()
            val d1 = ((i + 1) * wf).roundToInt()
            drawImage(
                image,
                srcOffset = IntOffset(bx0, 0),
                srcSize = IntSize(bx1 - bx0, image.height),
                dstOffset = IntOffset(d0, 0),
                dstSize = IntSize(max(1, d1 - d0), hi),
            )
            translate(left = i * wf) { drawRect(bk.ramp, size = Size(wf, h)) }
        }
        val rib = Color.Black.copy(alpha = 0.22f)
        val ribW = max(1f, 1.1f / pxk)
        for (i in 1 until n) {
            val x = floor(i * wf) + 0.5f
            drawLine(rib, Offset(x, 0f), Offset(x, h), ribW)
        }
        drawGrain(frame, frame.param("grain") * 0.6f)
    }

    private fun bakeSubject(frame: WallpaperFrame, sub: Int): ImageBitmap {
        val bw = ((BX1 - BX0) * BSCALE).roundToInt()
        val bh = (REF_H * BSCALE).roundToInt()
        val out = IntArray(bw * bh)
        val bg = frame.color("bg")
        val c1 = frame.color("c1")
        val c2 = frame.color("c2")
        val c3 = frame.color("c3")
        val st = frame.color("stem")
        val o = Px()
        for (py in 0 until bh) {
            val y = (py + 0.5f) / BSCALE
            for (pxi in 0 until bw) {
                val x = BX0 + (pxi + 0.5f) / BSCALE
                o.a = 0f
                when (sub) {
                    0 -> anemone(x, y, c1, c2, c3, st, o)
                    1 -> chrysanthemum(x, y, c1, c2, c3, st, o)
                    else -> inkblob(x, y, c1, c2, o)
                }
                val r = bg.red + (o.r - bg.red) * o.a
                val g = bg.green + (o.g - bg.green) * o.a
                val b = bg.blue + (o.b - bg.blue) * o.a
                val ri = (clamp01(r) * 255f + 0.5f).toInt()
                val gi = (clamp01(g) * 255f + 0.5f).toInt()
                val bi = (clamp01(b) * 255f + 0.5f).toInt()
                out[py * bw + pxi] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        return android.graphics.Bitmap
            .createBitmap(out, bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }

    private fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = clamp01((x - e0) / (e1 - e0))
        return t * t * (3f - 2f * t)
    }

    private fun hash11(n: Float): Float {
        val v = sin(n * 12.9898f) * 43758.5453f
        return v - floor(v)
    }

    private fun ellipse(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float): Float {
        val dx = (x - cx) / rx
        val dy = (y - cy) / ry
        return (sqrt(dx * dx + dy * dy) - 1f) * min(rx, ry)
    }

    private fun smin(a: Float, b: Float, k: Float): Float {
        val h = max(k - abs(a - b), 0f) / k
        return min(a, b) - h * h * k * 0.25f
    }

    private fun inkblob(x: Float, y: Float, c1: Color, c2: Color, o: Px) {
        var dd = ellipse(x, y, 292f, 478f, 58f, 92f)
        dd = smin(dd, ellipse(x, y, 238f, 576f, 82f, 50f), 52f)
        dd = smin(dd, ellipse(x, y, 330f, 700f, 52f, 150f), 40f)
        dd = smin(dd, ellipse(x, y, 420f, 1150f, 150f, 330f), 60f)
        val k = smoothstep(-90f, 0f, dd) * 0.5f
        o.r = c1.red + (c2.red - c1.red) * k
        o.g = c1.green + (c2.green - c1.green) * k
        o.b = c1.blue + (c2.blue - c1.blue) * k
        o.a = 1f - smoothstep(-1.5f, 2.5f, dd)
    }

    private fun anemone(x: Float, y: Float, c1: Color, c2: Color, c3: Color, st: Color, o: Px) {
        val sd = x - (329f + 0.012f * (y - 765f))
        val sk = 0.7f + 0.7f * smoothstep(-6f, 6f, sd)
        o.r = st.red * sk
        o.g = st.green * sk
        o.b = st.blue * sk
        o.a = (1f - smoothstep(5f, 7f, abs(sd))) * smoothstep(640f, 700f, y)

        val dx = x - 306f
        val dy = y - 492f
        val r = sqrt(dx * dx + dy * dy)
        val ang = atan2(dy, dx)
        val u = (ang + 0.32f * sin(ang * 2f + 1.1f) + 0.18f * sin(ang * 3f + 4f)) * (9f / 6.2831853f) + 0.3f
        val n = floor(u + 0.5f)
        val fu = u - n
        val hn = hash11(n + 3.7f)
        val hm = hash11(n * 1.7f + 11.3f)
        val prof = 0.78f + 0.22f * sqrt(max(0f, 1f - 4f * fu * fu))
        val rr = (232f + 70f * hn * hn + 20f * hm) * prof * (1f + 0.1f * dy / max(r, 1f))
        val cover = 1f - smoothstep(rr - 2f, rr + 2f, r)
        if (cover <= 0f) return
        val t = r / rr
        val lit = smoothstep(-20f, 190f, dy - 0.45f * dx) * (0.6f + 0.4f * hm)
        val dk = 0.3f + 0.7f * smoothstep(0.1f, 0.65f, t)
        val pk = smoothstep(30f, 190f, dy)
        val lm = lit * 0.85f * smoothstep(0.38f, 0.8f, t)
        val gk = 1f - 0.6f * smoothstep(0.36f, 0.5f, abs(fu)) * smoothstep(70f, 150f, r)
        var fr = (c1.red * dk + (c2.red * (1f + 0.22f * pk) - c1.red * dk) * lm) * gk
        var fg = (c1.green * dk + (c2.green * (1f - 0.2f * pk) - c1.green * dk) * lm) * gk
        var fb = (c1.blue * dk + (c2.blue * (1f + 0.06f * pk) - c1.blue * dk) * lm) * gk
        val ring = smoothstep(44f, 54f, r) * (1f - smoothstep(78f, 96f, r))
        fr += (c3.red * 1.5f + 0.2f - fr) * ring * 0.8f
        fg += (c3.green * 1.5f + 0.2f - fg) * ring * 0.8f
        fb += (c3.blue * 1.5f + 0.25f - fb) * ring * 0.8f
        val cx = x - 302f
        val cy = y - 484f
        val core = 1f - smoothstep(38f, 50f, sqrt(cx * cx + cy * cy))
        fr += (c3.red + 0.1f - fr) * core
        fg += (c3.green + 0.08f - fg) * core
        fb += (c3.blue + 0.15f - fb) * core
        o.r += (fr - o.r) * cover
        o.g += (fg - o.g) * cover
        o.b += (fb - o.b) * cover
        o.a = max(o.a, cover)
    }

    private fun chrysanthemum(x: Float, y: Float, c1: Color, c2: Color, c3: Color, st: Color, o: Px) {
        val yy = max(y - 548f, 0f)
        val s1x = 312f - 0.00048f * yy * yy - 0.04f * yy
        val sl1 = -0.00096f * yy - 0.04f
        val w1 = 21f - 0.0045f * yy
        val sdx = x - s1x
        val sk = 0.55f + 0.9f * smoothstep(-w1, w1, sdx)
        o.r = st.red * sk
        o.g = st.green * sk
        o.b = st.blue * sk
        o.a = (1f - smoothstep(w1 - 3f, w1 + 1.5f, abs(sdx) / sqrt(1f + sl1 * sl1))) * smoothstep(540f, 556f, y)

        val dx = (x - 312f) / 300f
        val dy = (y - 560f) / (if (y >= 560f) 95f else 350f)
        val e = sqrt(dx * dx + dy * dy)
        val th = atan2(dy, dx)
        val u1 = th * (12f / 6.2831853f)
        val n1 = floor(u1)
        val f1 = u1 - n1 - 0.5f
        val h1 = hash11(n1 + 5.3f)
        val b1 = (0.8f + 0.2f * h1) * (1f - 0.34f * abs(2f * f1))
        val u2 = th * (12f / 6.2831853f) + 0.5f
        val n2 = floor(u2)
        val f2 = u2 - n2 - 0.5f
        val h2 = hash11(n2 + 9.1f)
        val b2 = (0.7f + 0.18f * h2) * (1f - 0.3f * abs(2f * f2))
        val cov1 = 1f - smoothstep(b1 - 0.006f, b1 + 0.006f, e)
        val cov2 = 1f - smoothstep(b2 - 0.006f, b2 + 0.006f, e)
        val cover = max(cov1, cov2)
        if (cover <= 0f) return
        val front = if (cov2 > 0.001f && e <= b2) 1f else 0f
        val tt = e / (b1 + (b2 - b1) * front)
        val fl = f1 + (f2 - f1) * front
        val base = 0.35f + 0.65f * smoothstep(0.04f, 0.36f, tt)
        val tip = smoothstep(0.28f, 0.92f, tt) * (0.55f + 0.45f * (h1 + (h2 - h1) * front))
        var fr = c1.red * base
        var fg = c1.green * base
        var fb = c1.blue * base
        fr += (c2.red - fr) * tip
        fg += (c2.green - fg) * tip
        fb += (c2.blue - fb) * tip
        val body = smoothstep(0.2f, 0.5f, tt)
        val shade = (1f - 0.34f * body * (2f * fl) * (2f * fl)) * (1f - 0.5f * body * smoothstep(0.38f, 0.5f, abs(fl))) *
            (0.78f + 0.22f * front)
        val sun = smoothstep(80f, 560f, x)
        val lum = shade * (0.62f + 0.7f * sun) * (1f - 0.45f * smoothstep(450f, 575f, y))
        val green = 0.55f * smoothstep(440f, 560f, y) * smoothstep(330f, 500f, x)
        fr = fr * lum + (0.78f - fr * lum) * 0.3f * tip * sun * sun
        fg = fg * lum + (0.88f - fg * lum) * 0.3f * tip * sun * sun
        fb = fb * lum + (1.0f - fb * lum) * 0.3f * tip * sun * sun
        fr += (c3.red - fr) * green
        fg += (c3.green - fg) * green
        fb += (c3.blue - fb) * green
        o.r += (fr - o.r) * cover
        o.g += (fg - o.g) * cover
        o.b += (fb - o.b) * cover
        o.a = max(o.a, cover)
    }
}
