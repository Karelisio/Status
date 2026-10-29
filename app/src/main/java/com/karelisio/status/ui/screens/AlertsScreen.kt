package com.karelisio.status.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.AppGraph
import com.karelisio.status.alerts.AlarmPlayer
import com.karelisio.status.data.prefs.SettingsStore.Keys
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.SectionHeader
import com.karelisio.status.ui.components.SettingsGroup
import com.karelisio.status.ui.components.SliderRow
import com.karelisio.status.ui.components.SwitchRow
import com.karelisio.status.ui.components.cascadeIn
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(onBack: () -> Unit) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun <T> set(key: Preferences.Key<T>, v: T) = scope.launch { AppGraph.settings.set(key, v) }
    fun setAndRefresh(key: Preferences.Key<Boolean>, v: Boolean) = scope.launch {
        AppGraph.settings.set(key, v)
        AppGraph.ensureMonitor()
    }

    ScreenScaffold(title = "Alertes", onBack = onBack) {
        val st = s ?: return@ScreenScaffold
        item(key = "charge") {
            Column(Modifier.cascadeIn(0)) {
            SectionHeader("Charge")
            SettingsGroup {
                SwitchRow("Surveiller la charge", st.chargingServiceEnabled, { setAndRefresh(Keys.chargingServiceEnabled, it) },
                    subtitle = "Service lancé au branchement du chargeur", icon = Icons.Rounded.NotificationsActive)
                SwitchRow("Alerte de seuil", st.chargeAlertEnabled, { set(Keys.chargeAlertEnabled, it) },
                    icon = Icons.Rounded.BatteryChargingFull, enabled = st.chargingServiceEnabled)
                SliderRow("Seuil de charge", st.chargeThreshold, 50..100, { set(Keys.chargeThreshold, it) },
                    step = 5, enabled = st.chargeAlertEnabled && st.chargingServiceEnabled, format = { "$it %" })
            }
            }
        }
        item(key = "heat") {
            Column(Modifier.cascadeIn(1)) {
            SectionHeader("Surchauffe")
            SettingsGroup {
                SwitchRow("Alerte de surchauffe", st.overheatAlertEnabled, { set(Keys.overheatAlertEnabled, it) }, icon = Icons.Rounded.DeviceThermostat)
                SliderRow("Température max.", st.overheatThresholdC, 35..50, { set(Keys.overheatThresholdC, it) },
                    enabled = st.overheatAlertEnabled, format = { "$it °C" })
            }
            }
        }
        item(key = "storage") {
            Column(Modifier.cascadeIn(2)) {
            SectionHeader("Stockage et forfait")
            SettingsGroup {
                SwitchRow("Stockage faible", st.storageAlertEnabled, { set(Keys.storageAlertEnabled, it) },
                    subtitle = "Sous ${st.storageThresholdPct} % d'espace libre", icon = Icons.Rounded.SdStorage)
                SliderRow("Seuil de stockage", st.storageThresholdPct, 5..30, { set(Keys.storageThresholdPct, it) },
                    enabled = st.storageAlertEnabled, format = { "$it %" })
                SwitchRow("Forfait à 80 % et 95 %", st.planAlertsEnabled, { set(Keys.planAlertsEnabled, it) }, icon = Icons.Rounded.DataUsage)
            }
            }
        }
        item(key = "sound") {
            Column(Modifier.cascadeIn(3)) {
            SectionHeader("Son et vibration")
            SettingsGroup {
                SwitchRow("Son d'alarme", st.alarmSound, { set(Keys.alarmSound, it) },
                    subtitle = "Retentit même en mode silencieux (volume alarme)", icon = Icons.Rounded.VolumeUp)
                SwitchRow("Vibration d'alarme", st.alarmVibrate, { set(Keys.alarmVibrate, it) },
                    subtitle = "Passe outre le mode silencieux", icon = Icons.Rounded.Vibration)
                FilledTonalButton(
                    onClick = { AlarmPlayer.play(context, st.alarmSound, st.alarmVibrate, 2_500) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                ) { Text("Tester") }
            }
            }
        }
        item(key = "live") {
            Column(Modifier.cascadeIn(4)) {
            SectionHeader("Notification persistante")
            SettingsGroup {
                SwitchRow("Stats live dans la notification", st.liveNotification, { setAndRefresh(Keys.liveNotification, it) },
                    subtitle = "Batterie, puissance, température et débit en permanence", icon = Icons.Rounded.Insights)
            }
            }
        }
    }
}
