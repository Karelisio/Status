package com.karelisio.status.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.AppGraph
import com.karelisio.status.permissions.AppPermission
import com.karelisio.status.permissions.PermissionCatalog
import com.karelisio.status.permissions.PermissionRequest
import com.karelisio.status.shizuku.ShizukuManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Grant state of a permission + a function that launches its request flow. */
class PermissionHandle(val granted: Boolean, val request: () -> Unit, val confirmManually: (() -> Unit)?)

@Composable
fun rememberPermissionHandle(p: AppPermission): PermissionHandle {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    var bump by remember { mutableIntStateOf(0) }
    val shizuku by ShizukuManager.status.collectAsStateWithLifecycle()
    val manualKey = remember(p.id) { booleanPreferencesKey("perm_confirmed_${p.id}") }
    val manual by remember(p.id) { AppGraph.settings.raw.map { it[manualKey] == true } }.collectAsStateWithLifecycle(false)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { bump++ }
    val granted = remember(tick, bump, shizuku, manual) { p.check?.invoke(context) ?: manual }
    val request: () -> Unit = {
        when (val r = p.request) {
            is PermissionRequest.Runtime -> launcher.launch(r.permission)
            is PermissionRequest.SettingsPage -> PermissionCatalog.launch(context, r.intents(context))
            PermissionRequest.Shizuku -> { ShizukuManager.refresh(); ShizukuManager.requestPermission() }
        }
    }
    val confirm: (() -> Unit)? = if (p.check == null) {
        { AppGraph.scope.launchSet(manualKey) }
    } else null
    return PermissionHandle(granted, request, confirm)
}

private fun kotlinx.coroutines.CoroutineScope.launchSet(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>) {
    launch { AppGraph.settings.set(key, true) }
}
