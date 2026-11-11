package com.dotos.feature.wallpaper.engine

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/**
 * Fluted / ribbed glass: the content is split into vertical flutes. Each flute samples a horizontally
 * compressed and mirrored slice of what is behind it (like a cylindrical lens), with a soft specular
 * highlight on one edge and shade on the other.
 */
@RequiresApi(33)
object GlassEffect {
    private const val SRC = """
        uniform shader content;
        uniform float2 iResolution;
        uniform float stripes;
        uniform float strength;

        half4 main(float2 p) {
            float w = iResolution.x / stripes;
            float i = floor(p.x / w);
            float f = fract(p.x / w);
            // Cylindrical lens: centre magnified, edges compressed, image flipped across the flute.
            float lens = (0.5 - f);
            float x = (i + 0.5) * w + lens * w * (1.0 + strength * 1.6) ;
            x = clamp(x, 0.0, iResolution.x - 1.0);
            // Slight vertical stretch toward the flute centre for depth.
            float y = p.y + (0.5 - abs(lens)) * 6.0 * strength;
            half4 c = content.eval(float2(x, y));
            // Highlight on the left edge, shade on the right edge of every flute.
            float hi = smoothstep(0.0, 0.18, f) * (1.0 - smoothstep(0.18, 0.42, f));
            float sh = smoothstep(0.62, 1.0, f);
            c.rgb += half3(hi * 0.07 * strength);
            c.rgb *= half(1.0 - sh * 0.16 * strength);
            return c;
        }
    """

    fun create(width: Float, height: Float, stripes: Float, strength: Float = 1f): RenderEffect? = runCatching {
        val shader = RuntimeShader(SRC)
        shader.setFloatUniform("iResolution", width, height)
        shader.setFloatUniform("stripes", stripes.coerceIn(4f, 80f))
        shader.setFloatUniform("strength", strength)
        RenderEffect.createRuntimeShaderEffect(shader, "content")
    }.getOrNull()
}
