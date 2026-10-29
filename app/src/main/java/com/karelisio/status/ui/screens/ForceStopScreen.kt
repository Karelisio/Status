package com.karelisio.status.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.AppGraph
import com.karelisio.status.shizuku.ShizukuManager
import com.karelisio.status.tweaks.BloatList
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.UsageBarRow
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.components.rememberResumeTick
import com.karelisio.status.usage.NetType
import com.karelisio.status.usage.UsageAccess
import com.karelisio.status.util.Format
import java.util.Calendar
import kotlinx.coroutines.launch

data class GreedyApp(val pkg: String, val label: String, val foregroundMs: Long, val dataBytes: Long) {
    /** Rough "consumption" score: one minute on screen ≈ 5 MB of traffic. */
    val score: Double get() = foregroundMs / 60_000.0 * 5 + dataBytes / 1e6
}

@Composable
fun ForceStopScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val shizuku by ShizukuManager.status.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    var reload by remember { mutableIntStateOf(0) }

    val apps by produceState<List<GreedyApp>?>(null, tick, reload) {
        if (!UsageAccess.granted(context)) { value = emptyList(); return@produceState }
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val fg = AppGraph.screenTime.todayByPackage()
        val data = HashMap<String, Long>()
        NetType.entries.forEach { t ->
            AppGraph.dataUsage.perApp(t, start).forEach { a -> a.packageName?.let { data[it] = (data[it] ?: 0) + a.total } }
        }
        val pm = context.packageManager
        val own = context.packageName
        value = (fg.keys + data.keys).distinct()
            .filter { it != own && it !in BloatList.critical && it != "android" }
            .mapNotNull { pkg ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
                if (info.flags and ApplicationInfo.FLAG_STOPPED != 0) return@mapNotNull null
                GreedyApp(pkg, pm.getApplicationLabel(info).toString(), fg[pkg] ?: 0, data[pkg] ?: 0)
            }
            .filter { it.score > 0.5 }
            .sortedByDescending { it.score }
    }

    ScreenScaffold(title = "Forcer l'arrêt", subtitle = "Classées par conso aujourd'hui", onBack = onBack, snackbarHostState = snackbar) {
        val list = apps
        if (list == null) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { LoadingIndicator() } }
            return@ScreenScaffold
        }
        if (!shizuku.ready) {
            item {
                Text(
                    "Sans Shizuku / Shevery, le bouton ouvre la fiche de l'appli pour un arrêt manuel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (list.isEmpty()) {
            item { Text("Rien à signaler (ou accès aux données d'utilisation manquant).", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val max = list.firstOrNull()?.score ?: 1.0
        list.take(40).forEachIndexed { i, app ->
            item(key = app.pkg) {
                UsageBarRow(
                    packageName = app.pkg,
                    label = app.label,
                    value = Format.duration(app.foregroundMs),
                    secondary = "Data ${Format.bytes(app.dataBytes)}",
                    fraction = (app.score / max).toFloat(),
                    modifier = Modifier.cascadeIn(i).animateItem(),
                    trailing = {
                        FilledTonalIconButton(onClick = {
                            if (shizuku.ready) {
                                scope.launch {
                                    val r = AppGraph.tweaks.forceStop(app.pkg)
                                    haptics.performHapticFeedback(if (r.ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                                    snackbar.showSnackbar(if (r.ok) "${app.label} arrêtée" else r.err.ifBlank { "Échec" })
                                    reload++
                                }
                            } else {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Icon(if (shizuku.ready) Icons.Rounded.StopCircle else Icons.Rounded.Info, "Forcer l'arrêt") }
                    },
                )
            }
        }
    }
}
