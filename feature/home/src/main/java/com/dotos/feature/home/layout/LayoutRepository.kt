package com.dotos.feature.home.layout

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Telephony
import com.dotos.core.data.db.Container
import com.dotos.core.data.db.DotDatabase
import com.dotos.core.data.db.ItemEntity
import com.dotos.core.data.db.ItemType
import com.dotos.core.launcher.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** All home/dock/folder layout mutations. Every write is serialised through one mutex. */
class LayoutRepository(private val context: Context, private val db: DotDatabase, scope: CoroutineScope) {
    private val dao = db.items()
    private val mutex = Mutex()

    /** null until loaded. */
    val items: StateFlow<List<ItemEntity>?> = dao.observeAll().stateIn(scope, SharingStarted.Eagerly, null)

    private suspend fun <T> write(block: suspend () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }

    // ---------------------------------------------------------------- queries

    fun occupancy(all: List<ItemEntity>, screen: Int, cols: Int, rows: Int, excludeId: Long = -1): Array<BooleanArray> {
        val grid = Array(rows) { BooleanArray(cols) }
        for (it in all) {
            if (it.container != Container.HOME || it.screen != screen || it.id == excludeId) continue
            for (y in it.cellY until (it.cellY + it.spanY).coerceAtMost(rows)) {
                for (x in it.cellX until (it.cellX + it.spanX).coerceAtMost(cols)) {
                    if (y >= 0 && x >= 0) grid[y][x] = true
                }
            }
        }
        return grid
    }

    fun fits(grid: Array<BooleanArray>, x: Int, y: Int, spanX: Int, spanY: Int): Boolean {
        val rows = grid.size
        val cols = if (rows == 0) 0 else grid[0].size
        if (x < 0 || y < 0 || x + spanX > cols || y + spanY > rows) return false
        for (yy in y until y + spanY) for (xx in x until x + spanX) if (grid[yy][xx]) return false
        return true
    }

    /** Nearest free top-left for a span, searching outward from (prefX, prefY). */
    fun nearestFree(grid: Array<BooleanArray>, spanX: Int, spanY: Int, prefX: Int = 0, prefY: Int = 0): Pair<Int, Int>? {
        val rows = grid.size
        val cols = if (rows == 0) 0 else grid[0].size
        var best: Pair<Int, Int>? = null
        var bestD = Int.MAX_VALUE
        for (y in 0..rows - spanY) for (x in 0..cols - spanX) {
            if (!fits(grid, x, y, spanX, spanY)) continue
            val d = (x - prefX) * (x - prefX) + (y - prefY) * (y - prefY)
            if (d < bestD) {
                bestD = d
                best = x to y
            }
        }
        return best
    }

    // ---------------------------------------------------------------- mutations

    suspend fun moveOnHome(id: Long, screen: Int, x: Int, y: Int) = write {
        val item = dao.byId(id) ?: return@write
        val wasInFolder = item.container > 0
        dao.update(item.copy(container = Container.HOME, screen = screen, cellX = x, cellY = y, rank = 0))
        if (wasInFolder) cleanupFolder(item.container)
    }

    suspend fun moveToDock(id: Long, rank: Int, dockSize: Int) = write {
        val item = dao.byId(id) ?: return@write
        val oldContainer = item.container
        val dock = dao.inContainer(Container.DOCK).filter { it.id != id }.sortedBy { it.rank }.toMutableList()
        if (dock.size >= dockSize) return@write
        val r = rank.coerceIn(0, dock.size)
        dock.add(r, item.copy(container = Container.DOCK, screen = 0, cellX = 0, cellY = 0, spanX = 1, spanY = 1))
        dao.updateAll(dock.mapIndexed { i, e -> e.copy(rank = i) })
        if (oldContainer > 0) cleanupFolder(oldContainer)
    }

    suspend fun addApp(app: AppInfo, container: Long, screen: Int, x: Int, y: Int, rank: Int = 0): Long = write {
        dao.insert(appEntity(app, container, screen, x, y, rank))
    }

    suspend fun addAppToDock(app: AppInfo, rank: Int, dockSize: Int): Long = write {
        val dock = dao.inContainer(Container.DOCK).sortedBy { it.rank }
        if (dock.size >= dockSize) return@write -1L
        val r = rank.coerceIn(0, dock.size)
        dao.updateAll(dock.map { if (it.rank >= r) it.copy(rank = it.rank + 1) else it })
        dao.insert(appEntity(app, Container.DOCK, 0, 0, 0, r))
    }

    suspend fun addDotWidget(kind: String, face: String, screen: Int, x: Int, y: Int, spanX: Int, spanY: Int): Long = write {
        dao.insert(
            ItemEntity(
                type = ItemType.DOT_WIDGET, container = Container.HOME, screen = screen, cellX = x, cellY = y,
                spanX = spanX, spanY = spanY, widgetKind = kind, widgetConfig = WidgetConfigCodec.encode(mapOf("face" to face)),
            ),
        )
    }

    suspend fun addAppWidget(appWidgetId: Int, screen: Int, x: Int, y: Int, spanX: Int, spanY: Int): Long = write {
        dao.insert(
            ItemEntity(
                type = ItemType.APP_WIDGET, container = Container.HOME, screen = screen, cellX = x, cellY = y,
                spanX = spanX, spanY = spanY, appWidgetId = appWidgetId,
            ),
        )
    }

    suspend fun updateWidgetConfig(id: Long, config: Map<String, String>) = write {
        val item = dao.byId(id) ?: return@write
        dao.update(item.copy(widgetConfig = WidgetConfigCodec.encode(config)))
    }

    suspend fun resize(id: Long, spanX: Int, spanY: Int) = write {
        val item = dao.byId(id) ?: return@write
        dao.update(item.copy(spanX = spanX, spanY = spanY))
    }

    suspend fun remove(id: Long) = write {
        val item = dao.byId(id) ?: return@write
        dao.deleteWithChildren(id)
        if (item.container > 0) cleanupFolder(item.container)
        if (item.container == Container.DOCK) normalizeDock()
    }

    suspend fun removePackage(packageName: String, userSerial: Long) = write {
        dao.deleteForPackage(packageName, userSerial)
    }

    /** Drops [draggedId] (an existing item) onto [targetId] (an app on home or dock) to create a folder. */
    suspend fun createFolder(targetId: Long, draggedId: Long, title: String) = write {
        val target = dao.byId(targetId) ?: return@write
        val dragged = dao.byId(draggedId) ?: return@write
        val folderId = dao.insert(
            ItemEntity(
                type = ItemType.FOLDER, container = target.container, screen = target.screen,
                cellX = target.cellX, cellY = target.cellY, rank = target.rank, title = title,
            ),
        )
        val oldContainer = dragged.container
        dao.update(target.copy(container = folderId, rank = 0))
        dao.update(dragged.copy(container = folderId, rank = 1, spanX = 1, spanY = 1))
        if (oldContainer > 0) cleanupFolder(oldContainer)
        if (oldContainer == Container.DOCK) normalizeDock()
    }

    suspend fun addToFolder(folderId: Long, draggedId: Long) = write {
        val dragged = dao.byId(draggedId) ?: return@write
        if (dragged.id == folderId) return@write
        val oldContainer = dragged.container
        val count = dao.inContainer(folderId).size
        dao.update(dragged.copy(container = folderId, rank = count, spanX = 1, spanY = 1))
        if (oldContainer > 0 && oldContainer != folderId) cleanupFolder(oldContainer)
        if (oldContainer == Container.DOCK) normalizeDock()
    }

    suspend fun renameFolder(id: Long, title: String) = write {
        val item = dao.byId(id) ?: return@write
        dao.update(item.copy(title = title))
    }

    suspend fun deletePage(screen: Int) = write {
        dao.deleteScreen(screen)
        dao.shiftScreensAfter(screen)
    }

    /** After a grid-size change: move items that no longer fit to the nearest free spot (or a new page). */
    suspend fun reflow(cols: Int, rows: Int, pageCount: Int): Int = write {
        val all = dao.all().toMutableList()
        var pages = pageCount
        val home = all.filter { it.container == Container.HOME }.sortedWith(compareBy({ it.screen }, { it.cellY }, { it.cellX }))
        val placed = ArrayList<ItemEntity>()
        val updates = ArrayList<ItemEntity>()
        for (item in home) {
            val sx = item.spanX.coerceIn(1, cols)
            val sy = item.spanY.coerceIn(1, rows)
            var screen = item.screen
            var grid = occupancy(placed, screen, cols, rows)
            var pos: Pair<Int, Int>? = if (fits(grid, item.cellX, item.cellY, sx, sy)) item.cellX to item.cellY else null
            if (pos == null) pos = nearestFree(grid, sx, sy, item.cellX, item.cellY)
            while (pos == null) {
                screen++
                if (screen >= pages) pages = screen + 1
                grid = occupancy(placed, screen, cols, rows)
                pos = nearestFree(grid, sx, sy)
            }
            val moved = item.copy(screen = screen, cellX = pos.first, cellY = pos.second, spanX = sx, spanY = sy)
            placed += moved
            if (moved != item) updates += moved
        }
        if (updates.isNotEmpty()) dao.updateAll(updates)
        pages
    }

    private suspend fun cleanupFolder(folderId: Long) {
        val folder = dao.byId(folderId) ?: return
        val children = dao.inContainer(folderId).sortedBy { it.rank }
        when (children.size) {
            0 -> dao.delete(folder)
            1 -> {
                // A folder with one app dissolves back into that app, in place.
                dao.update(children[0].copy(container = folder.container, screen = folder.screen, cellX = folder.cellX, cellY = folder.cellY, rank = folder.rank))
                dao.delete(folder)
            }
            else -> dao.updateAll(children.mapIndexed { i, c -> c.copy(rank = i) })
        }
    }

    private suspend fun normalizeDock() {
        val dock = dao.inContainer(Container.DOCK).sortedBy { it.rank }
        dao.updateAll(dock.mapIndexed { i, e -> e.copy(rank = i) })
    }

    private fun appEntity(app: AppInfo, container: Long, screen: Int, x: Int, y: Int, rank: Int) = ItemEntity(
        type = ItemType.APP,
        container = container,
        screen = screen,
        cellX = x,
        cellY = y,
        rank = rank,
        packageName = app.component.packageName,
        className = app.component.className,
        userSerial = app.userSerial,
    )

    // ---------------------------------------------------------------- first run

    /**
     * Seeds a clean, Nothing-like first page if the database is empty:
     * clock + date widgets, a battery pill and a few essentials; dock with phone, messages,
     * camera, browser and settings.
     */
    suspend fun seedIfEmpty(apps: List<AppInfo>, mainSerial: Long, cols: Int, availableKinds: Set<String>) = write {
        if (dao.count() > 0) return@write
        val main = apps.filter { it.userSerial == mainSerial }
        val byPackage = main.groupBy { it.packageName }
        fun find(vararg pkgs: String?): AppInfo? = pkgs.filterNotNull().firstNotNullOfOrNull { byPackage[it]?.firstOrNull() }
        fun resolve(intent: Intent): String? = runCatching {
            context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
        }.getOrNull()?.takeIf { it != "android" }

        val phone = find(resolve(Intent(Intent.ACTION_DIAL)), "com.google.android.dialer", "com.android.dialer")
        val sms = find(runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(), "com.google.android.apps.messaging")
        val camera = find(resolve(Intent(MediaStore.ACTION_IMAGE_CAPTURE)), "com.motorola.camera5", "com.motorola.camera3", "com.google.android.GoogleCamera")
        val browser = find(resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))), "com.android.chrome")
        val settingsApp = find("com.android.settings")
        val dock = listOfNotNull(phone, sms, camera, browser, settingsApp).distinctBy { it.key }.take(5)
        val out = ArrayList<ItemEntity>()
        dock.forEachIndexed { i, a -> out += appEntity(a, Container.DOCK, 0, 0, 0, i) }

        var row = 0
        val twoByTwo = cols >= 4
        if ("clock" in availableKinds && twoByTwo) {
            out += ItemEntity(type = ItemType.DOT_WIDGET, container = Container.HOME, screen = 0, cellX = 0, cellY = 0, spanX = 2, spanY = 2, widgetKind = "clock", widgetConfig = WidgetConfigCodec.encode(mapOf("face" to "analog")))
        }
        if ("date" in availableKinds && twoByTwo) {
            out += ItemEntity(type = ItemType.DOT_WIDGET, container = Container.HOME, screen = 0, cellX = 2, cellY = 0, spanX = 2, spanY = 2, widgetKind = "date", widgetConfig = WidgetConfigCodec.encode(mapOf("face" to "default")))
        }
        if (out.any { it.type == ItemType.DOT_WIDGET }) row = 2
        if ("battery" in availableKinds && twoByTwo) {
            out += ItemEntity(type = ItemType.DOT_WIDGET, container = Container.HOME, screen = 0, cellX = 0, cellY = row, spanX = 2, spanY = 1, widgetKind = "battery", widgetConfig = WidgetConfigCodec.encode(mapOf("face" to "default")))
            if ("recorder" in availableKinds) {
                out += ItemEntity(type = ItemType.DOT_WIDGET, container = Container.HOME, screen = 0, cellX = 2, cellY = row, spanX = 2, spanY = 1, widgetKind = "recorder", widgetConfig = WidgetConfigCodec.encode(mapOf("face" to "default")))
            }
            row += 1
        }
        val used = dock.map { it.key }.toMutableSet()
        val essentials = listOf(
            "com.google.android.apps.photos", "com.google.android.gm", "com.google.android.apps.maps", "com.android.vending",
            "com.google.android.youtube", "com.whatsapp", "com.spotify.music", "com.google.android.deskclock",
            "com.google.android.calendar", "com.google.android.keep", "com.instagram.android", "org.telegram.messenger",
        ).mapNotNull { find(it) }.filter { used.add(it.key) }
        var x = 0
        for (app in essentials.take(cols * 2)) {
            out += appEntity(app, Container.HOME, 0, x, row, 0)
            x++
            if (x >= cols) {
                x = 0
                row++
            }
        }
        dao.insertAll(out)
    }

    companion object {
        fun componentKey(e: ItemEntity): String? {
            val pkg = e.packageName ?: return null
            val cls = e.className ?: return null
            return AppInfo.keyOf(ComponentName(pkg, cls), e.userSerial)
        }
    }
}
