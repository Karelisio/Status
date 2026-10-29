package com.karelisio.status.metrics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlin.math.abs

data class BatteryInfo(
    val level: Int,
    val status: Int,
    val charging: Boolean,
    val plugged: Int,
    val health: Int,
    val tempC: Float,
    val voltageMv: Int,
    /** Normalized: milliamps, positive = flowing into the battery. Null when the device doesn't report it. */
    val currentMa: Int?,
    /** Signed watts, positive while charging. */
    val powerW: Float?,
    val technology: String?,
    val chargeCounterMah: Int?,
    val designCapacityMah: Int?,
    val cycleCount: Int?,
) {
    val full: Boolean get() = status == BatteryManager.BATTERY_STATUS_FULL
}

/**
 * Reads the sticky ACTION_BATTERY_CHANGED intent plus BatteryManager properties.
 *
 * BATTERY_PROPERTY_CURRENT_NOW is specified in µA but several OEMs (Honor/Huawei among them)
 * report mA, and the sign convention varies. We detect the unit from magnitude — a phone never
 * draws < 25 mA with the CPU awake, nor > 25 A — and orient the sign with the charging status.
 */
class BatteryReader(private val context: Context) {
    private val bm = context.getSystemService(BatteryManager::class.java)
    private val prefs = context.getSharedPreferences("battery_calibration", Context.MODE_PRIVATE)
    @Volatile private var microAmps: Boolean? = if (prefs.contains(KEY_UNIT)) prefs.getBoolean(KEY_UNIT, true) else null

    fun stickyIntent(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun read(intent: Intent? = stickyIntent()): BatteryInfo {
        val levelRaw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (levelRaw >= 0 && scale > 0) levelRaw * 100 / scale
        else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            (plugged != 0 && status == BatteryManager.BATTERY_STATUS_FULL)
        val voltage = normalizeVoltage(intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0)
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val current = readCurrentMa(status)
        val power = current?.let { if (voltage > 0) voltage * it / 1_000_000f else null }
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it > 0 && it != Int.MIN_VALUE }?.let { if (it > 100_000) it / 1000 else it }
        val cycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else null
        return BatteryInfo(
            level = level.coerceIn(0, 100),
            status = status,
            charging = charging,
            plugged = plugged,
            health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0,
            tempC = temp,
            voltageMv = voltage,
            currentMa = current,
            powerW = power,
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            chargeCounterMah = counter,
            designCapacityMah = counter?.let { if (level > 0) it * 100 / level else null },
            cycleCount = cycles,
        )
    }

    private fun normalizeVoltage(raw: Int): Int = when {
        raw <= 0 -> 0
        raw < 100 -> raw * 1000 // reported in volts
        raw > 100_000 -> raw / 1000 // reported in µV
        else -> raw
    }

    private fun readCurrentMa(status: Int): Int? {
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == Int.MIN_VALUE || raw == Int.MAX_VALUE || raw == 0) return null
        if (abs(raw) > UNIT_THRESHOLD && microAmps != true) {
            microAmps = true
            prefs.edit().putBoolean(KEY_UNIT, true).apply()
        } else if (microAmps == null && abs(raw) in 1..UNIT_THRESHOLD) {
            // Small magnitudes are ambiguous on the first read; assume mA only if they look like a real draw.
            if (abs(raw) >= 50) {
                microAmps = false
                prefs.edit().putBoolean(KEY_UNIT, false).apply()
            }
        }
        var ma = if (microAmps != false) raw / 1000 else raw
        if (ma == 0 && microAmps != false) ma = if (raw > 0) 1 else -1
        // Orient: positive when charging, negative when discharging.
        ma = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> abs(ma)
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> -abs(ma)
            else -> ma
        }
        return ma
    }

    companion object {
        private const val KEY_UNIT = "current_unit_micro"
        private const val UNIT_THRESHOLD = 25_000
    }
}
