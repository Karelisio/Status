package com.karelisio.status.metrics

import android.content.Context
import com.karelisio.status.data.db.SnapshotEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext

data class LiveMetrics(
    val timestamp: Long,
    val battery: BatteryInfo,
    val cpu: CpuInfo,
    val memory: MemoryInfo,
    val storage: StorageInfo,
    val thermal: ThermalInfo,
    val uptimeMs: Long,
    val network: NetworkInfo,
    /** Rolling window of recent throughput samples (bytes/s), oldest first. */
    val rxHistory: List<Long>,
    val txHistory: List<Long>,
)

class MetricsRepository(context: Context, scope: CoroutineScope) {
    val battery = BatteryReader(context)
    val system = SystemReader(context)
    val network = NetworkReader(context)

    private val rxWindow = ArrayDeque<Long>()
    private val txWindow = ArrayDeque<Long>()

    private val ticker: Flow<LiveMetrics> = flow {
        while (currentCoroutineContext().isActive) {
            emit(sample())
            delay(TICK_MS)
        }
    }.onStart { network.start() }
        .onCompletion { network.stop() }
        .flowOn(Dispatchers.IO)

    /** Live metrics, sampled every second while someone is watching. */
    val live: StateFlow<LiveMetrics?> = ticker.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    @Synchronized
    fun sample(): LiveMetrics {
        val net = network.read()
        push(rxWindow, net.rxRate)
        push(txWindow, net.txRate)
        return LiveMetrics(
            timestamp = System.currentTimeMillis(),
            battery = battery.read(),
            cpu = system.cpu(),
            memory = system.memory(),
            storage = system.storage(),
            thermal = system.thermal(),
            uptimeMs = system.uptimeMs(),
            network = net,
            rxHistory = rxWindow.toList(),
            txHistory = txWindow.toList(),
        )
    }

    /** Builds a history row from a one-shot reading (used by the worker and power receiver). */
    fun snapshot(event: Int): SnapshotEntity {
        val b = battery.read()
        val c = network.counters()
        val uptime = system.uptimeMs()
        return SnapshotEntity(
            timestamp = System.currentTimeMillis(),
            event = event,
            batteryLevel = b.level,
            batteryTempC = b.tempC,
            voltageMv = b.voltageMv,
            currentMa = b.currentMa ?: 0,
            powerW = b.powerW ?: 0f,
            charging = b.charging,
            plugType = b.plugged,
            totalRxBytes = c.totalRx,
            totalTxBytes = c.totalTx,
            mobileRxBytes = c.mobileRx,
            mobileTxBytes = c.mobileTx,
            bootTime = System.currentTimeMillis() - uptime,
            storageFreeBytes = system.storage().freeBytes,
            ramAvailBytes = system.memory().availBytes,
        )
    }

    private fun push(window: ArrayDeque<Long>, value: Long) {
        window.addLast(value)
        while (window.size > WINDOW) window.removeFirst()
    }

    companion object {
        const val TICK_MS = 1_000L
        const val WINDOW = 60
    }
}
