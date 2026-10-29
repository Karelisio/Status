package com.karelisio.status.util

import android.os.BatteryManager
import android.os.PowerManager
import com.karelisio.status.metrics.BatteryInfo
import com.karelisio.status.metrics.Transport

object Labels {
    fun batteryStatus(b: BatteryInfo): String = when (b.status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "En charge" + plug(b.plugged)?.let { " · $it" }.orEmpty()
        BatteryManager.BATTERY_STATUS_FULL -> "Pleine"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Branché, pas en charge"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Sur batterie"
        else -> "Inconnu"
    }

    fun plug(plugged: Int): String? = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "secteur"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "sans fil"
        BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
        else -> null
    }

    fun health(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Bonne"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Surchauffe"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Morte"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Surtension"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Défaillance"
        BatteryManager.BATTERY_HEALTH_COLD -> "Froide"
        else -> "Inconnue"
    }

    fun thermal(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Tiède"
        PowerManager.THERMAL_STATUS_MODERATE -> "Chaud"
        PowerManager.THERMAL_STATUS_SEVERE -> "Très chaud"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critique"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Urgence"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Arrêt imminent"
        else -> "Inconnu"
    }

    fun transport(t: Transport, cellType: String?): String = when (t) {
        Transport.WIFI -> "Wi-Fi"
        Transport.CELLULAR -> cellType ?: "Mobile"
        Transport.ETHERNET -> "Ethernet"
        Transport.VPN -> "VPN"
        Transport.OTHER -> "Autre"
        Transport.NONE -> "Hors ligne"
    }
}
