package com.karelisio.status.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.AppGraph
import com.karelisio.status.history.HistoryData
import com.karelisio.status.history.HistoryRange
import com.karelisio.status.ui.components.AreaLineChart
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.SectionHeader
import com.karelisio.status.ui.components.StackedColumnChart
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.components.timeSeries
import com.karelisio.status.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    var range by rememberSaveable { mutableStateOf(HistoryRange.DAY) }
    val flow = remember(range) { AppGraph.history.observe(range) }
    val data by flow.collectAsStateWithLifecycle(null)
    val haptics = LocalHapticFeedback.current

    ScreenScaffold(title = "Historique", subtitle = "Relevés toutes les 15 min") {
        item(key = "range") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                HistoryRange.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = r == range,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            range = r
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, HistoryRange.entries.size),
                    ) { Text(r.label) }
                }
            }
        }
        val d = data ?: return@ScreenScaffold
        historyCharts(d)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyCharts(d: HistoryData) {
    val unit = d.range.bucketMs
    val xFormat = xFormatter(d)
    val spacing = when (d.range) { HistoryRange.DAY -> 16; HistoryRange.WEEK -> 4; HistoryRange.MONTH -> 20 }
    val bucketSpacing = when (d.range) { HistoryRange.DAY -> 4; HistoryRange.WEEK -> 1; HistoryRange.MONTH -> 5 }
    val battery = timeSeries(d.points, d.from, unit, { it.timestamp }, { it.batteryLevel.toDouble() })
    val temp = timeSeries(d.points, d.from, unit, { it.timestamp }, { it.batteryTempC.toDouble() })
    item(key = "battery-${d.range}") {
        ChartCard("Batterie", "${d.points.size} relevés", Modifier.cascadeIn(0)) {
            AreaLineChart(
                x = battery.first,
                y = battery.second,
                color = MaterialTheme.colorScheme.primary,
                minY = 0.0, maxY = 100.0,
                xFormatter = xFormat,
                yFormatter = { "${it.toInt()} %" },
                labelSpacing = spacing,
            )
        }
    }
    item(key = "temp-${d.range}") {
        ChartCard(
            "Température",
            listOfNotNull(d.avgTemp?.let { "moy. ${Format.celsius(it)}" }, d.maxTemp?.let { "max ${Format.celsius(it)}" }).joinToString(" · "),
            Modifier.cascadeIn(1),
        ) {
            AreaLineChart(
                x = temp.first,
                y = temp.second,
                color = MaterialTheme.colorScheme.tertiary,
                minY = temp.second.minOrNull()?.let { kotlin.math.floor(it - 2) },
                maxY = temp.second.maxOrNull()?.let { kotlin.math.ceil(it + 2) },
                xFormatter = xFormat,
                yFormatter = { "${it.toInt()}°" },
                labelSpacing = spacing,
            )
        }
    }
    item(key = "data-${d.range}") {
        val mobileColor = MaterialTheme.colorScheme.primary
        val wifiColor = MaterialTheme.colorScheme.tertiaryContainer
        ChartCard("Data", "Mobile ${Format.bytes(d.totalMobile)} · Wi-Fi ${Format.bytes(d.totalWifi)}", Modifier.cascadeIn(2)) {
            StackedColumnChart(
                series = listOf(
                    d.buckets.map { it.mobileBytes / 1e6 },
                    d.buckets.map { it.wifiBytes / 1e6 },
                ),
                colors = listOf(mobileColor, wifiColor),
                xFormatter = { i -> d.buckets.getOrNull(i.toInt())?.let { bucketLabel(d, it.startMs) } ?: "" },
                yFormatter = { if (it >= 1000) "${(it / 1000).toInt()} Go" else "${it.toInt()} Mo" },
                labelSpacing = bucketSpacing,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(mobileColor, "Mobile")
                Legend(wifiColor, "Wi-Fi")
            }
        }
    }
    if (d.sessions.isNotEmpty()) {
        item(key = "sessions-h") { SectionHeader("Sessions de charge") }
        d.sessions.take(20).forEachIndexed { i, s ->
            item(key = "s-${s.startMs}") {
                Surface(
                    Modifier.fillMaxWidth().cascadeIn(i + 3),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${s.fromLevel} % → ${s.toLevel?.let { "$it %" } ?: "en cours"}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                DATE_TIME.format(Date(s.startMs)) +
                                    (s.endMs?.let { " · ${Format.duration(it - s.startMs)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val DATE_TIME = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

private fun xFormatter(d: HistoryData): (Double) -> String = { units ->
    bucketLabel(d, d.from + (units * d.range.bucketMs).toLong())
}

private fun bucketLabel(d: HistoryData, ts: Long): String = when (d.range) {
    HistoryRange.DAY -> SimpleDateFormat("HH'h'", Locale.getDefault()).format(Date(ts))
    HistoryRange.WEEK -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ts))
    HistoryRange.MONTH -> SimpleDateFormat("d/MM", Locale.getDefault()).format(Date(ts))
}

@Composable
fun ChartCard(title: String, subtitle: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
