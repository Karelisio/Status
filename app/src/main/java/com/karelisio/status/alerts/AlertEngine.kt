package com.karelisio.status.alerts

import android.content.Context
import com.karelisio.status.data.prefs.SettingsStore
import com.karelisio.status.metrics.BatteryInfo
import com.karelisio.status.usage.DataUsageRepository
import com.karelisio.status.metrics.SystemReader

/** Evaluates alert conditions and fires each one at most once per relevant period. */
class AlertEngine(
    private val context: Context,
    private val store: SettingsStore,
    private val system: SystemReader,
    private val dataUsage: DataUsageRepository,
) {
    suspend fun onBattery(b: BatteryInfo) {
        val s = store.current()
        if (s.chargeAlertEnabled && b.charging && b.level >= s.chargeThreshold &&
            store.get(SettingsStore.Keys.chargeAlertFiredSession) != true
        ) {
            store.set(SettingsStore.Keys.chargeAlertFiredSession, true)
            Notifications.alert(
                context, Notifications.ID_CHARGE,
                "Charge à ${b.level} %",
                "Seuil de ${s.chargeThreshold} % atteint, vous pouvez débrancher.",
            )
            AlarmPlayer.play(context, s.alarmSound, s.alarmVibrate)
        }
        if (s.overheatAlertEnabled && b.tempC > s.overheatThresholdC) {
            val last = store.get(SettingsStore.Keys.lastOverheatAlert) ?: 0L
            if (System.currentTimeMillis() - last > OVERHEAT_COOLDOWN) {
                store.set(SettingsStore.Keys.lastOverheatAlert, System.currentTimeMillis())
                Notifications.alert(
                    context, Notifications.ID_OVERHEAT,
                    "Surchauffe : %.1f °C".format(b.tempC),
                    if (b.charging) "La batterie chauffe pendant la charge. Débranchez ou retirez la coque."
                    else "La batterie dépasse ${s.overheatThresholdC} °C. Laissez le téléphone refroidir.",
                )
                AlarmPlayer.play(context, s.alarmSound, s.alarmVibrate)
            }
        }
    }

    suspend fun onUnplugged() {
        store.set(SettingsStore.Keys.chargeAlertFiredSession, false)
    }

    suspend fun checkStorage() {
        val s = store.current()
        if (!s.storageAlertEnabled) return
        val st = system.storage()
        if (st.freePct >= s.storageThresholdPct) return
        val last = store.get(SettingsStore.Keys.lastStorageAlert) ?: 0L
        if (System.currentTimeMillis() - last < STORAGE_COOLDOWN) return
        store.set(SettingsStore.Keys.lastStorageAlert, System.currentTimeMillis())
        Notifications.alert(
            context, Notifications.ID_STORAGE,
            "Stockage presque plein",
            "Plus que %.0f %% d'espace libre. Libérez de la place.".format(st.freePct),
        )
    }

    suspend fun checkPlan() {
        val s = store.current()
        if (!s.planAlertsEnabled) return
        val plan = dataUsage.plan(s) ?: return
        val cycle = plan.cycle.start
        val storedCycle = store.get(SettingsStore.Keys.planAlertCycle)
        var level = if (storedCycle == cycle) store.get(SettingsStore.Keys.planAlertLevel) ?: 0 else 0
        val pct = plan.ratio * 100
        val target = when {
            pct >= 95 -> 95
            pct >= 80 -> 80
            else -> 0
        }
        if (target > level) {
            level = target
            Notifications.alert(
                context, Notifications.ID_PLAN,
                "Forfait à $target %",
                "Il reste ${com.karelisio.status.util.Format.bytes(plan.remainingBytes)} jusqu'au renouvellement (${plan.daysLeft} j).",
            )
        }
        store.edit {
            it[SettingsStore.Keys.planAlertCycle] = cycle
            it[SettingsStore.Keys.planAlertLevel] = level
        }
    }

    companion object {
        private const val OVERHEAT_COOLDOWN = 30 * 60_000L
        private const val STORAGE_COOLDOWN = 24 * 3_600_000L
    }
}
