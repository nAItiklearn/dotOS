package com.dotos.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

private val Context.settingsStore by preferencesDataStore(name = "launcher_settings")

/** Single source of truth for [LauncherSettings]. */
class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.settingsStore
    private val key = stringPreferencesKey("settings_json")

    /** Emits null until the first read completes, so the UI never flashes defaults. */
    val settingsOrNull: StateFlow<LauncherSettings?> = store.data
        .map { prefs -> SettingsCodec.decode(prefs[key]) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun current(): LauncherSettings = store.data.map { SettingsCodec.decode(it[key]) }.first()

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        store.edit { prefs ->
            prefs[key] = SettingsCodec.encode(transform(SettingsCodec.decode(prefs[key])))
        }
    }
}

object SettingsCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun encode(settings: LauncherSettings): String = json.encodeToString(LauncherSettings.serializer(), settings)

    /** Never throws: corrupted settings fall back to defaults instead of breaking the home screen. */
    fun decode(raw: String?): LauncherSettings {
        if (raw.isNullOrBlank()) return LauncherSettings()
        return runCatching { json.decodeFromString(LauncherSettings.serializer(), raw) }.getOrElse { LauncherSettings() }
    }
}
