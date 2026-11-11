package com.dotos.launcher

import android.app.Application

class DotApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        // Start loading settings + the app list immediately so the first frame has data.
        graph.settings.settingsOrNull
        graph.apps.start()
    }
}

val android.content.Context.graph: AppGraph get() = (applicationContext as DotApplication).graph
