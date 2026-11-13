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
    val recurringSort: RecurringSort = RecurringSort.NEXT,
    /** Amounts on Home and Movimenti shown as dots, for prying eyes. */
    val hideAmounts: Boolean = false,
    /** Only the account balances (Disponibile) shown as dots. */
    val hideBalances: Boolean = false,
    /** Skeuomorphic look (leather, paper, glossy buttons) instead of the classic one. */
    val skeuo: Boolean = false,
    /** Modern UI look (Windows Phone: flat tiles, big lowercase titles), wins over [skeuo]. */
    val metro: Boolean = false,
    /** Nothing look (monochrome, dot-matrix, Space Mono labels), wins over [metro] and [skeuo]. */
    val nothing: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val lock = booleanPreferencesKey("lock")
        val reminders = booleanPreferencesKey("reminders")
        val accent = longPreferencesKey("accent")
        val onboarded = booleanPreferencesKey("onboarded")
        val recurringSort = stringPreferencesKey("recurring_sort")
        val hideAmounts = booleanPreferencesKey("hide_amounts")
        val hideBalances = booleanPreferencesKey("hide_balances")
        val skeuo = booleanPreferencesKey("skeuo")
        val metro = booleanPreferencesKey("metro")
        val nothing = booleanPreferencesKey("nothing")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setLock(enabled: Boolean) = context.dataStore.edit { it[Keys.lock] = enabled }
    suspend fun setReminders(enabled: Boolean) = context.dataStore.edit { it[Keys.reminders] = enabled }
    suspend fun setAccent(color: Long) = context.dataStore.edit { it[Keys.accent] = color }
    suspend fun setOnboarded() = context.dataStore.edit { it[Keys.onboarded] = true }
    suspend fun setRecurringSort(sort: RecurringSort) = context.dataStore.edit { it[Keys.recurringSort] = sort.name }
    suspend fun setHideAmounts(hidden: Boolean) = context.dataStore.edit { it[Keys.hideAmounts] = hidden }
    suspend fun setHideBalances(hidden: Boolean) = context.dataStore.edit { it[Keys.hideBalances] = hidden }
    /** 0 classic, 1 skeuomorphic, 2 Modern UI, 3 Nothing. */
    suspend fun setStyle(style: Int) = context.dataStore.edit {
        it[Keys.skeuo] = style == 1
        it[Keys.metro] = style == 2
        it[Keys.nothing] = style == 3
    }

    private fun Preferences.toSettings() = Settings(
        theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        lockEnabled = this[Keys.lock] ?: false,
        remindersEnabled = this[Keys.reminders] ?: true,
        accent = this[Keys.accent] ?: Palette.LILAC,
        onboarded = this[Keys.onboarded] ?: false,
        recurringSort = this[Keys.recurringSort]?.let { runCatching { RecurringSort.valueOf(it) }.getOrNull() } ?: RecurringSort.NEXT,
        hideAmounts = this[Keys.hideAmounts] ?: false,
        hideBalances = this[Keys.hideBalances] ?: false,
        skeuo = this[Keys.skeuo] ?: false,
        metro = this[Keys.metro] ?: false,
        nothing = this[Keys.nothing] ?: false,
    )
}
