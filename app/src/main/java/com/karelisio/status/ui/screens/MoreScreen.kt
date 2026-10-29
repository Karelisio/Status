package com.karelisio.status.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.karelisio.status.BuildConfig
import com.karelisio.status.ui.components.NavRow
import com.karelisio.status.ui.components.ScreenScaffold
import com.karelisio.status.ui.components.SectionHeader
import com.karelisio.status.ui.components.SettingsGroup
import com.karelisio.status.ui.components.cascadeIn
import com.karelisio.status.ui.nav.AboutRoute
import com.karelisio.status.ui.nav.AdvancedAccessRoute
import com.karelisio.status.ui.nav.AlertsRoute
import com.karelisio.status.ui.nav.ForceStopRoute
import com.karelisio.status.ui.nav.PermissionsRoute
import com.karelisio.status.ui.nav.SystemAppsRoute

@Composable
fun MoreScreen(onOpen: (Any) -> Unit) {
    ScreenScaffold(title = "Plus", subtitle = "Status ${BuildConfig.VERSION_NAME}") {
        item(key = "general") {
            Column(Modifier.cascadeIn(0)) {
                SectionHeader("Réglages")
                SettingsGroup {
                    NavRow("Alertes", { onOpen(AlertsRoute) }, subtitle = "Charge, surchauffe, stockage, forfait, notification live", icon = Icons.Rounded.NotificationsActive)
                    NavRow("Autorisations", { onOpen(PermissionsRoute) }, subtitle = "État de chaque autorisation", icon = Icons.Rounded.Shield)
                    NavRow("Accès avancé", { onOpen(AdvancedAccessRoute) }, subtitle = "Shizuku / Shevery, ADB", icon = Icons.Rounded.AdminPanelSettings)
                }
            }
        }
        item(key = "tools") {
            Column(Modifier.cascadeIn(1)) {
                SectionHeader("Outils")
                SettingsGroup {
                    NavRow("Nettoyage Honor", { onOpen(SystemAppsRoute) }, subtitle = "Désactiver les applis système inutiles", icon = Icons.Rounded.CleaningServices)
                    NavRow("Forcer l'arrêt", { onOpen(ForceStopRoute) }, subtitle = "Applis gourmandes classées par conso", icon = Icons.Rounded.StopCircle)
                }
            }
        }
        item(key = "about") {
            Column(Modifier.cascadeIn(2)) {
                SectionHeader("Application")
                SettingsGroup {
                    NavRow("Mises à jour", { onOpen(AboutRoute) }, subtitle = "Version ${BuildConfig.VERSION_NAME}", icon = Icons.Rounded.SystemUpdate)
                    NavRow("À propos", { onOpen(AboutRoute) }, subtitle = "100 % local, sans compte ni serveur", icon = Icons.Rounded.Info)
                }
            }
        }
    }
}
