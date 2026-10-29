package com.karelisio.status.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.AppGraph
import com.karelisio.status.data.prefs.AppSettings
import com.karelisio.status.data.prefs.SettingsStore
import com.karelisio.status.ui.components.AnimatedCounter
import com.karelisio.status.ui.components.PermissionNeededCard
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.SectionHeader
import com.karelisio.status.ui.components.StackedColumnChart
import com.karelisio.status.ui.components.StatusLevel
import com.karelisio.status.ui.components.UsageBarRow
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.components.colors
import com.karelisio.status.ui.components.rememberResumeTick
import com.karelisio.status.ui.theme.NumberStyle
import com.karelisio.status.usage.AppDataUsage
import com.karelisio.status.usage.NetType
import com.karelisio.status.usage.PlanUsage
import com.karelisio.status.usage.ScreenTimeReport
import com.karelisio.status.usage.UsageAccess
import com.karelisio.status.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class UsageTab(val label: String) { DATA("Data"), SCREEN("Temps d'écran") }

@Composable
fun UsageScreen() {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    var tab by rememberSaveable { mutableStateOf(UsageTab.DATA) }
    var netType by rememberSaveable { mutableStateOf(NetType.MOBILE) }
    var editPlan by rememberSaveable { mutableStateOf(false) }
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(null)
    val haptics = LocalHapticFeedback.current
    val granted = androidx.compose.runtime.remember(tick) { UsageAccess.granted(context) }

    val plan by produceState<PlanUsage?>(null, settings, tick) { settings?.let { value = AppGraph.dataUsage.plan(it) } }
    val apps by produceState<List<AppDataUsage>?>(null, plan?.cycle, netType, tick) {
        val c = plan?.cycle ?: return@produceState
        value = AppGraph.dataUsage.perApp(netType, c.start)
    }
    val screen by produceState<ScreenTimeReport?>(null, tick) { value = AppGraph.screenTime.report() }

    ScreenScaffold(title = "Usage") {
        item(key = "tabs") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                UsageTab.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = tab == t,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); tab = t },
                        shape = SegmentedButtonDefaults.itemShape(i, UsageTab.entries.size),
                    ) { Text(t.label) }
                }
            }
        }
        if (!granted) {
            item(key = "perm") {
                PermissionNeededCard(
                    title = "Accès aux données d'utilisation",
                    text = "Nécessaire pour la consommation data par appli et le temps d'écran. Activez Status dans la liste.",
                    intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    icon = Icons.Rounded.QueryStats,
                )
            }
            return@ScreenScaffold
        }
        when (tab) {
            UsageTab.DATA -> {
                item(key = "plan") {
                    plan?.let { PlanCard(it, settings, Modifier.cascadeIn(0)) { editPlan = true } }
                }
                item(key = "net") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().cascadeIn(1)) {
                        NetType.entries.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = netType == t,
                                onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); netType = t },
                                shape = SegmentedButtonDefaults.itemShape(i, NetType.entries.size),
                            ) { Text(t.label) }
                        }
                    }
                }
                item(key = "apps-h") { SectionHeader("Par appli depuis le ${plan?.cycle?.start?.let { DAY.format(Date(it)) } ?: "…"}") }
                val list = apps.orEmpty()
                val max = list.firstOrNull()?.total ?: 1
                list.take(60).forEachIndexed { i, a ->
                    item(key = "a-${netType}-${a.key}") {
                        UsageBarRow(
                            packageName = a.packageName,
                            label = a.label,
                            value = Format.bytes(a.total),
                            secondary = "↓ ${Format.bytes(a.rxBytes)} · ↑ ${Format.bytes(a.txBytes)}",
                            fraction = a.total.toFloat() / max,
                            modifier = Modifier.cascadeIn(i + 2).animateItem(),
                        )
                    }
                }
            }
            UsageTab.SCREEN -> screen?.let { screenTimeItems(it) }
        }
    }

    if (editPlan && settings != null) {
        PlanDialog(settings!!, onDismiss = { editPlan = false })
    }
}

private val DAY = SimpleDateFormat("d MMMM", Locale.getDefault())

@Composable
private fun PlanCard(plan: PlanUsage, settings: AppSettings?, modifier: Modifier, onEdit: () -> Unit) {
    val level = StatusLevel.of(plan.ratio, 0.8f, 0.95f)
    val projectedLevel = StatusLevel.of(plan.projectedRatio, 0.9f, 1.0f)
    val colors = level.colors()
    val progress by animateFloatAsState(plan.ratio.coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = 60f), label = "plan")
    val stroke = with(LocalDensity.current) { Stroke(14.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Forfait mobile", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Renouvellement le ${DAY.format(Date(plan.cycle.end))} · ${plan.daysLeft} j",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Modifier le forfait") }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(220.dp),
                    color = if (level == StatusLevel.NORMAL) MaterialTheme.colorScheme.primary else colors.accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    stroke = stroke,
                    trackStroke = stroke,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedCounter(Format.bytes(plan.usedBytes), style = MaterialTheme.typography.headlineLarge.merge(NumberStyle))
                    Text("sur ${settings?.planGb?.toInt() ?: 0} Go", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${Format.bytes(plan.remainingBytes)} restants", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            val pc = projectedLevel.colors()
            Surface(shape = RoundedCornerShape(20.dp), color = pc.container, contentColor = pc.content, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Projection fin de cycle", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${Format.bytes(plan.projectedBytes)} · ${Format.pct(plan.projectedRatio)} du forfait",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanDialog(settings: AppSettings, onDismiss: () -> Unit) {
    var gb by rememberSaveable { mutableStateOf(settings.planGb.toInt().toString()) }
    var day by rememberSaveable { mutableStateOf(settings.planRenewDay.toString()) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forfait mensuel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(gb, { gb = it.filter(Char::isDigit) }, label = { Text("Volume (Go)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(day, { day = it.filter(Char::isDigit) }, label = { Text("Jour de renouvellement (1–28)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    gb.toFloatOrNull()?.takeIf { it > 0 }?.let { AppGraph.settings.set(SettingsStore.Keys.planGb, it) }
                    day.toIntOrNull()?.coerceIn(1, 28)?.let { AppGraph.settings.set(SettingsStore.Keys.planRenewDay, it) }
                    onDismiss()
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.screenTimeItems(r: ScreenTimeReport) {
    item(key = "st-today") {
        Row(Modifier.fillMaxWidth().cascadeIn(0), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.weight(1.4f), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(20.dp)) {
                    Text("Aujourd'hui", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    AnimatedCounter(Format.duration(r.today.totalMs), style = MaterialTheme.typography.headlineMedium.merge(NumberStyle), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LockOpen, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(" Déverrouillages", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    AnimatedCounter(r.today.unlocks.toString(), style = MaterialTheme.typography.headlineMedium.merge(NumberStyle), color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
    item(key = "st-week") {
        val avg = r.week.map { it.totalMs }.average().toLong()
        ChartCard("Cette semaine", "Moyenne ${Format.duration(avg)} / jour", Modifier.cascadeIn(1)) {
            StackedColumnChart(
                series = listOf(r.week.map { it.totalMs / 3_600_000.0 }),
                colors = listOf(MaterialTheme.colorScheme.primary),
                xFormatter = { i -> r.week.getOrNull(i.toInt())?.let { WEEKDAY.format(Date(it.dayStart)) } ?: "" },
                yFormatter = { "${it.toInt()} h" },
            )
        }
    }
    item(key = "st-h") { SectionHeader("Par appli aujourd'hui") }
    val max = r.apps.firstOrNull()?.foregroundMs ?: 1
    r.apps.take(50).forEachIndexed { i, a ->
        item(key = "st-${a.packageName}") {
            UsageBarRow(
                packageName = a.packageName,
                label = a.label,
                value = Format.duration(a.foregroundMs),
                secondary = "${a.launches} ouverture${if (a.launches > 1) "s" else ""}",
                fraction = a.foregroundMs.toFloat() / max,
                modifier = Modifier.cascadeIn(i + 2),
            )
        }
    }
}

private val WEEKDAY = SimpleDateFormat("EEE", Locale.getDefault())
