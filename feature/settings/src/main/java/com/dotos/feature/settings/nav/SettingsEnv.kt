package com.dotos.feature.settings.nav

import androidx.compose.runtime.Stable
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.feature.settings.SettingsDeps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Serialises and batches settings writes. Updates are queued without blocking; the writer drains
 * everything queued so far into ONE DataStore edit, so dragging a slider never causes a write storm
 * and no update is ever dropped. The scope is independent of any composition, so a write queued just
 * before the screen closes still lands (call [close] to stop accepting new updates and let the
 * queue drain).
 */
@Stable
internal class SettingsController(private val deps: SettingsDeps) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = Channel<(LauncherSettings) -> LauncherSettings>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (first in queue) {
                val batch = ArrayList<(LauncherSettings) -> LauncherSettings>()
                batch.add(first)
                while (true) {
                    val next = queue.tryReceive().getOrNull() ?: break
                    batch.add(next)
                }
                try {
                    deps.settings.update { current -> batch.fold(current) { acc, f -> f(acc) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failed write must never take the settings screen down.
                }
            }
        }
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        queue.trySend(transform)
    }

    fun close() {
        queue.close()
    }
}

/** Everything a settings screen needs: dependencies, writes and navigation. */
@Stable
internal class SettingsEnv(
    val deps: SettingsDeps,
    val controller: SettingsController,
    val navigate: (String) -> Unit,
    val back: () -> Unit,
    val close: () -> Unit,
) {
    fun update(transform: (LauncherSettings) -> LauncherSettings) = controller.update(transform)

    fun updateWallpaper(transform: (WallpaperConfig) -> WallpaperConfig) =
        controller.update { it.copy(wallpaper = transform(it.wallpaper)) }

    /** Fire-and-forget work on the IO dispatcher that survives leaving the current screen. */
    fun launchIO(block: suspend CoroutineScope.() -> Unit): Job = controller.scope.launch(Dispatchers.IO, block = block)
}
