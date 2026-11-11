package com.dotos.feature.wallpaper.wallpapers

import com.dotos.feature.wallpaper.engine.DotWallpaper

/**
 * Procedural recreations of the fluted-glass / x-ray / grain reference looks. The maintainer appends
 * [list] to AllWallpapers (kept separate so two people can work on the registry without conflicts).
 */
object ReferenceWallpapers {
    val list: List<DotWallpaper> = listOf(
        FlutedBloom,
        XrayLeaves,
        GrainTotem,
    )
}
