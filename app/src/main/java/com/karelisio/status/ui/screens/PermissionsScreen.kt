package com.karelisio.status.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.status.permissions.AppPermission
import com.karelisio.status.permissions.PermissionCatalog
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.animatedPolygonShape
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.components.rememberPermissionHandle

@Composable
fun PermissionsScreen(onBack: () -> Unit, onReplayOnboarding: () -> Unit) {
    val perms = remember { PermissionCatalog.all() }
    ScreenScaffold(title = "Autorisations", onBack = onBack) {
        perms.forEachIndexed { i, p -> item(key = p.id) { PermissionRow(p, Modifier.cascadeIn(i)) } }
        item(key = "replay") {
            TextButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) { Text("Revoir la présentation") }
        }
    }
}

@Composable
private fun PermissionRow(p: AppPermission, modifier: Modifier) {
    val h = rememberPermissionHandle(p)
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(if (h.granted) cs.primary else cs.secondaryContainer, animatedPolygonShape(if (h.granted) MaterialShapes.Circle else MaterialShapes.Cookie7Sided)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (h.granted) Icons.Rounded.Check else p.icon, null, tint = if (h.granted) cs.onPrimary else cs.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title + if (p.optional) " · facultatif" else "", style = MaterialTheme.typography.titleSmall)
                Text(p.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            AnimatedContent(h.granted, label = "btn") { granted ->
                if (granted) Text("OK", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                else FilledTonalButton(onClick = h.request) { Text(if (h.confirmManually != null) "Ouvrir" else "Autoriser") }
            }
        }
    }
}
