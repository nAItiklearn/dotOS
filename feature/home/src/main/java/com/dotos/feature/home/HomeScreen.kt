package com.dotos.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.feature.wallpaper.engine.WallpaperLayer

/** Temporary skeleton: wallpaper only. Replaced by the full home in the next commit. */
@Composable
fun HomeScreen(settings: LauncherSettings, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        WallpaperLayer(settings.wallpaper)
    }
}
