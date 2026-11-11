package com.dotos.feature.wallpaper.engine

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** A tunable numeric parameter, shown as a slider in the customizer. */
@Immutable
data class WallpaperParam(
    val id: String,
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float,
    /** 0 = continuous; otherwise slider snaps to this step. */
    val step: Float = 0f,
)

/** A colour slot, shown as a swatch in the customizer. Every wallpaper has a "bg" slot. */
@Immutable
data class ColorSlot(val id: String, val label: String)

@Immutable
data class WallpaperPreset(
    val id: String,
    val name: String,
    val colors: Map<String, Color>,
    val params: Map<String, Float> = emptyMap(),
)

/**
 * Per-frame input. One instance is owned by each renderer and mutated in place every frame, so
 * implementations must not keep references to it across frames.
 */
class WallpaperFrame internal constructor() {
    /** Seconds, already multiplied by the user animation speed. Constant when animation is off. */
    var time: Float = 0f
        internal set
    var width: Float = 0f
        internal set
    var height: Float = 0f
        internal set
    /** Horizontal home-page scroll, 0 = first page, 1 = last page. */
    var scroll: Float = 0f
        internal set
    /** Gyro parallax, roughly -1..1 on each axis (0 when disabled). */
    var tiltX: Float = 0f
        internal set
    var tiltY: Float = 0f
        internal set
    /** Last touch position in px, or negative when there was none. */
    var touchX: Float = -1f
        internal set
    var touchY: Float = -1f
        internal set
    /** Seconds since the last touch (large when none). */
    var touchAge: Float = 1000f
        internal set
    /** Battery level 0..1. */
    var battery: Float = 1f
        internal set
    var charging: Boolean = false
        internal set
    /** Local hour of day 0..24 (fractional). */
    var hour: Float = 12f
        internal set
    /** Density so fallbacks can size dots in dp: px = dp * density. */
    var density: Float = 1f
        internal set

    internal var hourStamp: Long = 0L
    internal var colorMap: Map<String, Color> = emptyMap()
    internal var paramMap: Map<String, Float> = emptyMap()

    /** Renderer-owned scratch space for precomputed data (dot positions, noise bitmaps). */
    val cache: MutableMap<String, Any> = HashMap()

    fun color(id: String): Color = colorMap[id] ?: Color.Magenta
    fun param(id: String): Float = paramMap[id] ?: 0f

    /** Hash of everything except time, so fallbacks can rebuild caches only when needed. */
    internal var layoutKey: Int = 0
    fun layoutKey(): Int = layoutKey
}

/**
 * A procedural wallpaper. Implementations are stateless singletons; all mutable state lives in
 * [WallpaperFrame.cache]. Both paths must look the same: the AGSL shader on API 33+, and
 * [drawFallback] (Canvas) on API 29-32 or if the shader fails to compile.
 */
interface DotWallpaper {
    val id: String
    val name: String
    val colorSlots: List<ColorSlot>
    val params: List<WallpaperParam>
    /** First preset is the default. Provide 4-6 named presets. */
    val presets: List<WallpaperPreset>
    /** False for static designs: the renderer then never schedules frames. */
    val animated: Boolean get() = true

    /**
     * AGSL source, or null to always use [drawFallback]. The shader must declare only uniforms that
     * [bindUniforms] sets; setting an undeclared uniform throws.
     */
    val shaderSource: String? get() = null

    @RequiresApi(33)
    fun bindUniforms(shader: RuntimeShader, frame: WallpaperFrame) {}

    /** Canvas path. Must not allocate per frame: precompute into [WallpaperFrame.cache]. */
    fun DrawScope.drawFallback(frame: WallpaperFrame)
}

/** Helpers for binding standard uniforms. */
@RequiresApi(33)
fun RuntimeShader.setColor(name: String, color: Color) {
    setColorUniform(name, android.graphics.Color.valueOf(color.red, color.green, color.blue, color.alpha))
}

@RequiresApi(33)
fun RuntimeShader.setStandard(frame: WallpaperFrame) {
    setFloatUniform("iResolution", frame.width, frame.height)
    setFloatUniform("iTime", frame.time)
}
