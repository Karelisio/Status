package com.karelisio.status.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.AppGraph
import com.karelisio.status.shizuku.ShizukuManager
import com.karelisio.status.tweaks.TweakKeys
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.SectionHeader
import com.karelisio.status.ui.components.SettingsGroup
import com.karelisio.status.ui.components.animatedPolygonShape
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.components.rememberResumeTick

const val ADB_GRANT = "adb shell pm grant com.karelisio.status android.permission.WRITE_SECURE_SETTINGS"

@Composable
fun AdvancedAccessScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val tick = rememberResumeTick()
    val shizuku by ShizukuManager.status.collectAsStateWithLifecycle()
    LaunchedEffect(tick) { ShizukuManager.refresh() }
    val backend = AppGraph.tweaks.backend
    val secure = remember(tick) { backend.hasSecureSettings() }
    val write = remember(tick) { backend.hasWriteSettings() }
    val grant = ADB_GRANT.replace("com.karelisio.status", context.packageName)

    ScreenScaffold(title = "Accès avancé", onBack = onBack) {
        item(key = "shizuku") {
            Column(Modifier.cascadeIn(0)) {
                SectionHeader("Shizuku / Shevery")
                StatusCard(
                    ok = shizuku.ready,
                    title = shizuku.label,
                    text = buildString {
                        append("Service compatible Shizuku détecté par son binder (Shizuku, Shevery ou un fork).")
                        shizuku.version?.let { append("\nVersion de l'API : $it") }
                        shizuku.uid?.let { append(" · uid $it") }
                    },
                ) {
                    when {
                        !shizuku.running -> Text(
                            "Lancez Shevery ou Shizuku puis revenez ici.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        !shizuku.permissionGranted -> Button(onClick = { ShizukuManager.requestPermission() }) { Text("Autoriser Status") }
                    }
                }
            }
        }
        item(key = "adb") {
            Column(Modifier.cascadeIn(1)) {
                SectionHeader("Sans Shizuku : ADB")
                StatusCard(
                    ok = secure,
                    title = if (secure) "WRITE_SECURE_SETTINGS accordée" else "WRITE_SECURE_SETTINGS non accordée",
                    text = "Une seule fois, depuis un ordinateur avec le débogage USB activé :",
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(grant, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                    FilledTonalButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", grant))
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }) {
                        Icon(Icons.Rounded.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copier la commande")
                    }
                }
            }
        }
        item(key = "write") {
            Column(Modifier.cascadeIn(2)) {
                SectionHeader("Paramètres système")
                StatusCard(
                    ok = write,
                    title = if (write) "Modification autorisée" else "Modification non autorisée",
                    text = "Délai de veille, luminosité, rotation et caféine.",
                ) {
                    if (!write) Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Autoriser") }
                }
            }
        }
        item(key = "features") {
            Column(Modifier.cascadeIn(3)) {
                SectionHeader("Fonctions disponibles")
                SettingsGroup {
                    FeatureRow("Animations, DNS privé", backend.canWrite(TweakKeys.windowAnim))
                    FeatureRow("Fréquence d'écran forcée", backend.canWrite(TweakKeys.peakRefresh))
                    FeatureRow("Veille, luminosité, rotation, caféine", backend.canWrite(TweakKeys.screenOffTimeout))
                    FeatureRow("Désactiver des applis système", shizuku.ready)
                    FeatureRow("Forcer l'arrêt d'applis", shizuku.ready)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(ok: Boolean, title: String, text: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val badge by animateColorAsState(if (ok) cs.primary else cs.surfaceContainerHighest, label = "badge")
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(badge, animatedPolygonShape(if (ok) MaterialShapes.Cookie9Sided else MaterialShapes.Pentagon)), contentAlignment = Alignment.Center) {
                    Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.Close, null, tint = if (ok) cs.onPrimary else cs.onSurfaceVariant)
                }
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun FeatureRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.Close, null, tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    }
}
