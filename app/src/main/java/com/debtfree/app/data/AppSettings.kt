package com.debtfree.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "debt_free_settings")

data class AppSettings(
    val rate22K: BigDecimal = BigDecimal.ZERO,
    val rate24K: BigDecimal = BigDecimal.ZERO,
    val goldRateUpdatedOn: LocalDate? = null,
    val reminderDaysBefore: List<Int> = listOf(7, 1),
    val reminderTime: String = "09:00",
    val warnLtvAbovePercent: Int = 75,
    val appLockEnabled: Boolean = false,
    val screenCaptureProtection: Boolean = true,
    val lastBackupExport: LocalDate? = null,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val rate22 = stringPreferencesKey("gold_rate_22k")
        val rate24 = stringPreferencesKey("gold_rate_24k")
        val rateDate = stringPreferencesKey("gold_rate_updated_on")
        val reminderDays = stringPreferencesKey("reminder_days_before")
        val reminderTime = stringPreferencesKey("reminder_time")
        val ltvWarning = floatPreferencesKey("ltv_warning_percent")
        val appLock = booleanPreferencesKey("app_lock_enabled")
        val screenProtection = booleanPreferencesKey("screen_capture_protection")
        val lastExport = stringPreferencesKey("last_backup_export")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { preferences ->
        AppSettings(
            rate22K = preferences[Keys.rate22]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            rate24K = preferences[Keys.rate24]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            goldRateUpdatedOn = preferences[Keys.rateDate]?.let(LocalDate::parse),
            reminderDaysBefore = preferences[Keys.reminderDays]
                ?.split(",")?.mapNotNull(String::toIntOrNull)?.filter { it >= 0 } ?: listOf(7, 1),
            reminderTime = preferences[Keys.reminderTime] ?: "09:00",
            warnLtvAbovePercent = preferences[Keys.ltvWarning]?.toInt() ?: 75,
            appLockEnabled = preferences[Keys.appLock] ?: false,
            screenCaptureProtection = preferences[Keys.screenProtection] ?: true,
            lastBackupExport = preferences[Keys.lastExport]?.let(LocalDate::parse),
        )
    }

    suspend fun update(
        rate22K: BigDecimal,
        rate24K: BigDecimal,
        reminderDays: List<Int>,
        reminderTime: String,
        ltvWarning: Int,
        appLockEnabled: Boolean,
        screenCaptureProtection: Boolean,
    ) {
        context.settingsStore.edit { preferences ->
            val ratesChanged = preferences[Keys.rate22] != rate22K.toPlainString() ||
                preferences[Keys.rate24] != rate24K.toPlainString()
            if (ratesChanged) {
                if (rate22K > BigDecimal.ZERO || rate24K > BigDecimal.ZERO) {
                    preferences[Keys.rateDate] = LocalDate.now().toString()
                } else {
                    preferences.remove(Keys.rateDate)
                }
            }
            preferences[Keys.rate22] = rate22K.toPlainString()
            preferences[Keys.rate24] = rate24K.toPlainString()
            preferences[Keys.reminderDays] = reminderDays.sortedDescending().distinct().joinToString(",")
            preferences[Keys.reminderTime] = reminderTime
            preferences[Keys.ltvWarning] = ltvWarning.toFloat()
            preferences[Keys.appLock] = appLockEnabled
            preferences[Keys.screenProtection] = screenCaptureProtection
        }
    }

    suspend fun markExported(date: LocalDate = LocalDate.now()) {
        context.settingsStore.edit { it[Keys.lastExport] = date.toString() }
    }

    suspend fun saveGoldRates(rate22K: BigDecimal, rate24K: BigDecimal, date: LocalDate = LocalDate.now()) {
        require(rate22K >= BigDecimal.ZERO && rate24K >= BigDecimal.ZERO) { "Gold rates cannot be negative" }
        context.settingsStore.edit {
            it[Keys.rate22] = rate22K.toPlainString()
            it[Keys.rate24] = rate24K.toPlainString()
            it[Keys.rateDate] = date.toString()
        }
    }

    suspend fun clear() {
        context.settingsStore.edit { it.clear() }
    }

    suspend fun restore(settings: AppSettings) {
        context.settingsStore.edit {
            it[Keys.rate22] = settings.rate22K.toPlainString()
            it[Keys.rate24] = settings.rate24K.toPlainString()
            settings.goldRateUpdatedOn?.let { date -> it[Keys.rateDate] = date.toString() }
                ?: it.remove(Keys.rateDate)
            it[Keys.reminderDays] = settings.reminderDaysBefore.joinToString(",")
            it[Keys.reminderTime] = settings.reminderTime
            it[Keys.ltvWarning] = settings.warnLtvAbovePercent.toFloat()
            it[Keys.appLock] = settings.appLockEnabled
            it[Keys.screenProtection] = settings.screenCaptureProtection
            settings.lastBackupExport?.let { date -> it[Keys.lastExport] = date.toString() }
                ?: it.remove(Keys.lastExport)
        }
    }
}
