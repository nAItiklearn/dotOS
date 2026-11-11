package com.dotos.launcher

import android.app.Application
import com.dotos.core.data.db.DotDatabase
import com.dotos.core.data.settings.SettingsRepository
import com.dotos.core.launcher.AppsRepository
import com.dotos.feature.icons.engine.IconEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-written dependency graph. Deliberately no DI framework: zero reflection, zero codegen and
 * no startup cost, which keeps cold start fast. Everything heavy is lazy.
 */
class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(app, scope)
    val database: DotDatabase by lazy { DotDatabase.create(app) }
    val apps = AppsRepository(app, scope)
    val icons = IconEngine(app, scope)
}
