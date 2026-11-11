package com.dotos.feature.settings

import com.dotos.core.data.db.DotDatabase
import com.dotos.core.data.settings.SettingsRepository
import com.dotos.core.launcher.AppsRepository
import com.dotos.feature.icons.engine.IconEngine

/** Everything the settings UI needs, provided by the app module (no DI framework). */
interface SettingsDeps {
    val settings: SettingsRepository
    val apps: AppsRepository
    val icons: IconEngine
    val database: DotDatabase

    /** Opens the system "Default home app" chooser. */
    fun openDefaultLauncherChooser()

    /** Opens notification-listener access settings for badges / music. */
    fun openNotificationAccess()
    fun hasNotificationAccess(): Boolean

    /** Applies the live wallpaper service system-wide (opens the system preview). */
    fun openLiveWallpaperPicker()

    /** Version name for About. */
    val versionName: String
}

/** Deep-link targets so the home edit sheet can open a specific screen. */
object SettingsScreens {
    const val EXTRA = "dotos.settings.screen"
    const val HOME_CUSTOMIZER = "customizer"
    const val WALLPAPERS = "wallpapers"
    const val ICONS = "icons"
    const val LAYOUT = "layout"
    const val COLOURS = "colours"
    const val WIDGETS = "widgets"
    const val FIRST_RUN = "first_run"
}
