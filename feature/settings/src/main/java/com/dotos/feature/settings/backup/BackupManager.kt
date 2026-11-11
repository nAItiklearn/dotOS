package com.dotos.feature.settings.backup

import android.content.Context
import android.net.Uri
import com.dotos.core.data.db.HiddenAppEntity
import com.dotos.core.data.db.ItemType
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.SettingsCodec
import com.dotos.core.data.settings.WallpaperConfig
import com.dotos.feature.settings.SettingsDeps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal class BackupResult(val ok: Boolean, val message: String)

/** Upgrades older backups in place. Version 1 is current, so there is nothing to migrate yet. */
internal object BackupMigrations {
    fun migrate(fromVersion: Int, files: MutableMap<String, ByteArray>): Boolean {
        // Future format: while (version < VERSION) { when (version) { 1 -> upgrade files to 2 ... } }
        return fromVersion == BackupFormat.VERSION && files.isNotEmpty()
    }
}

/**
 * `.dotbackup` = a zip with manifest.json (format + version header), settings.json, items.json,
 * hidden.json, overrides.json and presets.json. Export and import run on the IO dispatcher; import
 * decodes and validates everything BEFORE it touches any data, so a bad file can never leave the
 * launcher half-restored.
 */
internal object BackupManager {
    private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 24 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun export(context: Context, deps: SettingsDeps, uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val db = deps.database
            val settingsJson = SettingsCodec.encode(deps.settings.current())
            val items = db.items().all().map { it.toDto() }
            val hidden = db.appMeta().allHidden().map { it.toDto() }
            val overrides = db.appMeta().allOverrides().map { it.toDto() }
            val presets = db.wallpaperPresets().all().map { it.toDto() }
            val manifest = BackupManifest(createdAt = System.currentTimeMillis(), appVersion = deps.versionName)

            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext BackupResult(false, "Could not open the file for writing.")
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                fun put(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                put(BackupFormat.MANIFEST, json.encodeToString(BackupManifest.serializer(), manifest))
                put(BackupFormat.SETTINGS, settingsJson)
                put(BackupFormat.ITEMS, json.encodeToString(ListSerializer(ItemDto.serializer()), items))
                put(BackupFormat.HIDDEN, json.encodeToString(ListSerializer(HiddenDto.serializer()), hidden))
                put(BackupFormat.OVERRIDES, json.encodeToString(ListSerializer(OverrideDto.serializer()), overrides))
                put(BackupFormat.PRESETS, json.encodeToString(ListSerializer(PresetDto.serializer()), presets))
            }
            BackupResult(
                true,
                "Backup saved: ${items.size} home items, ${hidden.size} hidden apps, " +
                    "${overrides.size} icon overrides, ${presets.size} wallpaper presets.",
            )
        } catch (e: Exception) {
            BackupResult(false, "Backup failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun restore(context: Context, deps: SettingsDeps, uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val files = readZip(context, uri) ?: return@withContext BackupResult(false, "Could not open the file.")

            val manifestText = files[BackupFormat.MANIFEST]?.toString(Charsets.UTF_8)
                ?: return@withContext BackupResult(false, "This is not a DotOS backup (manifest.json is missing).")
            val manifest = json.decodeFromString(BackupManifest.serializer(), manifestText)
            if (manifest.format != BackupFormat.NAME) {
                return@withContext BackupResult(false, "This is not a DotOS backup.")
            }
            if (manifest.version > BackupFormat.VERSION) {
                return@withContext BackupResult(false, "This backup was made by a newer DotOS. Update the launcher and try again.")
            }
            if (manifest.version < 1 || !BackupMigrations.migrate(manifest.version, files)) {
                return@withContext BackupResult(false, "This backup version is not supported.")
            }

            // Decode everything first. Nothing has been changed yet if any of this throws.
            var settings: LauncherSettings = SettingsCodec.decode(files[BackupFormat.SETTINGS]?.toString(Charsets.UTF_8))
            val allItems = decodeList(files[BackupFormat.ITEMS], ListSerializer(ItemDto.serializer()))
            val hidden = decodeList(files[BackupFormat.HIDDEN], ListSerializer(HiddenDto.serializer()))
            val overrides = decodeList(files[BackupFormat.OVERRIDES], ListSerializer(OverrideDto.serializer()))
            val presets = decodeList(files[BackupFormat.PRESETS], ListSerializer(PresetDto.serializer()))

            // Hosted third-party widgets are bound to ids that only exist on the old install.
            val items = allItems.filter { it.type != ItemType.APP_WIDGET }
            val droppedWidgets = allItems.size - items.size

            // A photo wallpaper points at a private file that is not part of the backup.
            val photoPath = settings.wallpaper.imagePath
            if (settings.wallpaper.id == "image" && (photoPath == null || !File(photoPath).exists())) {
                settings = settings.copy(wallpaper = WallpaperConfig())
            }
            val restoredSettings = settings

            deps.settings.update { restoredSettings }
            val db = deps.database
            db.items().replaceAll(items.map { it.toEntity() })
            db.appMeta().clearHidden()
            for (h in hidden) db.appMeta().hide(HiddenAppEntity(h.componentKey))
            db.appMeta().clearOverrides()
            for (o in overrides) db.appMeta().upsertOverride(o.toEntity())
            db.wallpaperPresets().clear()
            for (p in presets) db.wallpaperPresets().insert(p.toEntity())

            val iconMap = HashMap<String, String>()
            for (o in overrides) {
                val icon = o.icon
                if (icon != null) iconMap[o.componentKey] = icon
            }
            deps.icons.setOverrides(iconMap)

            val note = if (droppedWidgets > 0) " $droppedWidgets third-party widgets were skipped." else ""
            BackupResult(
                true,
                "Restored: ${items.size} home items, ${hidden.size} hidden apps, " +
                    "${overrides.size} icon overrides, ${presets.size} wallpaper presets.$note",
            )
        } catch (e: Exception) {
            BackupResult(false, "Restore failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun <T> decodeList(bytes: ByteArray?, serializer: kotlinx.serialization.KSerializer<List<T>>): List<T> {
        if (bytes == null) return emptyList()
        return json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))
    }

    private fun readZip(context: Context, uri: Uri): MutableMap<String, ByteArray>? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val files = HashMap<String, ByteArray>()
        var total = 0
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                // Only known file names are ever read; the entry name is never used as a path.
                val name = entry.name.substringAfterLast('/')
                if (name !in BackupFormat.ALL_FILES) continue
                val bytes = readLimited(zip, MAX_ENTRY_BYTES)
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) throw IOException("The backup is too large.")
                files[name] = bytes
            }
        }
        return files
    }

    private fun readLimited(stream: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw IOException("A file inside the backup is too large.")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
