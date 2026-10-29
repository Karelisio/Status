package com.karelisio.status.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User-facing configuration. Every field has a sensible default so the app works out of the box. */
data class AppSettings(
    val onboardingDone: Boolean = false,
    val chargeAlertEnabled: Boolean = true,
    val chargeThreshold: Int = 80,
    val overheatAlertEnabled: Boolean = true,
    val overheatThresholdC: Int = 42,
    val storageAlertEnabled: Boolean = true,
    val storageThresholdPct: Int = 10,
    val planAlertsEnabled: Boolean = true,
    val alarmSound: Boolean = false,
    val alarmVibrate: Boolean = true,
    val chargingServiceEnabled: Boolean = true,
    val liveNotification: Boolean = false,
    val planGb: Float = 300f,
    val planRenewDay: Int = 1,
)

class SettingsStore(private val context: Context) {
    private val store get() = context.dataStore

    object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val chargeAlertEnabled = booleanPreferencesKey("charge_alert")
        val chargeThreshold = intPreferencesKey("charge_threshold")
        val overheatAlertEnabled = booleanPreferencesKey("overheat_alert")
        val overheatThresholdC = intPreferencesKey("overheat_threshold")
        val storageAlertEnabled = booleanPreferencesKey("storage_alert")
        val storageThresholdPct = intPreferencesKey("storage_threshold")
        val planAlertsEnabled = booleanPreferencesKey("plan_alerts")
        val alarmSound = booleanPreferencesKey("alarm_sound")
        val alarmVibrate = booleanPreferencesKey("alarm_vibrate")
        val chargingServiceEnabled = booleanPreferencesKey("charging_service")
        val liveNotification = booleanPreferencesKey("live_notification")
        val planGb = floatPreferencesKey("plan_gb")
        val planRenewDay = intPreferencesKey("plan_renew_day")

        // Internal state
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val skippedVersion = stringPreferencesKey("skipped_version")
        val chargeAlertFiredSession = booleanPreferencesKey("charge_alert_fired")
        val lastOverheatAlert = longPreferencesKey("last_overheat_alert")
        val lastStorageAlert = longPreferencesKey("last_storage_alert")
        val planAlertLevel = intPreferencesKey("plan_alert_level")
        val planAlertCycle = longPreferencesKey("plan_alert_cycle")
        val caffeineActive = booleanPreferencesKey("caffeine_active")
        val caffeineOriginalTimeout = intPreferencesKey("caffeine_original_timeout")
        val caffeineUntil = longPreferencesKey("caffeine_until")
        val presetsJson = stringPreferencesKey("presets_json")
        val activePresetId = stringPreferencesKey("active_preset")
        val prePresetJson = stringPreferencesKey("pre_preset_json")
        val tilePresetId = stringPreferencesKey("tile_preset")
        val originalsJson = stringPreferencesKey("originals_json")
        val disabledByApp = stringPreferencesKey("disabled_by_app")
        val lastDnsSpecifier = stringPreferencesKey("last_dns_specifier")
        val customDns = stringPreferencesKey("custom_dns")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
            chargeAlertEnabled = p[Keys.chargeAlertEnabled] ?: d.chargeAlertEnabled,
            chargeThreshold = p[Keys.chargeThreshold] ?: d.chargeThreshold,
            overheatAlertEnabled = p[Keys.overheatAlertEnabled] ?: d.overheatAlertEnabled,
            overheatThresholdC = p[Keys.overheatThresholdC] ?: d.overheatThresholdC,
            storageAlertEnabled = p[Keys.storageAlertEnabled] ?: d.storageAlertEnabled,
            storageThresholdPct = p[Keys.storageThresholdPct] ?: d.storageThresholdPct,
            planAlertsEnabled = p[Keys.planAlertsEnabled] ?: d.planAlertsEnabled,
            alarmSound = p[Keys.alarmSound] ?: d.alarmSound,
            alarmVibrate = p[Keys.alarmVibrate] ?: d.alarmVibrate,
            chargingServiceEnabled = p[Keys.chargingServiceEnabled] ?: d.chargingServiceEnabled,
            liveNotification = p[Keys.liveNotification] ?: d.liveNotification,
            planGb = p[Keys.planGb] ?: d.planGb,
            planRenewDay = p[Keys.planRenewDay] ?: d.planRenewDay,
        )
    }

    val raw: Flow<Preferences> = store.data

    suspend fun current(): AppSettings = settings.first()

    suspend fun <T> get(key: Preferences.Key<T>): T? = store.data.first()[key]

    suspend fun <T> set(key: Preferences.Key<T>, value: T?) {
        store.edit { if (value == null) it.remove(key) else it[key] = value }
    }

    suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }
}
