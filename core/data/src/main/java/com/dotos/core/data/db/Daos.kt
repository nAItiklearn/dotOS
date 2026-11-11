package com.dotos.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items")
    suspend fun all(): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE container = :container")
    suspend fun inContainer(container: Long): List<ItemEntity>

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    @Update
    suspend fun updateAll(items: List<ItemEntity>)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id OR container = :id")
    suspend fun deleteWithChildren(id: Long)

    @Query("DELETE FROM items WHERE packageName = :packageName AND userSerial = :userSerial AND type IN (0, 1)")
    suspend fun deleteForPackage(packageName: String, userSerial: Long)

    @Query("DELETE FROM items")
    suspend fun clear()

    @Query("UPDATE items SET screen = screen - 1 WHERE container = -100 AND screen > :screen")
    suspend fun shiftScreensAfter(screen: Int)

    @Query("DELETE FROM items WHERE container = -100 AND screen = :screen")
    suspend fun deleteScreen(screen: Int)

    @Transaction
    suspend fun replaceAll(items: List<ItemEntity>) {
        clear()
        insertAll(items)
    }
}

@Dao
interface AppMetaDao {
    @Query("SELECT * FROM hidden_apps")
    fun observeHidden(): Flow<List<HiddenAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(entity: HiddenAppEntity)

    @Query("DELETE FROM hidden_apps WHERE componentKey = :key")
    suspend fun unhide(key: String)

    @Query("SELECT * FROM app_overrides")
    fun observeOverrides(): Flow<List<AppOverrideEntity>>

    @Upsert
    suspend fun upsertOverride(entity: AppOverrideEntity)

    @Query("DELETE FROM app_overrides WHERE componentKey = :key")
    suspend fun clearOverride(key: String)

    @Query("SELECT * FROM app_usage")
    fun observeUsage(): Flow<List<AppUsageEntity>>

    @Query("INSERT OR IGNORE INTO app_usage(componentKey, launchCount, lastLaunched) VALUES(:key, 0, 0)")
    suspend fun ensureUsage(key: String)

    @Query("UPDATE app_usage SET launchCount = launchCount + 1, lastLaunched = :now WHERE componentKey = :key")
    suspend fun bumpUsage(key: String, now: Long)

    @Query("SELECT * FROM hidden_apps")
    suspend fun allHidden(): List<HiddenAppEntity>

    @Query("SELECT * FROM app_overrides")
    suspend fun allOverrides(): List<AppOverrideEntity>

    @Query("DELETE FROM hidden_apps")
    suspend fun clearHidden()

    @Query("DELETE FROM app_overrides")
    suspend fun clearOverrides()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE widgetItemId = :itemId LIMIT 1")
    fun observeForWidget(itemId: Long): Flow<NoteEntity?>

    @Upsert
    suspend fun upsert(note: NoteEntity): Long

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface WallpaperPresetDao {
    @Query("SELECT * FROM wallpaper_presets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WallpaperPresetEntity>>

    @Insert
    suspend fun insert(preset: WallpaperPresetEntity): Long

    @Delete
    suspend fun delete(preset: WallpaperPresetEntity)

    @Query("SELECT * FROM wallpaper_presets")
    suspend fun all(): List<WallpaperPresetEntity>

    @Query("DELETE FROM wallpaper_presets")
    suspend fun clear()
}

@Dao
interface WidgetStateDao {
    @Query("SELECT value FROM widget_state WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM widget_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entity: WidgetStateEntity)
}
