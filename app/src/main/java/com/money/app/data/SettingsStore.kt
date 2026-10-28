package com.money.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Chiaro"), DARK("Scuro") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val lockEnabled: Boolean = false,
    val remindersEnabled: Boolean = true,
    /** ARGB of the app primary color. */
    val accent: Long = Palette.LILAC,
    val onboarded: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val lock = booleanPreferencesKey("lock")
        val reminders = booleanPreferencesKey("reminders")
        val accent = longPreferencesKey("accent")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setLock(enabled: Boolean) = context.dataStore.edit { it[Keys.lock] = enabled }
    suspend fun setReminders(enabled: Boolean) = context.dataStore.edit { it[Keys.reminders] = enabled }
    suspend fun setAccent(color: Long) = context.dataStore.edit { it[Keys.accent] = color }
    suspend fun setOnboarded() = context.dataStore.edit { it[Keys.onboarded] = true }

    private fun Preferences.toSettings() = Settings(
        theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        lockEnabled = this[Keys.lock] ?: false,
        remindersEnabled = this[Keys.reminders] ?: true,
        accent = this[Keys.accent] ?: Palette.LILAC,
        onboarded = this[Keys.onboarded] ?: false,
    )
}
