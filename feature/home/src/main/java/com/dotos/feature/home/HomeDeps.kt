package com.dotos.feature.home

import android.content.ComponentName
import com.dotos.core.data.db.DotDatabase
import com.dotos.core.data.settings.SettingsRepository
import com.dotos.core.launcher.AppsRepository
import com.dotos.feature.home.layout.LayoutRepository
import com.dotos.feature.icons.engine.IconEngine
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Everything the home screen needs from the app module. */
interface HomeDeps {
    val settings: SettingsRepository
    val apps: AppsRepository
    val icons: IconEngine
    val layout: LayoutRepository
    val database: DotDatabase
    /** Notification counts per package (empty unless the listener is enabled). */
    val badges: StateFlow<Map<String, Int>>
    /** Emits when HOME is pressed while the launcher is already in front. */
    val homePresses: SharedFlow<Unit>
    /** True after repeated startup crashes: built-in widgets render as placeholders. */
    val safeMode: Boolean
    val notificationListener: ComponentName

    fun openSettings(screen: String? = null)
    /** Returns false when no lock method is set up (caller explains how to enable it). */
    fun lockScreen(): Boolean
    fun hasNotificationAccess(): Boolean
    fun requestNotificationAccess()
}
