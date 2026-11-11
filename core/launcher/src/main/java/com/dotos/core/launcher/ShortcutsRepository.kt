package com.dotos.core.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppShortcut(
    val id: String,
    val packageName: String,
    val user: UserHandle,
    val shortLabel: String,
    val isPinned: Boolean,
    internal val info: ShortcutInfo,
)

/** Static + dynamic app shortcuts. Requires being the default launcher (shortcut host). */
class ShortcutsRepository(private val context: Context, private val launcherApps: LauncherApps) {
    fun hasPermission(): Boolean = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    suspend fun forApp(app: AppInfo): List<AppShortcut> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(app.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        runCatching { launcherApps.getShortcuts(query, app.user).orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(5)
            .map { AppShortcut(it.id, it.`package`, it.userHandle, (it.shortLabel ?: it.longLabel ?: "").toString(), it.isPinned, it) }
    }

    fun start(shortcut: AppShortcut, bounds: Rect? = null) {
        runCatching { launcherApps.startShortcut(shortcut.info, bounds, null) }
    }

    fun start(packageName: String, id: String, user: UserHandle, bounds: Rect? = null) {
        runCatching { launcherApps.startShortcut(packageName, id, bounds, null, user) }
    }

    fun icon(shortcut: AppShortcut, density: Int): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(shortcut.info, density) }.getOrNull()
}
