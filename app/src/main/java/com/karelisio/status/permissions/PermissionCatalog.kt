package com.karelisio.status.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.karelisio.status.shizuku.ShizukuManager
import com.karelisio.status.usage.UsageAccess

sealed interface PermissionRequest {
    /** Classic runtime permission dialog. */
    data class Runtime(val permission: String) : PermissionRequest
    /** A system settings page; the state is re-checked when the user comes back. */
    data class SettingsPage(val intents: (Context) -> List<Intent>) : PermissionRequest
    data object Shizuku : PermissionRequest
}

data class AppPermission(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val optional: Boolean,
    val request: PermissionRequest,
    /** Null when the state can't be read (e.g. Honor launch manager): the user confirms manually. */
    val check: ((Context) -> Boolean)?,
)

object PermissionCatalog {
    val isHonor: Boolean
        get() = Build.MANUFACTURER.equals("HONOR", true) || Build.MANUFACTURER.equals("HUAWEI", true) || Build.BRAND.equals("HONOR", true)

    private fun pkgUri(c: Context) = Uri.parse("package:${c.packageName}")

    fun all(): List<AppPermission> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(
            AppPermission(
                "notifications", "Notifications",
                "Pour les alertes de charge, de surchauffe, de stockage et de forfait.",
                Icons.Rounded.Notifications, false,
                PermissionRequest.Runtime(Manifest.permission.POST_NOTIFICATIONS),
            ) { granted(it, Manifest.permission.POST_NOTIFICATIONS) },
        )
        add(
            AppPermission(
                "usage", "Données d'utilisation",
                "Consommation data par appli, temps d'écran et déverrouillages. Activez Status dans la liste.",
                Icons.Rounded.QueryStats, false,
                PermissionRequest.SettingsPage { c ->
                    listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkgUri(c)), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
            ) { UsageAccess.granted(it) },
        )
        add(
            AppPermission(
                "phone", "État du téléphone",
                "Pour afficher le type de réseau (4G, 5G) et la qualité du signal.",
                Icons.Rounded.PhoneAndroid, false,
                PermissionRequest.Runtime(Manifest.permission.READ_PHONE_STATE),
            ) { granted(it, Manifest.permission.READ_PHONE_STATE) },
        )
        add(
            AppPermission(
                "location", "Localisation",
                "Facultatif : Android l'exige pour lire le nom du Wi-Fi (SSID). Rien n'est enregistré.",
                Icons.Rounded.LocationOn, true,
                PermissionRequest.Runtime(Manifest.permission.ACCESS_FINE_LOCATION),
            ) { granted(it, Manifest.permission.ACCESS_FINE_LOCATION) || granted(it, Manifest.permission.ACCESS_COARSE_LOCATION) },
        )
        add(
            AppPermission(
                "write_settings", "Paramètres système",
                "Pour le délai de veille, la luminosité, la rotation et la caféine.",
                Icons.Rounded.SettingsSuggest, false,
                PermissionRequest.SettingsPage { c -> listOf(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri(c))) },
            ) { Settings.System.canWrite(it) },
        )
        add(
            AppPermission(
                "battery", "Optimisation de la batterie",
                "Pour que la surveillance de charge et les relevés fonctionnent en arrière-plan.",
                Icons.Rounded.BatterySaver, false,
                PermissionRequest.SettingsPage { c ->
                    listOf(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(c)),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                },
            ) { it.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(it.packageName) },
        )
        if (isHonor) add(
            AppPermission(
                "honor_launch", "Lancement d'applis Honor",
                "MagicOS ferme les applis en arrière-plan. Passez Status en « Gérer manuellement » et laissez les 3 options activées.",
                Icons.Rounded.RocketLaunch, false,
                PermissionRequest.SettingsPage { c -> honorLaunchIntents(c) },
                check = null,
            ),
        )
        add(
            AppPermission(
                "shizuku", "Shizuku / Shevery",
                "Facultatif : débloque tous les tweaks (animations, fréquence d'écran, applis système). Lancez Shevery ou Shizuku avant d'autoriser.",
                Icons.Rounded.AdminPanelSettings, true,
                PermissionRequest.Shizuku,
            ) { ShizukuManager.status.value.ready },
        )
        add(
            AppPermission(
                "install", "Installer des mises à jour",
                "Pour installer les nouvelles versions de Status directement depuis l'appli.",
                Icons.Rounded.InstallMobile, false,
                PermissionRequest.SettingsPage { c -> listOf(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri(c))) },
            ) { it.packageManager.canRequestPackageInstalls() },
        )
    }

    private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    /** Honor / Huawei "App launch" manager, with app details as fallback. */
    fun honorLaunchIntents(c: Context): List<Intent> = listOf(
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
    ).map { Intent().setComponent(it) } +
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(c))

    /** Starts the first intent of the list that resolves. */
    fun launch(c: Context, intents: List<Intent>): Boolean {
        for (i in intents) {
            val ok = runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return true
        }
        return false
    }
}
