package com.dotos.feature.settings.ui

import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.feature.wallpaper.engine.DotWallpaper
import com.dotos.feature.wallpaper.engine.WallpaperPreset
import com.dotos.feature.wallpaper.engine.WallpaperRegistry
import kotlin.math.roundToInt
import kotlin.random.Random

/** Id used by the photo wallpaper (see WallpaperLayer). */
internal const val IMAGE_WALLPAPER_ID = "image"

/** Colours map reduced to the icon tone (wallpaper colour slots are dropped). */
internal fun WallpaperConfig.toneOnly(): Map<String, Long> = colors.filterKeys { it == TONE_KEY }

/** Switches to [wp] with its default preset, keeping effects (glass, parallax...) and the photo path. */
internal fun WallpaperConfig.withWallpaper(wp: DotWallpaper): WallpaperConfig = copy(
    id = wp.id,
    presetId = wp.presets.first().id,
    params = emptyMap(),
    colors = toneOnly(),
)

internal fun WallpaperConfig.withPreset(preset: WallpaperPreset): WallpaperConfig = copy(
    presetId = preset.id,
    params = emptyMap(),
    colors = toneOnly(),
)

/** A random preset with every numeric param jittered inside its allowed range. */
internal fun randomized(config: WallpaperConfig, random: Random = Random.Default): WallpaperConfig {
    val wp = WallpaperRegistry[config.id]
    val preset = wp.presets.random(random)
    val params = HashMap<String, Float>()
    for (p in wp.params) {
        val base = preset.params[p.id] ?: p.default
        val span = p.max - p.min
        var v = (base + (random.nextFloat() - 0.5f) * 0.5f * span).coerceIn(p.min, p.max)
        if (p.step > 0f) {
            v = (p.min + ((v - p.min) / p.step).roundToInt() * p.step).coerceIn(p.min, p.max)
        }
        params[p.id] = v
    }
    return config.copy(presetId = preset.id, params = params, colors = config.toneOnly())
}

/** Gallery section for a wallpaper id; unknown ids land in "More". */
internal fun gallerySection(id: String): String = when {
    id.startsWith("atmosphere") || id.startsWith("aurora") || id.startsWith("haze") -> "Atmosphere"
    id.startsWith("glass") || id.startsWith("grain") || id.startsWith("gradient") || id.startsWith("dither") -> "Glass & Grain"
    id.startsWith("dot") || id.startsWith("halftone") || id.startsWith("orbit") || id.startsWith("matrix") ||
        id.startsWith("contour") || id.startsWith("pulse") || id.startsWith("glyph") || id.startsWith("wave") -> "Dot"
    else -> "More"
}

internal val GallerySectionOrder = listOf("Atmosphere", "Dot", "Glass & Grain", "More")
