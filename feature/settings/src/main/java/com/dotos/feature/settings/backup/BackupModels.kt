package com.dotos.feature.settings.backup

import com.dotos.core.data.db.AppOverrideEntity
import com.dotos.core.data.db.HiddenAppEntity
import com.dotos.core.data.db.ItemEntity
import com.dotos.core.data.db.WallpaperPresetEntity
import kotlinx.serialization.Serializable

internal object BackupFormat {
    const val NAME = "dotbackup"

    /** Bump when the zip layout changes and add a step to [BackupMigrations]. */
    const val VERSION = 1

    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    const val ITEMS = "items.json"
    const val HIDDEN = "hidden.json"
    const val OVERRIDES = "overrides.json"
    const val PRESETS = "presets.json"

    val ALL_FILES = setOf(MANIFEST, SETTINGS, ITEMS, HIDDEN, OVERRIDES, PRESETS)
}

/** Version header of every backup. */
@Serializable
internal data class BackupManifest(
    val format: String = BackupFormat.NAME,
    val version: Int = BackupFormat.VERSION,
    val createdAt: Long = 0L,
    val appVersion: String = "",
)

/** Serializable mirror of [ItemEntity] (Room entities are not @Serializable). */
@Serializable
internal data class ItemDto(
    val id: Long = 0,
    val type: Int = 0,
    val container: Long = -100L,
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
    val widgetConfig: String? = null,
)

@Serializable
internal data class HiddenDto(val componentKey: String)

@Serializable
internal data class OverrideDto(val componentKey: String, val label: String? = null, val icon: String? = null)

@Serializable
internal data class PresetDto(val name: String, val configJson: String, val createdAt: Long = 0L)

internal fun ItemEntity.toDto() = ItemDto(
    id = id,
    type = type,
    container = container,
    screen = screen,
    cellX = cellX,
    cellY = cellY,
    spanX = spanX,
    spanY = spanY,
    rank = rank,
    packageName = packageName,
    className = className,
    userSerial = userSerial,
    shortcutId = shortcutId,
    title = title,
    appWidgetId = appWidgetId,
    widgetKind = widgetKind,
    widgetConfig = widgetConfig,
)

internal fun ItemDto.toEntity() = ItemEntity(
    id = id,
    type = type,
    container = container,
    screen = screen,
    cellX = cellX,
    cellY = cellY,
    spanX = spanX,
    spanY = spanY,
    rank = rank,
    packageName = packageName,
    className = className,
    userSerial = userSerial,
    shortcutId = shortcutId,
    title = title,
    appWidgetId = appWidgetId,
    widgetKind = widgetKind,
    widgetConfig = widgetConfig,
)

internal fun HiddenAppEntity.toDto() = HiddenDto(componentKey)

internal fun AppOverrideEntity.toDto() = OverrideDto(componentKey, label, icon)

internal fun OverrideDto.toEntity() = AppOverrideEntity(componentKey = componentKey, label = label, icon = icon)

internal fun WallpaperPresetEntity.toDto() = PresetDto(name, configJson, createdAt)

internal fun PresetDto.toEntity() = WallpaperPresetEntity(name = name, configJson = configJson, createdAt = createdAt)
