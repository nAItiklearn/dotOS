package com.dotos.feature.wallpaper.wallpapers

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dotos.feature.wallpaper.engine.ColorSlot
import com.dotos.feature.wallpaper.engine.DotWallpaper
import com.dotos.feature.wallpaper.engine.WallpaperFrame
import com.dotos.feature.wallpaper.engine.WallpaperParam
import com.dotos.feature.wallpaper.engine.WallpaperPreset
import com.dotos.feature.wallpaper.engine.setColor
import com.dotos.feature.wallpaper.engine.setStandard
import com.dotos.feature.wallpaper.wallpapers.Noise.drawGrain
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Atmosphere: a soft, drifting mesh of blurred colour light with fine static film grain and a
 * gentle vignette. Four light sources orbit slowly on independent Lissajous paths over a base
 * colour; their weights are blended as a normalised field (no hard edges, no banding thanks to
 * grain). Optional time-of-day tint warms the scene at dusk and cools it at night.
 */
object Atmosphere : DotWallpaper {
    override val id = "atmosphere"
    override val name = "Atmosphere"

    override val colorSlots = listOf(
        ColorSlot("bg", "Base"),
        ColorSlot("c1", "Light 1"),
        ColorSlot("c2", "Light 2"),
        ColorSlot("c3", "Light 3"),
        ColorSlot("c4", "Light 4"),
    )

    override val params = listOf(
        WallpaperParam("glow", "Glow", 0f, 2f, 1.35f),
        WallpaperParam("softness", "Softness", 0.15f, 0.9f, 0.34f),
        WallpaperParam("grain", "Grain", 0f, 1f, 0.45f),
        WallpaperParam("vignette", "Vignette", 0f, 1f, 0.35f),
        WallpaperParam("drift", "Drift", 0f, 2f, 1f),
        WallpaperParam("tod", "Time-of-day tint", 0f, 1f, 0f),
    )

    private fun c(hex: Long) = Color(hex)

    override val presets = listOf(
        WallpaperPreset(
            "paper", "Paper",
            mapOf("bg" to c(0xFFB9B1A6), "c1" to c(0xFFE7E2DA), "c2" to c(0xFF8E857A), "c3" to c(0xFFD6CEC2), "c4" to c(0xFF5F574E)),
        ),
        WallpaperPreset(
            "mocha", "Mocha",
            mapOf("bg" to c(0xFF231C18), "c1" to c(0xFF6A5748), "c2" to c(0xFF9A8472), "c3" to c(0xFF120E0C), "c4" to c(0xFF46372D)),
            mapOf("vignette" to 0.55f),
        ),
        WallpaperPreset(
            "midnight", "Midnight",
            mapOf("bg" to c(0xFF050506), "c1" to c(0xFF2C2C33), "c2" to c(0xFF16161B), "c3" to c(0xFF3D3D47), "c4" to c(0xFF0B0B0E)),
            mapOf("vignette" to 0.6f, "glow" to 0.9f),
        ),
        WallpaperPreset(
            "ember", "Ember",
            mapOf("bg" to c(0xFF140804), "c1" to c(0xFFD9541E), "c2" to c(0xFFF29055), "c3" to c(0xFF5E1D08), "c4" to c(0xFF2A0F06)),
            mapOf("vignette" to 0.5f),
        ),
        WallpaperPreset(
            "frost", "Frost",
            mapOf("bg" to c(0xFFC4D1DB), "c1" to c(0xFFF0F4F7), "c2" to c(0xFF8AA3B8), "c3" to c(0xFFDDE6ED), "c4" to c(0xFF5B7891)),
        ),
        WallpaperPreset(
            "signal", "Signal Red",
            mapOf("bg" to c(0xFF090909), "c1" to c(0xFFD71921), "c2" to c(0xFF3A0508), "c3" to c(0xFF1C1C1C), "c4" to c(0xFF7A0C11)),
            mapOf("glow" to 0.85f, "vignette" to 0.6f),
        ),
        WallpaperPreset(
            "sage", "Sage",
            mapOf("bg" to c(0xFF9CA48F), "c1" to c(0xFFD8DCCD), "c2" to c(0xFF6B7560), "c3" to c(0xFFB9C0AA), "c4" to c(0xFF454D3D)),
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
        layout(color) uniform half4 c4;
        uniform float pGlow;
        uniform float pSoft;
        uniform float pGrain;
        uniform float pVignette;
        uniform float pDrift;
        uniform float pTod;
        uniform float pHour;

        float hash(float2 p) {
            p = fract(p * float2(443.897, 441.423));
            p += dot(p, p.yx + 19.19);
            return fract((p.x + p.y) * p.x);
        }

        float blob(float2 uv, float2 c, float s) {
            float2 d = uv - c;
            return exp(-dot(d, d) / (s * s));
        }

        half4 main(float2 frag) {
            float aspect = iResolution.x / iResolution.y;
            float2 uv = frag / iResolution.y;
            uv.x += iScroll * 0.06 * aspect;
            float t = iTime * 0.07 * pDrift;

            // Gentle domain warp so blobs breathe organically instead of sliding.
            uv += 0.035 * float2(sin(uv.y * 3.1 + t * 1.7), cos(uv.x * 2.7 - t * 1.3));

            float2 p1 = float2(aspect * (0.28 + 0.20 * sin(t * 1.00 + 0.0)), 0.22 + 0.10 * cos(t * 0.80));
            float2 p2 = float2(aspect * (0.78 + 0.16 * cos(t * 0.70 + 1.3)), 0.48 + 0.16 * sin(t * 0.90 + 0.4));
            float2 p3 = float2(aspect * (0.42 + 0.22 * sin(t * 0.55 + 2.1)), 0.80 + 0.10 * cos(t * 0.65 + 1.0));
            float2 p4 = float2(aspect * (0.62 + 0.18 * cos(t * 0.45 + 3.7)), 0.05 + 0.95 * (0.5 + 0.5 * sin(t * 0.33)));

            float s = pSoft;
            float w1 = blob(uv, p1, s * 1.10) * pGlow;
            float w2 = blob(uv, p2, s * 0.95) * pGlow;
            float w3 = blob(uv, p3, s * 1.25) * pGlow;
            float w4 = blob(uv, p4, s * 0.85) * pGlow * 0.9;
            float wb = 0.16;

            half3 col = (cBg.rgb * wb + c1.rgb * w1 + c2.rgb * w2 + c3.rgb * w3 + c4.rgb * w4) / (wb + w1 + w2 + w3 + w4);

            // Optional time-of-day tint: warm at dusk/dawn, cool at night.
            float dusk = exp(-pow((pHour - 19.0) / 2.0, 2.0)) + exp(-pow((pHour - 6.5) / 1.6, 2.0));
            float night = smoothstep(21.0, 24.0, pHour) + 1.0 - smoothstep(0.0, 5.5, pHour);
            col = mix(col, col * half3(1.10, 0.96, 0.86), half(dusk * pTod * 0.6));
            col = mix(col, col * half3(0.82, 0.86, 1.02), half(clamp(night, 0.0, 1.0) * pTod * 0.6));

            float2 q = frag / iResolution - 0.5;
            float v = smoothstep(0.35, 0.95, length(q * float2(1.0, 0.9)) * 1.25);
            col *= half(1.0 - pVignette * 0.55 * v);

            // Static grain (does not shimmer) + doubles as dithering against banding.
            float g = hash(floor(frag)) - 0.5;
            col += half3(g * pGrain * 0.075);
            return half4(clamp(col, 0.0, 1.0), 1.0);
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
        shader.setColor("c4", frame.color("c4"))
        shader.setFloatUniform("pGlow", frame.param("glow"))
        shader.setFloatUniform("pSoft", frame.param("softness"))
        shader.setFloatUniform("pGrain", frame.param("grain"))
        shader.setFloatUniform("pVignette", frame.param("vignette"))
        shader.setFloatUniform("pDrift", frame.param("drift"))
        shader.setFloatUniform("pTod", frame.param("tod"))
        shader.setFloatUniform("pHour", frame.hour)
    }

    override fun DrawScope.drawFallback(frame: WallpaperFrame) {
        val w = size.width
        val h = size.height
        val aspect = w / h
        val t = frame.time * 0.07f * frame.param("drift")
        val soft = frame.param("softness")
        val glow = frame.param("glow").coerceIn(0f, 2f)
        drawRect(frame.color("bg"))
        val sx = -frame.scroll * 0.06f * aspect * h
        fun blob(color: Color, ux: Float, uy: Float, s: Float, strength: Float) {
            val center = Offset(ux * h + sx, uy * h)
            val radius = max(1f, s * h * 1.9f)
            val a = (strength * glow * 0.85f).coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = a),
                    0.45f to color.copy(alpha = a * 0.55f),
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        blob(frame.color("c4"), aspect * (0.62f + 0.18f * cos(t * 0.45f + 3.7f)), 0.05f + 0.95f * (0.5f + 0.5f * sin(t * 0.33f)), soft * 0.85f, 0.9f)
        blob(frame.color("c3"), aspect * (0.42f + 0.22f * sin(t * 0.55f + 2.1f)), 0.80f + 0.10f * cos(t * 0.65f + 1f), soft * 1.25f, 1f)
        blob(frame.color("c2"), aspect * (0.78f + 0.16f * cos(t * 0.70f + 1.3f)), 0.48f + 0.16f * sin(t * 0.90f + 0.4f), soft * 0.95f, 1f)
        blob(frame.color("c1"), aspect * (0.28f + 0.20f * sin(t)), 0.22f + 0.10f * cos(t * 0.8f), soft * 1.1f, 1f)
        val vig = frame.param("vignette")
        if (vig > 0.01f) {
            drawRect(
                Brush.radialGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.5f * vig),
                    center = Offset(w / 2f, h / 2f),
                    radius = max(w, h) * 0.75f,
                ),
            )
        }
        drawGrain(frame, frame.param("grain"))
    }
}
