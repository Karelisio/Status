package com.karelisio.status.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.status.BuildConfig
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.onboarding.WelcomeIllustration
import com.karelisio.status.update.UpdateController
import com.karelisio.status.update.UpdateState

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by UpdateController.state.collectAsStateWithLifecycle()
    ScreenScaffold(title = "À propos", onBack = onBack) {
        item(key = "hero") {
            Column(Modifier.fillMaxWidth().cascadeIn(0), horizontalAlignment = Alignment.CenterHorizontally) {
                WelcomeIllustration(size = 180.dp)
                Spacer(Modifier.height(12.dp))
                Text("Status", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "update") {
            Surface(Modifier.fillMaxWidth().cascadeIn(1), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Mises à jour", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Vérification automatique au lancement (au plus toutes les 6 h) sur les releases GitHub de ${BuildConfig.RELEASES_REPO}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedContent(state, contentKey = { it::class }, label = "check") { s ->
                        when (s) {
                            UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                                LoadingIndicator(Modifier.size(36.dp)); Spacer(Modifier.width(12.dp)); Text("Vérification…")
                            }
                            UpdateState.UpToDate -> Text("Status est à jour.", color = MaterialTheme.colorScheme.primary)
                            is UpdateState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                            else -> Spacer(Modifier.height(0.dp))
                        }
                    }
                    Button(onClick = { UpdateController.checkNow() }, modifier = Modifier.fillMaxWidth()) { Text("Rechercher une mise à jour") }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.RELEASES_REPO}/releases")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Voir les versions sur GitHub") }
                }
            }
        }
        item(key = "privacy") {
            Text(
                "100 % local : aucune donnée ne quitte le téléphone, pas de compte, pas de serveur. Seule la vérification des mises à jour contacte GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().cascadeIn(2).padding(horizontal = 12.dp),
            )
        }
    }
}
