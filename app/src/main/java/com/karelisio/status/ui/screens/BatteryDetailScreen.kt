package com.karelisio.status.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.AppGraph
import com.karelisio.status.history.HistoryRange
import com.karelisio.status.ui.components.AreaLineChart
import com.karelisio.status.ui.components.AnimatedCounter
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.components.sharedBoundsIfAvailable
import com.karelisio.status.ui.theme.NumberStyle
import com.karelisio.status.util.Format
import com.karelisio.status.util.Labels
import kotlin.math.abs

@Composable
fun BatteryDetailScreen(onBack: () -> Unit) {
    val live by AppGraph.metrics.live.collectAsStateWithLifecycle()
    val flow = remember { AppGraph.history.observe(HistoryRange.DAY) }
    val history by flow.collectAsStateWithLifecycle(null)

    ScreenScaffold(title = "Batterie", onBack = onBack) {
        val m = live ?: return@ScreenScaffold
        val b = m.battery
        item(key = "hero") {
            Surface(
                Modifier.fillMaxWidth().sharedBoundsIfAvailable("battery-card"),
                shape = RoundedCornerShape(36.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BatteryGaugeWithLabel(m, MaterialTheme.colorScheme.primary, Modifier.size(280.dp))
                }
            }
        }
        item(key = "power") {
            Row(Modifier.fillMaxWidth().cascadeIn(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("Puissance", b.powerW?.let { Format.watts(abs(it)) } ?: "—", Modifier.weight(1f))
                BigStat("Courant", b.currentMa?.let { "${abs(it)} mA" } ?: "—", Modifier.weight(1f))
            }
        }
        item(key = "chart") {
            val d = history
            ChartCard("Dernières 24 h", null, Modifier.cascadeIn(2)) {
                AreaLineChart(
                    x = d?.points?.map { (it.timestamp - d.from) / 3_600_000.0 }.orEmpty(),
                    y = d?.points?.map { it.batteryLevel.toDouble() }.orEmpty(),
                    color = MaterialTheme.colorScheme.primary,
                    minY = 0.0, maxY = 100.0,
                    xFormatter = { "${it.toInt()}h" },
                    yFormatter = { "${it.toInt()} %" },
                )
            }
        }
        item(key = "details") {
            Surface(
                Modifier.fillMaxWidth().cascadeIn(3),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    val rows = listOfNotNull(
                        "État" to Labels.batteryStatus(b),
                        "Santé" to Labels.health(b.health),
                        "Température" to Format.celsius(b.tempC),
                        "Tension" to Format.volts(b.voltageMv),
                        b.technology?.let { "Technologie" to it },
                        b.chargeCounterMah?.let { "Charge restante" to "$it mAh" },
                        b.designCapacityMah?.let { "Capacité estimée" to "$it mAh" },
                        b.cycleCount?.let { "Cycles" to it.toString() },
                    )
                    rows.forEachIndexed { i, (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(v, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (i < rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            AnimatedCounter(value, style = MaterialTheme.typography.headlineMedium.merge(NumberStyle), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
