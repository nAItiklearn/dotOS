package com.dotos.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where an item lives. Positive values are folder ids. */
object Container {
    const val HOME = -100L
    const val DOCK = -101L
}

object ItemType {
    const val APP = 0
    const val SHORTCUT = 1
    const val FOLDER = 2
    /** Built-in DotOS widget rendered by the launcher. */
    const val DOT_WIDGET = 3
    /** Third-party AppWidget hosted through AppWidgetHost. */
    const val APP_WIDGET = 4
}

/**
 * One placed thing on home, dock or inside a folder.
 * Grid units: cellX/cellY top-left, spanX/spanY size. Dock and folder items use rank.
 */
@Entity(tableName = "items", indices = [Index("container"), Index("screen")])
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val container: Long,
    val screen: Int = 0,
    val cellX: Int = 0,
    val cellY: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val rank: Int = 0,
    val packageName: String? = null,
    val className: String? = null,
    val userSerial: Long = 0,
    val shortcutId: String? = null,
    val title: String? = null,
    val appWidgetId: Int = -1,
    val widgetKind: String? = null,
    /** Widget face / options as a small JSON object. */
    val widgetConfig: String? = null,
)

@Entity(tableName = "hidden_apps")
data class HiddenAppEntity(@PrimaryKey val componentKey: String)

@Entity(tableName = "app_overrides")
data class AppOverrideEntity(
    @PrimaryKey val componentKey: String,
    val label: String? = null,
    /** "glyph:<id>" for a bundled glyph. */
    val icon: String? = null,
)

@Entity(tableName = "app_usage")
data class AppUsageEntity(
    @PrimaryKey val componentKey: String,
    val launchCount: Int = 0,
    val lastLaunched: Long = 0,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Widget-bound notes carry the owning item id; free notes (quick capture) use 0. */
    val widgetItemId: Long = 0,
)

@Entity(tableName = "wallpaper_presets")
data class WallpaperPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Serialized WallpaperConfig JSON. */
    val configJson: String,
    val createdAt: Long,
)

/** Small key/value store for widget state (counters, high scores, timers). */
@Entity(tableName = "widget_state")
data class WidgetStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)
