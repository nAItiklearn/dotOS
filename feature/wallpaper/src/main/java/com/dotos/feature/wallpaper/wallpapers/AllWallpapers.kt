package com.dotos.feature.wallpaper.wallpapers

import com.dotos.feature.wallpaper.engine.DotWallpaper

/** Every bundled wallpaper, in picker order. Atmosphere first (it is the default). */
object AllWallpapers {
    val list: List<DotWallpaper> = listOf(
        Atmosphere,
        DotField,
        HalftoneOrb,
        DotWave,
        Orbit,
        GradientDither,
        MatrixRain,
        ContourMap,
        Pulse,
        GlyphBurst,
    )
}
