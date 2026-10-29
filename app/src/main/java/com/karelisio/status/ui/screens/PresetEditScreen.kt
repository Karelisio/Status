package com.karelisio.status.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.status.AppGraph
import com.karelisio.status.tweaks.DnsProvider
import com.karelisio.status.tweaks.Preset
import com.karelisio.status.tweaks.PresetValues
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.SectionHeader
import com.karelisio.status.ui.components.SettingsGroup
import com.karelisio.status.ui.components.SliderRow
import com.karelisio.status.ui.components.SwitchRow
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.util.Format
import kotlinx.coroutines.launch
import java.util.UUID

private val ICONS = listOf("game", "eco", "night", "sun", "speed", "star")

/** Tri-state chip row: "Ne pas changer" + options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionalChoice(title: String, value: T?, options: List<T>, label: (T) -> String, onChange: (T?) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = value == null, onClick = { onChange(null) }, label = { Text("Inchangé") })
            options.forEach { o -> FilterChip(selected = value == o, onClick = { onChange(o) }, label = { Text(label(o)) }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetEditScreen(id: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var preset by remember { mutableStateOf<Preset?>(null) }
    LaunchedEffect(id) {
        preset = AppGraph.tweaks.loadPresets().firstOrNull { it.id == id }
            ?: Preset(UUID.randomUUID().toString().take(8), "Mon préréglage", "star", PresetValues())
    }
    val p = preset
    ScreenScaffold(
        title = if (id == "new") "Nouveau préréglage" else "Modifier",
        onBack = onBack,
        actions = {
            if (p != null && id != "new") {
                IconButton(onClick = { scope.launch { AppGraph.tweaks.deletePreset(p.id); onBack() } }) { Icon(Icons.Rounded.Delete, "Supprimer") }
            }
        },
    ) {
        if (p == null) return@ScreenScaffold
        val v = p.values
        fun update(nv: PresetValues) { preset = p.copy(values = nv) }
        item(key = "name") {
            Column(Modifier.cascadeIn(0)) {
                OutlinedTextField(p.name, { preset = p.copy(name = it.take(20)) }, label = { Text("Nom") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ICONS.forEach { key ->
                        FilterChip(selected = p.icon == key, onClick = { preset = p.copy(icon = key) }, label = { Icon(presetIcon(key), null) })
                    }
                }
            }
        }
        item(key = "values") {
            Column(Modifier.cascadeIn(1)) {
                SectionHeader("Réglages appliqués")
                SettingsGroup {
                    OptionalChoice("Animations", v.animScale, listOf(1f, 0.5f, 0f), { if (it == 0f) "Désactivées" else if (it == 1f) "1x" else "0,5x" }) { update(v.copy(animScale = it)) }
                    OptionalChoice("Fréquence d'écran", v.refreshHz, AppGraph.tweaks.supportedRefreshRates().filter { it in listOf(60, 90, 120) }.ifEmpty { listOf(60, 90, 120) }, { "$it Hz" }) { update(v.copy(refreshHz = it)) }
                    OptionalChoice("Mise en veille", v.timeoutMs, listOf(15_000, 30_000, 60_000, 300_000, 600_000, 1_800_000), { if (it < 60_000) "${it / 1000} s" else Format.duration(it.toLong()) }) { update(v.copy(timeoutMs = it)) }
                    OptionalChoice("Rotation auto", v.autoRotate, listOf(true, false), { if (it) "Activée" else "Désactivée" }) { update(v.copy(autoRotate = it)) }
                    OptionalChoice("Luminosité auto", v.autoBrightness, listOf(true, false), { if (it) "Activée" else "Désactivée" }) {
                        update(v.copy(autoBrightness = it, brightness = if (it == true) null else v.brightness))
                    }
                    SwitchRow("Luminosité fixe", v.brightness != null, { on -> update(v.copy(brightness = if (on) 128 else null, autoBrightness = if (on) false else v.autoBrightness)) })
                    if (v.brightness != null) {
                        SliderRow("Niveau", v.brightness, 1..255, { update(v.copy(brightness = it)) }, format = { "${it * 100 / 255} %" })
                    }
                    OptionalChoice("DNS privé", v.dns, DnsProvider.entries.filter { it != DnsProvider.CUSTOM }, { it.label }) { update(v.copy(dns = it)) }
                }
            }
        }
        item(key = "save") {
            Row(Modifier.fillMaxWidth().cascadeIn(2).padding(top = 8.dp)) {
                Button(
                    onClick = { scope.launch { AppGraph.tweaks.savePreset(p); onBack() } },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Rounded.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Enregistrer")
                }
            }
        }
    }
}
