package com.dotos.feature.wallpaper.service

import com.dotos.core.data.settings.WallpaperConfig
import kotlinx.coroutines.flow.Flow

/** Implemented by the Application so the service can read the current wallpaper without DI. */
interface WallpaperConfigSource {
    val wallpaperConfig: Flow<WallpaperConfig>
}
