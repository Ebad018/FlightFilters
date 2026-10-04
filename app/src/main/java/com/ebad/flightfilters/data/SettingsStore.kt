package com.ebad.flightfilters.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.store by preferencesDataStore(name = "flightfilters")

/** Settings, the working query and saved presets, stored as JSON in one DataStore. */
class SettingsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val settingsKey = stringPreferencesKey("settings")
    private val queryKey = stringPreferencesKey("query")
    private val presetsKey = stringPreferencesKey("presets")

    val settings: Flow<AppSettings> = read(settingsKey, AppSettings.serializer()) { AppSettings() }
    val query: Flow<Query> = read(queryKey, Query.serializer()) { defaultQuery() }
    val presets: Flow<List<Preset>> = read(presetsKey, ListSerializer(Preset.serializer())) { emptyList() }

    suspend fun saveSettings(s: AppSettings) = write(settingsKey, AppSettings.serializer(), s)
    suspend fun saveQuery(q: Query) = write(queryKey, Query.serializer(), q)
    suspend fun savePresets(p: List<Preset>) = write(presetsKey, ListSerializer(Preset.serializer()), p)

    private fun <T> read(key: androidx.datastore.preferences.core.Preferences.Key<String>, ser: KSerializer<T>, default: () -> T): Flow<T> =
        context.store.data.map { prefs ->
            prefs[key]?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: default()
        }

    private suspend fun <T> write(key: androidx.datastore.preferences.core.Preferences.Key<String>, ser: KSerializer<T>, value: T) {
        context.store.edit { it[key] = json.encodeToString(ser, value) }
    }

    companion object {
        /** Next weekend from home, to anywhere: other airports and destinations are the owner's to add. */
        fun defaultQuery(today: LocalDate = LocalDate.now()): Query {
            var fri = today.plusDays(1)
            while (fri.dayOfWeek != java.time.DayOfWeek.FRIDAY) fri = fri.plusDays(1)
            val window = TimeWindow(fri.toString(), fri.plusDays(1).toString(), 6, 22)
            return Query(origins = listOf(OriginLine("BSL", window)))
        }
    }
}
