package com.karelisio.status.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.AppGraph
import com.karelisio.status.shizuku.ShizukuManager
import com.karelisio.status.tweaks.BloatEntry
import com.karelisio.status.tweaks.BloatList
import com.karelisio.status.ui.components.AppIcon
import com.karelisio.status.ui.components.ConnectedChoice
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.components.rememberResumeTick
import com.karelisio.status.ui.nav.AdvancedAccessRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SystemApp(val pkg: String, val label: String, val enabled: Boolean, val bloat: BloatEntry?)

private enum class AppFilter(val label: String) { RECOMMENDED("Recommandées"), DISABLED("Désactivées"), ALL("Toutes") }

@Composable
fun SystemAppsScreen(onBack: () -> Unit, onOpen: (Any) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val shizuku by ShizukuManager.status.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    var reload by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(AppFilter.RECOMMENDED) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<SystemApp?>(null) }

    val apps by produceState<List<SystemApp>?>(null, tick, reload) {
        value = withContext(Dispatchers.IO) { loadSystemApps(context.packageManager) }
    }

    fun toggle(app: SystemApp) {
        scope.launch {
            val r = if (app.enabled) AppGraph.tweaks.disablePackage(app.pkg) else AppGraph.tweaks.enablePackage(app.pkg)
            if (r.ok) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                snackbar.showSnackbar("${app.label} ${if (app.enabled) "désactivée" else "réactivée"}")
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                snackbar.showSnackbar(r.err.ifBlank { "Échec (${r.code})" })
            }
            reload++
        }
    }

    ScreenScaffold(title = "Nettoyage Honor", subtitle = "pm disable-user --user 0", onBack = onBack, snackbarHostState = snackbar) {
        if (!shizuku.ready) {
            item(key = "need") {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Shizuku / Shevery requis", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "La désactivation d'applis système passe par le shell. Sans Shizuku, utilisez « Infos » pour ouvrir la fiche de l'appli.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { onOpen(AdvancedAccessRoute) }) { Text("Configurer l'accès avancé") }
                    }
                }
            }
        }
        item(key = "filter") {
            ConnectedChoice(AppFilter.entries, filter, { it.label }, { filter = it })
        }
        item(key = "search") {
            OutlinedTextField(
                query, { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Rechercher une appli ou un paquet") },
                shape = RoundedCornerShape(28.dp),
            )
        }
        val list = apps
        if (list == null) {
            item(key = "loading") { Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) { LoadingIndicator() } }
            return@ScreenScaffold
        }
        val q = query.trim().lowercase()
        val shown = list.filter {
            when (filter) {
                AppFilter.RECOMMENDED -> it.bloat != null
                AppFilter.DISABLED -> !it.enabled
                AppFilter.ALL -> true
            }
        }.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
        if (shown.isEmpty()) {
            item(key = "empty") { Text("Aucune appli", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        }
        shown.forEachIndexed { i, app ->
            item(key = app.pkg) {
                SystemAppRow(
                    app = app,
                    canToggle = shizuku.ready,
                    modifier = Modifier.cascadeIn(i).animateItem(),
                    onToggle = { if (app.enabled && app.bloat?.safe != true) confirm = app else toggle(app) },
                    onInfo = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
        }
    }

    confirm?.let { app ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Désactiver ${app.label} ?") },
            text = {
                Text(
                    (app.bloat?.note?.let { "$it.\n\n" } ?: "") +
                        "Cette appli n'est pas dans la liste des applis sûres. La désactiver peut casser une fonction du téléphone. " +
                        "Vous pourrez la réactiver ici ou avec « Tout restaurer ».",
                )
            },
            confirmButton = { TextButton(onClick = { confirm = null; toggle(app) }) { Text("Désactiver quand même") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun SystemAppRow(app: SystemApp, canToggle: Boolean, modifier: Modifier, onToggle: () -> Unit, onInfo: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = if (app.enabled) cs.surfaceContainerLow else cs.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg, modifier = Modifier.graphicsAlpha(if (app.enabled) 1f else 0.45f))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    app.bloat?.let {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            if (it.safe) Icons.Rounded.Verified else Icons.Rounded.Warning, null,
                            tint = if (it.safe) cs.primary else cs.error,
                            modifier = Modifier.width(18.dp),
                        )
                    }
                }
                Text(app.bloat?.note ?: app.pkg, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            AnimatedContent(app.enabled, label = "toggle") { enabled ->
                if (canToggle) {
                    if (enabled) FilledTonalButton(onClick = onToggle) { Text("Désactiver") }
                    else OutlinedButton(onClick = onToggle) { Text("Réactiver") }
                } else {
                    TextButton(onClick = onInfo) { Text("Infos") }
                }
            }
        }
    }
}

private fun Modifier.graphicsAlpha(a: Float) = this.then(Modifier.graphicsLayer { alpha = a })

fun loadSystemApps(pm: PackageManager): List<SystemApp> {
    val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
    return pm.getInstalledApplications(flags)
        .asSequence()
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 || BloatList.lookup(it.packageName) != null }
        .filterNot { it.packageName in BloatList.critical }
        .map { info ->
            val state = runCatching { pm.getApplicationEnabledSetting(info.packageName) }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            val enabled = when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> info.enabled
            }
            SystemApp(info.packageName, pm.getApplicationLabel(info).toString(), enabled, BloatList.lookup(info.packageName))
        }
        .sortedWith(compareByDescending<SystemApp> { it.bloat?.safe == true }.thenBy { it.label.lowercase() })
        .toList()
}
