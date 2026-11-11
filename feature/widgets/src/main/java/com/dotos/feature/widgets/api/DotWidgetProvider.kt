package com.dotos.feature.widgets.api

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Size in home-grid cells. */
@Immutable
data class WidgetSize(val spanX: Int, val spanY: Int) {
    override fun toString() = "${spanX}×$spanY"
}

/** Visual variant (clock faces etc.). */
@Immutable
data class WidgetFace(val id: String, val name: String, val description: String = "")

/** One placed widget. [itemId] is -1 for picker previews: previews must have no side effects. */
@Immutable
data class WidgetInstance(
    val itemId: Long,
    val kind: String,
    val face: String,
    val spanX: Int,
    val spanY: Int,
    val config: Map<String, String> = emptyMap(),
) {
    val isPreview: Boolean get() = itemId < 0
    /** Stable key prefix for [WidgetHostServices.state]. */
    val stateKey: String get() = "w$itemId"
}

/** Shape the launcher clips the widget to. Content draws inside; the launcher draws the surface. */
enum class WidgetShape { ROUNDED, PILL, CIRCLE }

/**
 * A built-in DotOS widget. Implementations are stateless singletons; persistent state goes through
 * [LocalWidgetHost]. The launcher draws the background surface (colour, radius, border) from
 * [LocalWidgetStyle]; [Content] draws only what is inside and must fill the given modifier.
 */
interface DotWidgetProvider {
    val kind: String
    val title: String
    val description: String
    val sizes: List<WidgetSize>
    val faces: List<WidgetFace> get() = listOf(WidgetFace("default", title))
    /** Runtime permissions the widget needs before it can work (asked on first use, never in preview). */
    val permissions: List<String> get() = emptyList()
    /** Surface shape for a given size, e.g. PILL for 2x1 recorders, CIRCLE for a 1x1 compass. */
    fun shape(size: WidgetSize): WidgetShape = WidgetShape.ROUNDED

    @Composable
    fun Content(instance: WidgetInstance, modifier: Modifier)
}

/** Colours and geometry every widget should use (user adjustable). */
@Immutable
data class WidgetStyle(
    val background: Color = Color(0xF21B1B1B),
    val content: Color = Color.White,
    val muted: Color = Color(0xFF8A8A8A),
    val accent: Color = Color(0xFFD71921),
    val track: Color = Color(0xFF2E2E2E),
    val radius: Dp = 28.dp,
    /** Dot density multiplier for dot-matrix renderers (1 = default). */
    val dotDensity: Float = 1f,
)

val LocalWidgetStyle = staticCompositionLocalOf { WidgetStyle() }

/**
 * Services the launcher provides to widgets. All suspend functions are safe to call from
 * LaunchedEffect. Every method has a harmless default so previews work without a host.
 */
interface WidgetHostServices {
    /** Observe a persisted string value (null if unset). Keys should start with instance.stateKey. */
    fun state(key: String): Flow<String?> = emptyFlow()
    suspend fun putState(key: String, value: String) {}

    /** Ask for a runtime permission; [onResult] gets true when granted. */
    fun requestPermission(permission: String, onResult: (Boolean) -> Unit) = onResult(false)
    fun hasPermission(permission: String): Boolean = false

    /** Launch an activity intent (adds NEW_TASK as needed). */
    fun startActivity(intent: Intent) {}

    /** System Photo Picker (no storage permission). Returns a launcher-private copy of the image. */
    fun pickImage(onPicked: (Uri?) -> Unit) = onPicked(null)

    /** Opens the launcher search overlay. */
    fun openSearch() {}

    /** Asks the launcher to show this widget's face/options sheet. */
    fun openOptions(instance: WidgetInstance) {}

    /** Persist a new config map for the instance (face changes etc.). */
    suspend fun updateConfig(instance: WidgetInstance, config: Map<String, String>) {}

    /** Notification-listener access state (needed for music sessions). */
    fun hasNotificationAccess(): Boolean = false
    fun requestNotificationAccess() {}

    /** The launcher's NotificationListenerService component, for MediaSessionManager.getActiveSessions. */
    val notificationListener: android.content.ComponentName? get() = null
}

val LocalWidgetHost = staticCompositionLocalOf<WidgetHostServices> { object : WidgetHostServices {} }
