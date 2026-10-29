package com.karelisio.status.ui.screens

import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCell
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.AppGraph
import com.karelisio.status.metrics.CpuInfo
import com.karelisio.status.metrics.LiveMetrics
import com.karelisio.status.metrics.Transport
import com.karelisio.status.ui.components.AnimatedCounter
import com.karelisio.status.ui.components.BreathingBatteryGauge
import com.karelisio.status.ui.components.EcgTrace
import com.karelisio.status.ui.components.MetricCard
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.SectionHeader
import com.karelisio.status.ui.components.StatusLevel
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.components.sharedBoundsIfAvailable
import com.karelisio.status.ui.nav.BatteryDetailRoute
import com.karelisio.status.ui.theme.NumberStyle
import com.karelisio.status.util.Format
import com.karelisio.status.util.Labels
import kotlin.math.abs

@Composable
fun DashboardScreen(onOpen: (Any) -> Unit) {
    val live by AppGraph.metrics.live.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(null)
    DashboardContent(live, (settings?.overheatThresholdC ?: 42).toFloat(), onOpen)
}

@Composable
fun DashboardContent(live: LiveMetrics?, overheat: Float, onOpen: (Any) -> Unit) {
    val haptics = LocalHapticFeedback.current

    ScreenScaffold(title = "Status", subtitle = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}") {
        val m = live
        if (m == null) {
            item { Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            return@ScreenScaffold
        }
        item(key = "battery") {
            BatteryHero(m, overheat, Modifier.cascadeIn(0)) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onOpen(BatteryDetailRoute)
            }
        }
        item(key = "net") { NetworkCard(m, Modifier.cascadeIn(1)) }
        item(key = "sys") { SectionHeader("Système", Modifier.cascadeIn(2)) }
        item(key = "row1") {
            CardRow(Modifier.cascadeIn(3)) {
                val cpu = m.cpu
                MetricCard(
                    title = "Processeur",
                    value = cpu.load?.let { Format.pct(it) } ?: "—",
                    subtitle = buildString {
                        cpu.cores.mapNotNull { it.curKhz }.maxOrNull()?.let { append(Format.ghz(it)) }
                        if (cpu.loadIsEstimate && cpu.load != null) append(if (isEmpty()) "estimée" else " · estimée")
                    }.ifEmpty { null },
                    icon = Icons.Rounded.DeveloperBoard,
                    level = StatusLevel.of(cpu.load ?: 0f, 0.7f, 0.9f),
                    progress = cpu.load,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                MetricCard(
                    title = "Thermique",
                    value = Labels.thermal(m.thermal.status),
                    subtitle = listOfNotNull(
                        m.cpu.socTempC?.let { "SoC ${Format.celsius(it)}" },
                        m.thermal.headroom?.let { "Marge ${Format.pct((1f - it).coerceIn(0f, 1f))}" },
                    ).joinToString(" · ").ifEmpty { null },
                    icon = Icons.Rounded.Thermostat,
                    level = when {
                        m.thermal.status >= PowerManager.THERMAL_STATUS_SEVERE -> StatusLevel.CRITICAL
                        m.thermal.status >= PowerManager.THERMAL_STATUS_MODERATE -> StatusLevel.WARNING
                        else -> StatusLevel.NORMAL
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        item(key = "row2") {
            CardRow(Modifier.cascadeIn(4)) {
                MetricCard(
                    title = "Mémoire",
                    value = Format.pct(m.memory.usedRatio),
                    subtitle = "${Format.bytes(m.memory.totalBytes - m.memory.availBytes)} / ${Format.bytes(m.memory.totalBytes)}",
                    icon = Icons.Rounded.Memory,
                    level = StatusLevel.of(m.memory.usedRatio, 0.8f, 0.92f),
                    progress = m.memory.usedRatio,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                MetricCard(
                    title = "Stockage",
                    value = "${Format.bytes(m.storage.freeBytes)} libres",
                    subtitle = "sur ${Format.bytes(m.storage.totalBytes, 0)}",
                    icon = Icons.Rounded.SdStorage,
                    level = StatusLevel.ofLow(m.storage.freePct, 20f, 10f),
                    progress = m.storage.usedRatio,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        item(key = "row3") {
            CardRow(Modifier.cascadeIn(5)) {
                MetricCard(
                    title = "Temp. batterie",
                    value = Format.celsius(m.battery.tempC),
                    subtitle = "Santé : ${Labels.health(m.battery.health)}",
                    icon = Icons.Rounded.DeviceThermostat,
                    level = StatusLevel.of(m.battery.tempC, overheat - 4f, overheat),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                MetricCard(
                    title = "Allumé depuis",
                    value = Format.duration(m.uptimeMs),
                    icon = Icons.Rounded.Timer,
                    level = StatusLevel.NORMAL,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        item(key = "cores") { CoresCard(m.cpu, Modifier.cascadeIn(6)) }
    }
}

@Composable
private fun CardRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatteryHero(m: LiveMetrics, overheat: Float, modifier: Modifier, onClick: () -> Unit) {
    val b = m.battery
    val level = when {
        b.tempC >= overheat -> StatusLevel.CRITICAL
        !b.charging && b.level <= 10 -> StatusLevel.CRITICAL
        !b.charging && b.level <= 20 -> StatusLevel.WARNING
        else -> StatusLevel.NORMAL
    }
    val cs = MaterialTheme.colorScheme
    val gaugeColor by animateColorAsState(
        when (level) {
            StatusLevel.CRITICAL -> cs.error
            StatusLevel.WARNING -> cs.tertiary
            StatusLevel.NORMAL -> cs.primary
        }, label = "gauge",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().sharedBoundsIfAvailable("battery-card"),
        shape = RoundedCornerShape(36.dp),
        color = cs.surfaceContainerLow,
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BatteryGaugeWithLabel(m, gaugeColor, Modifier.size(232.dp))
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                b.powerW?.let { StatPill(Icons.Rounded.Bolt, Format.watts(abs(it)), if (it >= 0) "entrée" else "conso") }
                b.currentMa?.let { StatPill(Icons.Rounded.BatteryChargingFull, "${abs(it)} mA", if (it >= 0) "charge" else "décharge") }
                StatPill(Icons.Rounded.DeviceThermostat, Format.celsius(b.tempC), "temp.")
                if (b.voltageMv > 0) StatPill(Icons.Rounded.Bolt, Format.volts(b.voltageMv), "tension")
            }
        }
    }
}

@Composable
fun BatteryGaugeWithLabel(m: LiveMetrics, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val b = m.battery
    BreathingBatteryGauge(
        level = b.level,
        charging = b.charging,
        powerW = b.powerW ?: 0f,
        color = color,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedCounter(
                    b.level.toString(),
                    style = MaterialTheme.typography.displayLarge.merge(NumberStyle),
                )
                Text(
                    "%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp, start = 2.dp),
                )
            }
            Text(
                Labels.batteryStatus(b),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatPill(icon: ImageVector, value: String, label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(6.dp))
            AnimatedCounter(value, style = MaterialTheme.typography.labelLarge.merge(NumberStyle))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun NetworkCard(m: LiveMetrics, modifier: Modifier) {
    val n = m.network
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = cs.surfaceContainerLow) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (n.transport == Transport.WIFI) Icons.Rounded.Wifi else Icons.Rounded.NetworkCell, null, tint = cs.primary)
                Spacer(Modifier.width(8.dp))
                Text(Labels.transport(n.transport, n.cellType), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                val detail = when (n.transport) {
                    Transport.WIFI -> listOfNotNull(n.ssid, n.wifiRssi?.let { "$it dBm" }).joinToString(" · ")
                    else -> listOfNotNull(n.operator, n.signalDbm?.let { "$it dBm" }).joinToString(" · ")
                }
                Text(detail, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            EcgTrace(
                rx = m.rxHistory,
                tx = m.txHistory,
                windowSize = 14,
                rxColor = cs.primary,
                txColor = cs.tertiary,
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                RateColumn(Icons.Rounded.ArrowDownward, "Réception", Format.rate(n.rxRate), cs.primary, Modifier.weight(1f))
                RateColumn(Icons.Rounded.ArrowUpward, "Envoi", Format.rate(n.txRate), cs.tertiary, Modifier.weight(1f))
            }
            val ip = n.ipv4 ?: n.ipv6
            if (ip != null) {
                Spacer(Modifier.height(8.dp))
                Text("IP $ip", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (n.transport == Transport.WIFI && n.ssid == null) {
                Text("SSID masqué : autorisez la localisation", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RateColumn(icon: ImageVector, label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedCounter(value, style = MaterialTheme.typography.titleLarge.merge(NumberStyle))
    }
}

@Composable
private fun CoresCard(cpu: CpuInfo, modifier: Modifier) {
    if (cpu.cores.none { it.curKhz != null }) return
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerLow) {
        Column(Modifier.padding(20.dp)) {
            Text("Fréquences par cœur", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cpu.cores.forEach { core ->
                    val ratio by animateFloatAsState(core.ratio ?: 0f, label = "core${core.index}")
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.weight(1f).width(14.dp).background(cs.surfaceContainerHighest, CircleShape),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight(ratio.coerceIn(0.06f, 1f))
                                    .background(if (core.online) cs.primary else cs.outlineVariant, CircleShape),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            core.curKhz?.let { "${it / 1000}" } ?: "off",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
            Text("MHz", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
