package com.karelisio.status.metrics

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.util.concurrent.Executors

enum class Transport { NONE, WIFI, CELLULAR, ETHERNET, VPN, OTHER }

data class NetworkInfo(
    val transport: Transport,
    val validated: Boolean,
    val metered: Boolean,
    /** Bytes per second. */
    val rxRate: Long,
    val txRate: Long,
    val cellType: String?,
    val signalDbm: Int?,
    val signalLevel: Int?,
    val operator: String?,
    val ssid: String?,
    val wifiRssi: Int?,
    val wifiLinkMbps: Int?,
    val wifiFreqMhz: Int?,
    val ipv4: String?,
    val ipv6: String?,
)

data class TrafficCounters(val totalRx: Long, val totalTx: Long, val mobileRx: Long, val mobileTx: Long)

class NetworkReader(private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val tm = context.getSystemService(TelephonyManager::class.java)
    private val wm = context.applicationContext.getSystemService(WifiManager::class.java)

    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L
    @Volatile private var overrideNetworkType: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    private var displayCallback: Any? = null

    fun counters(): TrafficCounters = TrafficCounters(
        totalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0),
        totalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0),
        mobileRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0),
        mobileTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0),
    )

    /** Starts listening to 5G NSA/advanced display info. Safe to call repeatedly. */
    fun start() {
        if (displayCallback != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !hasPhoneState()) return
        val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                overrideNetworkType = info.overrideNetworkType
            }
        }
        runCatching { tm.registerTelephonyCallback(Executors.newSingleThreadExecutor(), cb) }
            .onSuccess { displayCallback = cb }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (displayCallback as? TelephonyCallback)?.let { runCatching { tm.unregisterTelephonyCallback(it) } }
        }
        displayCallback = null
    }

    @SuppressLint("MissingPermission")
    fun read(): NetworkInfo {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val dt = (now - lastAt).coerceAtLeast(1)
        val rxRate = if (lastRx >= 0 && rx >= lastRx) (rx - lastRx) * 1000 / dt else 0
        val txRate = if (lastTx >= 0 && tx >= lastTx) (tx - lastTx) * 1000 / dt else 0
        lastRx = rx; lastTx = tx; lastAt = now

        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps == null -> Transport.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Transport.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            else -> Transport.OTHER
        }
        val link = network?.let { cm.getLinkProperties(it) }
        val addresses = link?.linkAddresses?.map { it.address }.orEmpty().filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }

        val signal = runCatching { tm.signalStrength }.getOrNull()
        val cell = signal?.cellSignalStrengths?.firstOrNull()

        @Suppress("DEPRECATION")
        val wifi = if (transport == Transport.WIFI) runCatching { wm.connectionInfo }.getOrNull() else null
        val ssid = wifi?.ssid?.removeSurrounding("\"")?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.isBlank() || it == "<unknown ssid>" }

        return NetworkInfo(
            transport = transport,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = cm.isActiveNetworkMetered,
            rxRate = rxRate,
            txRate = txRate,
            cellType = cellTypeLabel(),
            signalDbm = cell?.dbm?.takeIf { it != Int.MAX_VALUE && it < 0 },
            signalLevel = cell?.level,
            operator = tm.networkOperatorName?.takeIf { it.isNotBlank() },
            ssid = ssid,
            wifiRssi = wifi?.rssi?.takeIf { it > -127 && it < 0 },
            wifiLinkMbps = wifi?.linkSpeed?.takeIf { it > 0 },
            wifiFreqMhz = wifi?.frequency?.takeIf { it > 0 },
            ipv4 = addresses.firstOrNull { it is Inet4Address }?.hostAddress,
            ipv6 = addresses.firstOrNull { it !is Inet4Address }?.hostAddress?.substringBefore('%'),
        )
    }

    @SuppressLint("MissingPermission")
    private fun cellTypeLabel(): String? {
        if (!hasPhoneState()) return null
        val override = overrideNetworkType
        if (override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
            override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
        ) return "5G"
        if (override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO) return "4G+"
        val type = runCatching { tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_GSM -> "2G"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> null
            else -> "Mobile"
        }
    }

    private fun hasPhoneState() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
        PackageManager.PERMISSION_GRANTED
}
