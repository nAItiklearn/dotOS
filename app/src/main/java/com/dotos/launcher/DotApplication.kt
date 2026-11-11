package com.dotos.launcher

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.feature.wallpaper.service.WallpaperConfigSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class DotApplication : Application(), WallpaperConfigSource {
    override val wallpaperConfig: Flow<WallpaperConfig>
        get() = graph.settings.settingsOrNull.filterNotNull().map { it.wallpaper }.distinctUntilChanged()

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val safe = CrashGuard.install(this)
        graph = AppGraph(this, safe)
        // Start loading settings + the app list immediately so the first frame has data.
        graph.settings.settingsOrNull
        graph.apps.start()
        Handler(Looper.getMainLooper()).postDelayed({ CrashGuard.markHealthy(this) }, 60_000L)
    }
}

val android.content.Context.graph: AppGraph get() = (applicationContext as DotApplication).graph
