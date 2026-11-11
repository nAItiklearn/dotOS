package com.dotos.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItemEntity::class,
        HiddenAppEntity::class,
        AppOverrideEntity::class,
        AppUsageEntity::class,
        NoteEntity::class,
        WallpaperPresetEntity::class,
        WidgetStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DotDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun appMeta(): AppMetaDao
    abstract fun notes(): NoteDao
    abstract fun wallpaperPresets(): WallpaperPresetDao
    abstract fun widgetState(): WidgetStateDao

    companion object {
        fun create(context: Context): DotDatabase = Room
            .databaseBuilder(context.applicationContext, DotDatabase::class.java, "dotos.db")
            // A launcher must never refuse to start: an unknown future schema resets instead of crashing.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
    }
}
