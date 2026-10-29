package com.karelisio.status.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.AppGraph
import com.karelisio.status.shizuku.ShizukuManager
import com.karelisio.status.tweaks.DnsProvider
import com.karelisio.status.tweaks.OemRefreshKey
import com.karelisio.status.tweaks.Preset
import com.karelisio.status.tweaks.TweakKeys
import com.karelisio.status.tweaks.TweakState
import com.karelisio.status.tweaks.WriteResult
import com.karelisio.status.ui.components.ConnectedChoice
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.SectionHeader
import com.karelisio.status.ui.components.SettingsGroup
import com.karelisio.status.ui.components.SliderRow
import com.karelisio.status.ui.components.SwitchRow
import com.karelisio.status.ui.components.animatedPolygonShape
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.components.rememberResumeTick
import com.karelisio.status.ui.nav.AdvancedAccessRoute
import com.karelisio.status.ui.nav.PresetEditRoute
import com.karelisio.status.ui.theme.LocalReduceMotion
import com.karelisio.status.util.Format
import kotlinx.coroutines.launch

fun presetIcon(key: String): ImageVector = when (key) {
    "game" -> Icons.Rounded.SportsEsports
    "eco" -> Icons.Rounded.EnergySavingsLeaf
    "night" -> Icons.Rounded.Bedtime
    "sun" -> Icons.Rounded.WbSunny
    "speed" -> Icons.Rounded.Speed
    else -> Icons.Rounded.AutoAwesome
}

private val TIMEOUTS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000)
private val CAFFEINE_DURATIONS = listOf<Int?>(15, 30, 60, 120, null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TweaksScreen(onOpen: (Any) -> Unit) {
    val tweaks = AppGraph.tweaks
    val backend = tweaks.backend
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val tick = rememberResumeTick()
    val shizuku by ShizukuManager.status.collectAsStateWithLifecycle()
    val presets by tweaks.presets.collectAsStateWithLifecycle(emptyList())
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<TweakState?>(null) }
    var oemKeys by remember { mutableStateOf<List<OemRefreshKey>>(emptyList()) }
    var measured by remember { mutableStateOf<Int?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var customDns by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tick, reload, shizuku) {
        ShizukuManager.refresh()
        state = tweaks.state()
    }
    LaunchedEffect(shizuku.ready) { if (shizuku.ready) oemKeys = tweaks.detectOemRefreshKeys() }

    fun act(block: suspend () -> WriteResult) {
        scope.launch {
            val r = block()
            if (r is WriteResult.Failed) {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                snackbar.showSnackbar(r.reason)
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
            reload++
            AppGraph.refreshTiles()
        }
    }

    val canAnim = backend.canWrite(TweakKeys.windowAnim)
    val canRefresh = backend.canWrite(TweakKeys.peakRefresh)
    val canSystem = backend.canWrite(TweakKeys.screenOffTimeout)
    val canDns = backend.canWrite(TweakKeys.privateDnsMode)

    ScreenScaffold(title = "Tweaks", subtitle = accessLabel(shizuku.ready, backend.hasSecureSettings(), backend.hasWriteSettings()), snackbarHostState = snackbar) {
        val st = state ?: return@ScreenScaffold

        if (!canAnim || !canRefresh) {
            item(key = "access") {
                Surface(
                    onClick = { onOpen(AdvancedAccessRoute) },
                    modifier = Modifier.fillMaxWidth().cascadeIn(0),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Débloquer tous les tweaks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                "Activez Shizuku / Shevery ou accordez WRITE_SECURE_SETTINGS par ADB.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        }

        item(key = "presets") {
            Column(Modifier.cascadeIn(1)) {
                SectionHeader("Préréglages")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(presets, key = { _, p -> p.id }) { _, p ->
                        PresetCard(
                            preset = p,
                            active = st.activePreset == p.id,
                            enabled = canSystem || canAnim,
                            onApply = {
                                if (st.activePreset == p.id) act { tweaks.restorePreset() } else act { tweaks.applyPreset(p.id) }
                            },
                            onEdit = { onOpen(PresetEditRoute(p.id)) },
                        )
                    }
                    item(key = "add") {
                        Surface(
                            onClick = { onOpen(PresetEditRoute("new")) },
                            modifier = Modifier.size(width = 120.dp, height = 148.dp),
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, "Nouveau préréglage") }
                        }
                    }
                }
                AnimatedVisibility(st.activePreset != null) {
                    OutlinedButton(
                        onClick = { act { tweaks.restorePreset() } },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Undo, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Restaurer l'état précédent")
                    }
                }
                Text(
                    "Appui : appliquer / annuler · appui long : modifier",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }

        item(key = "caffeine") {
            Column(Modifier.cascadeIn(2)) {
                SectionHeader("Caféine")
                SettingsGroup {
                    SwitchRow(
                        "Écran toujours allumé",
                        st.caffeine,
                        { on -> act { tweaks.setCaffeine(on, if (on) 60 else null) } },
                        subtitle = when {
                            !canSystem -> "Autorisez la modification des paramètres système"
                            st.caffeine && st.caffeineUntil != null -> "Jusqu'à ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(st.caffeineUntil))}"
                            st.caffeine -> "Sans limite · délai d'origine restauré à l'arrêt"
                            else -> "Le délai de veille d'origine est restauré automatiquement"
                        },
                        icon = Icons.Rounded.Coffee,
                        enabled = canSystem,
                    )
                    FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CAFFEINE_DURATIONS.forEach { d ->
                            FilterChip(
                                selected = false,
                                onClick = { act { tweaks.setCaffeine(true, d) } },
                                label = { Text(d?.let { if (it >= 60) "${it / 60} h" else "$it min" } ?: "∞") },
                                enabled = canSystem,
                            )
                        }
                    }
                }
            }
        }

        item(key = "anim") {
            Column(Modifier.cascadeIn(3)) {
                SectionHeader("Animations")
                SettingsGroup {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Animation, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Échelle des animations", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        ConnectedChoice(
                            options = listOf(1f, 0.5f, 0f),
                            selected = st.animScale ?: 1f,
                            label = { if (it == 0f) "Désactivées" else if (it == 1f) "1x" else "0,5x" },
                            onSelect = { s -> act { tweaks.setAnimationScale(s) } },
                            enabled = canAnim,
                        )
                        if (!canAnim) Unavailable("Nécessite Shizuku ou WRITE_SECURE_SETTINGS (ADB)")
                    }
                }
            }
        }

        item(key = "refresh") {
            Column(Modifier.cascadeIn(4)) {
                SectionHeader("Fréquence d'écran")
                SettingsGroup {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Forcer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("Mesuré : ${measured ?: st.measuredHz} Hz", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(12.dp))
                        val rates = (listOf(60, 90, 120).filter { it in st.supportedHz }).ifEmpty { st.supportedHz }
                        ConnectedChoice(
                            options = rates,
                            selected = st.peakRefresh?.toInt()?.takeIf { st.minRefresh?.toInt() == it },
                            label = { "$it Hz" },
                            onSelect = { hz ->
                                act {
                                    val (r, m) = tweaks.setRefreshRate(hz)
                                    measured = m
                                    if (r is WriteResult.Ok && m != hz) {
                                        WriteResult.Failed("Appliqué, mais l'écran tourne à $m Hz : MagicOS peut l'ignorer (voir clés détectées)")
                                    } else r
                                }
                            },
                            enabled = canRefresh,
                        )
                        if (!canRefresh) Unavailable("Nécessite Shizuku / Shevery (clés système privées)")
                        if (oemKeys.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Clés constructeur détectées", style = MaterialTheme.typography.labelLarge)
                            oemKeys.take(8).forEach {
                                Text("${it.key.ns.shell}/${it.key.key} = ${it.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item(key = "screen") {
            Column(Modifier.cascadeIn(5)) {
                SectionHeader("Écran")
                SettingsGroup {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ScreenLockPortrait, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Délai de mise en veille", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TIMEOUTS.forEach { t ->
                                FilterChip(
                                    selected = st.timeoutMs == t,
                                    onClick = { act { tweaks.setScreenTimeout(t) } },
                                    label = { Text(Format.duration(t.toLong()).takeIf { t >= 60_000 } ?: "${t / 1000} s") },
                                    enabled = canSystem && !st.caffeine,
                                )
                            }
                        }
                        if (!canSystem) Unavailable("Autorisez la modification des paramètres système")
                    }
                    SwitchRow(
                        "Luminosité automatique", st.autoBrightness == true,
                        { on -> act { tweaks.setAutoBrightness(on) } },
                        icon = Icons.Rounded.BrightnessAuto, enabled = canSystem,
                    )
                    SliderRow(
                        "Luminosité", st.brightness ?: 128, 1..255,
                        { v -> act { tweaks.setBrightness(v) } },
                        enabled = canSystem && st.autoBrightness != true,
                        format = { "${it * 100 / 255} %" },
                    )
                    SwitchRow(
                        "Rotation automatique", st.autoRotate == true,
                        { on -> act { tweaks.setAutoRotate(on) } },
                        icon = Icons.Rounded.ScreenRotation, enabled = canSystem,
                    )
                }
            }
        }

        item(key = "dns") {
            Column(Modifier.cascadeIn(6)) {
                SectionHeader("DNS privé")
                SettingsGroup {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Dns, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Fournisseur", style = MaterialTheme.typography.titleSmall)
                                Text(st.dnsHost?.takeIf { st.dnsMode == "hostname" } ?: st.dnsProvider.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DnsProvider.entries.forEach { p ->
                                FilterChip(
                                    selected = st.dnsProvider == p,
                                    onClick = {
                                        if (p == DnsProvider.CUSTOM) customDns = st.dnsHost.orEmpty()
                                        else act { tweaks.setDns(p) }
                                    },
                                    label = { Text(p.label) },
                                    enabled = canDns,
                                )
                            }
                        }
                        if (!canDns) Unavailable("Nécessite Shizuku ou WRITE_SECURE_SETTINGS (ADB)")
                    }
                }
            }
        }

        item(key = "restore") {
            Column(Modifier.cascadeIn(7).padding(top = 12.dp)) {
                Button(
                    onClick = { confirmRestore = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = st.backupCount > 0 || st.disabledCount > 0 || st.activePreset != null,
                ) {
                    Icon(Icons.Rounded.Restore, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tout restaurer")
                }
                Text(
                    "${st.backupCount} réglage(s) d'origine sauvegardé(s) · ${st.disabledCount} appli(s) désactivée(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            icon = { Icon(Icons.Rounded.Restore, null) },
            title = { Text("Tout restaurer ?") },
            text = { Text("Les valeurs d'origine de tous les réglages modifiés seront remises et les applis désactivées réactivées.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    scope.launch {
                        val errors = tweaks.restoreAll()
                        reload++
                        AppGraph.refreshTiles()
                        snackbar.showSnackbar(if (errors.isEmpty()) "Tout est restauré" else "Restauré avec ${errors.size} erreur(s) : ${errors.first()}")
                    }
                }) { Text("Restaurer") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Annuler") } },
        )
    }

    customDns?.let { value ->
        AlertDialog(
            onDismissRequest = { customDns = null },
            title = { Text("DNS personnalisé") },
            text = {
                OutlinedTextField(value, { customDns = it.trim() }, label = { Text("Nom d'hôte DoT") }, singleLine = true, placeholder = { Text("dns.exemple.com") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val host = value
                    customDns = null
                    act { tweaks.setDns(DnsProvider.CUSTOM, host) }
                }) { Text("Appliquer") }
            },
            dismissButton = { TextButton(onClick = { customDns = null }) { Text("Annuler") } },
        )
    }
}

private fun accessLabel(shizuku: Boolean, secure: Boolean, write: Boolean) = when {
    shizuku -> "Accès complet via Shizuku"
    secure -> "Accès ADB (WRITE_SECURE_SETTINGS)"
    write -> "Accès limité aux réglages publics"
    else -> "Accès restreint"
}

@Composable
private fun Unavailable(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PresetCard(preset: Preset, active: Boolean, enabled: Boolean, onApply: () -> Unit, onEdit: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(if (active) cs.primaryContainer else cs.surfaceContainerHigh, label = "pc")
    val badge by animateColorAsState(if (active) cs.primary else cs.secondaryContainer, label = "pb")
    val onBadge by animateColorAsState(if (active) cs.onPrimary else cs.onSecondaryContainer, label = "pob")
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "s")
    Surface(
        modifier = Modifier
            .size(width = 120.dp, height = 148.dp)
            .clip(RoundedCornerShape(if (active) 40.dp else 32.dp))
            .combinedClickable(
                enabled = enabled,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onApply() },
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onEdit() },
            ),
        shape = RoundedCornerShape(if (active) 40.dp else 32.dp),
        color = container,
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(64.dp)
                        .graphicsLayer { if (active && !reduce) rotationZ = spin }
                        .background(badge, animatedPolygonShape(if (active) MaterialShapes.VerySunny else MaterialShapes.Cookie12Sided)),
                )
                Icon(presetIcon(preset.icon), null, tint = onBadge, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(preset.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(if (active) "Actif" else "Appliquer", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
