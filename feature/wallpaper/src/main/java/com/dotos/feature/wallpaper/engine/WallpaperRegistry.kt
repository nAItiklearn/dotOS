package com.dotos.feature.wallpaper.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.feature.wallpaper.wallpapers.AllWallpapers
import com.dotos.feature.wallpaper.wallpapers.Atmosphere

object WallpaperRegistry {
    val all: List<DotWallpaper> by lazy { AllWallpapers.list }

    operator fun get(id: String): DotWallpaper = all.firstOrNull { it.id == id } ?: Atmosphere
}

/** Config resolved against the wallpaper's preset and defaults. */
class ResolvedWallpaper(
    val wallpaper: DotWallpaper,
    val preset: WallpaperPreset,
    val colors: Map<String, Color>,
    val params: Map<String, Float>,
) {
    /** True when the wallpaper is light enough that content on top should be dark. */
    val isLight: Boolean get() = (colors["bg"] ?: Color.Black).luminance() > 0.5f
}

fun WallpaperConfig.resolve(): ResolvedWallpaper {
    val wp = WallpaperRegistry[id]
    val preset = wp.presets.firstOrNull { it.id == presetId } ?: wp.presets.first()
    val colors = HashMap<String, Color>()
    wp.colorSlots.forEach { slot -> colors[slot.id] = preset.colors[slot.id] ?: Color.Gray }
    this.colors.forEach { (k, v) -> colors[k] = Color(v.toInt()) }
    val params = HashMap<String, Float>()
    wp.params.forEach { p -> params[p.id] = (this.params[p.id] ?: preset.params[p.id] ?: p.default).coerceIn(p.min, p.max) }
    return ResolvedWallpaper(wp, preset, colors, params)
}
