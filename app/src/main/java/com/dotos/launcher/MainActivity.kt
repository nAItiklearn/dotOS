package com.dotos.launcher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotos.core.data.db.DotDatabase
import com.dotos.core.data.settings.SettingsRepository
import com.dotos.core.data.settings.ThemeMode
import com.dotos.core.designsystem.DotTheme
import com.dotos.core.launcher.AppsRepository
import com.dotos.feature.home.HomeDeps
import com.dotos.feature.home.HomeScreen
import com.dotos.feature.home.layout.LayoutRepository
import com.dotos.feature.home.widgets.AppWidgetController
import com.dotos.feature.icons.engine.IconEngine
import com.dotos.feature.icons.engine.color
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var appWidgets: AppWidgetController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val g = graph
        appWidgets = AppWidgetController(applicationContext)
        // Automated screenshot runs skip the first-run flow.
        if (intent?.getBooleanExtra("dotos.demo", false) == true) {
            g.scope.launch { g.settings.update { it.copy(firstRunDone = true) } }
        }
        // Screenshot runs: lay out every built-in widget on extra pages for visual review.
        if (intent?.getBooleanExtra("dotos.demoWidgets", false) == true) {
            g.scope.launch { DemoLayout.placeAllWidgets(g) }
        }
        val deps = object : HomeDeps {
            override val settings: SettingsRepository = g.settings
            override val apps: AppsRepository = g.apps
            override val icons: IconEngine = g.icons
            override val layout: LayoutRepository = g.layout
            override val database: DotDatabase = g.database
            override val badges: StateFlow<Map<String, Int>> = DotNotificationListener.badges
            override val homePresses: SharedFlow<Unit> = g.homePresses
            override val safeMode: Boolean = g.safeMode
            override val notificationListener = g.notificationListener

            override fun openSettings(screen: String?) {
                startActivity(
                    Intent(this@MainActivity, SettingsActivity::class.java)
                        .putExtra(com.dotos.feature.settings.SettingsScreens.EXTRA, screen),
                )
            }

            override fun lockScreen(): Boolean {
                if (DotAccessibilityService.lock()) return true
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                return false
            }

            override fun hasNotificationAccess(): Boolean =
                NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity).contains(packageName)

            override fun requestNotificationAccess() {
                runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        }
        setContent {
            val settings by g.settings.settingsOrNull.collectAsStateWithLifecycle()
            val s = settings
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (s?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.AUTO -> systemDark
                else -> true
            }
            DotTheme(
                dark = dark,
                accent = (s?.accent ?: com.dotos.core.data.settings.Accent.RED).color(),
                useDotDisplayFont = s?.dotDisplayFont ?: true,
                reduceMotion = s?.reduceMotion ?: false,
                hapticsEnabled = s?.haptics ?: true,
            ) {
                if (s == null) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                } else {
                    LaunchedEffect(s.allowRotation) {
                        requestedOrientation = if (s.allowRotation) {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                    LaunchedEffect(s.showStatusBar) {
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        if (s.showStatusBar) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
                    }
                    LaunchedEffect(s.firstRunDone) {
                        if (!s.firstRunDone) deps.openSettings(com.dotos.feature.settings.SettingsScreens.FIRST_RUN)
                    }
                    HomeScreen(deps = deps, settings = s, appWidgets = appWidgets)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgets.start()
    }

    override fun onStop() {
        super.onStop()
        appWidgets.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // HOME pressed while we are already the front activity.
        // (When returning from another app, onNewIntent runs before onResume, so we are not RESUMED yet.)
        val alreadyInFront = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        if (alreadyInFront && Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_HOME)) {
            graph.homePresses.tryEmit(Unit)
        }
    }
}
