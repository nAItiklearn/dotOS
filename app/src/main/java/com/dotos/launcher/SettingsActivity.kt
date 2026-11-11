package com.dotos.launcher

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationManagerCompat
import com.dotos.core.data.db.DotDatabase
import com.dotos.core.data.settings.SettingsRepository
import com.dotos.core.launcher.AppsRepository
import com.dotos.feature.icons.engine.IconEngine
import com.dotos.feature.settings.SettingsDeps
import com.dotos.feature.settings.SettingsRoot
import com.dotos.feature.settings.SettingsScreens
import com.dotos.feature.wallpaper.service.DotWallpaperService

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val g = graph
        val deps = object : SettingsDeps {
            override val settings: SettingsRepository = g.settings
            override val apps: AppsRepository = g.apps
            override val icons: IconEngine = g.icons
            override val database: DotDatabase = g.database
            override val versionName: String = BuildConfig.VERSION_NAME

            override fun openDefaultLauncherChooser() {
                val intents = listOf(
                    Intent(Settings.ACTION_HOME_SETTINGS),
                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS),
                )
                for (i in intents) {
                    if (runCatching { startActivity(i) }.isSuccess) return
                }
            }

            override fun openNotificationAccess() {
                runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }

            override fun hasNotificationAccess(): Boolean =
                NotificationManagerCompat.getEnabledListenerPackages(this@SettingsActivity).contains(packageName)

            override fun openLiveWallpaperPicker() {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@SettingsActivity, DotWallpaperService::class.java))
                if (runCatching { startActivity(intent) }.isFailure) {
                    runCatching { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
                }
            }
        }
        val start = intent.getStringExtra(SettingsScreens.EXTRA)
        setContent {
            SettingsRoot(deps = deps, startScreen = start, onClose = { finish() })
        }
    }
}
