package com.karelisio.status.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.BuildConfig
import com.karelisio.status.ui.theme.NumberStyle
import com.karelisio.status.update.GhRelease
import com.karelisio.status.update.UpdateController
import com.karelisio.status.update.UpdateInstaller
import com.karelisio.status.update.UpdateState
import com.karelisio.status.util.Format

/** Shown above everything whenever an update is available / downloading / installing. */
@Composable
fun UpdateSheetHost() {
    val state by UpdateController.state.collectAsStateWithLifecycle()
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Installing -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    } ?: return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { UpdateController.dismiss() }, sheetState = sheet) {
        UpdateSheetContent(release, state)
    }
}

@Composable
private fun UpdateSheetContent(release: GhRelease, state: UpdateState) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(cs.primaryContainer, animatedPolygonShape(if (state is UpdateState.Downloading) MaterialShapes.SoftBurst else MaterialShapes.Cookie9Sided)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = cs.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Status ${release.version}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Installée : ${BuildConfig.VERSION_NAME}" + (release.apk?.size?.takeIf { it > 0 }?.let { " · ${Format.bytes(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            Changelog(release.body.orEmpty())
        }
        AnimatedContent(state, contentKey = { it::class }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "update") { s ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (s) {
                    is UpdateState.Downloading -> {
                        val p by animateFloatAsState(s.progress, spring(stiffness = 120f), label = "dl")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Téléchargement", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            AnimatedCounter("${(p * 100).toInt()} %", style = MaterialTheme.typography.labelLarge.merge(NumberStyle))
                        }
                        LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(10.dp))
                        TextButton(onClick = { UpdateController.dismiss() }, modifier = Modifier.align(Alignment.End)) { Text("Annuler") }
                    }
                    is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Installation… confirmez si Android le demande.", style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> {
                        if (s is UpdateState.Failed) Text(s.message, color = cs.error, style = MaterialTheme.typography.bodySmall)
                        if (!UpdateInstaller.canInstall(context)) {
                            Text("Autorisez d'abord Status à installer des applis.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Autoriser l'installation") }
                        } else {
                            Button(onClick = { UpdateController.download(release) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Text(if (s is UpdateState.Failed) "Réessayer" else "Télécharger et installer")
                            }
                        }
                        Row {
                            TextButton(onClick = { UpdateController.skip(release) }) { Text("Ignorer cette version") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { UpdateController.dismiss() }) { Text("Plus tard") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Minimal Markdown: headings, bullets, bold markers stripped. */
@Composable
fun Changelog(markdown: String) {
    val lines = remember(markdown) { markdown.lines().map { it.trimEnd() }.filter { it.isNotBlank() } }
    if (lines.isEmpty()) {
        Text("Pas de notes de version.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { raw ->
            val line = raw.replace("**", "").replace("`", "")
            when {
                line.startsWith("#") -> Text(line.trimStart('#', ' '), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row {
                    Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
                    Text(line.trimStart().drop(2), style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
