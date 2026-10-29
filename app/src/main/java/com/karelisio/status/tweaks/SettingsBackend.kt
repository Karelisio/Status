package com.karelisio.status.tweaks

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.karelisio.status.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Ns(val shell: String) { SYSTEM("system"), SECURE("secure"), GLOBAL("global") }

data class SettingKey(val ns: Ns, val key: String, val public: Boolean = false) {
    val id: String get() = "${ns.shell}/$key"

    companion object {
        fun parse(id: String): SettingKey? {
            val ns = Ns.entries.firstOrNull { id.startsWith(it.shell + "/") } ?: return null
            return SettingKey(ns, id.substringAfter('/'))
        }
    }
}

/** How a given setting can be written on this device right now. */
enum class Route { SHIZUKU, SECURE_SETTINGS, WRITE_SETTINGS, NONE }

sealed interface WriteResult {
    data object Ok : WriteResult
    data class Failed(val reason: String) : WriteResult
}

/**
 * Reads and writes system settings through the best available channel:
 * Shizuku/Shevery (`settings put`) → WRITE_SECURE_SETTINGS granted via ADB → WRITE_SETTINGS
 * (public System keys only).
 */
class SettingsBackend(private val context: Context) {
    private val cr get() = context.contentResolver

    fun hasSecureSettings(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun hasWriteSettings(): Boolean = Settings.System.canWrite(context)

    fun route(k: SettingKey): Route = when {
        ShizukuManager.status.value.ready -> Route.SHIZUKU
        k.ns != Ns.SYSTEM && hasSecureSettings() -> Route.SECURE_SETTINGS
        k.ns == Ns.SYSTEM && k.public && hasWriteSettings() -> Route.WRITE_SETTINGS
        else -> Route.NONE
    }

    fun canWrite(k: SettingKey) = route(k) != Route.NONE

    suspend fun read(k: SettingKey): String? = withContext(Dispatchers.IO) {
        val direct = runCatching {
            when (k.ns) {
                Ns.SYSTEM -> Settings.System.getString(cr, k.key)
                Ns.SECURE -> Settings.Secure.getString(cr, k.key)
                Ns.GLOBAL -> Settings.Global.getString(cr, k.key)
            }
        }
        if (direct.isSuccess) return@withContext direct.getOrNull()
        // Hidden keys may not be readable by apps on recent Android: ask the shell instead.
        if (ShizukuManager.status.value.ready) {
            val r = ShizukuManager.exec("settings get ${k.ns.shell} ${k.key}")
            if (r.ok) r.out.takeUnless { it == "null" || it.isEmpty() } else null
        } else null
    }

    suspend fun write(k: SettingKey, value: String?): WriteResult = withContext(Dispatchers.IO) {
        if (value != null && !SAFE_VALUE.matches(value)) return@withContext WriteResult.Failed("Valeur refusée")
        when (route(k)) {
            Route.SHIZUKU -> {
                val cmd = if (value == null) "settings delete ${k.ns.shell} ${k.key}"
                else "settings put ${k.ns.shell} ${k.key} $value"
                val r = ShizukuManager.exec(cmd)
                if (r.ok) WriteResult.Ok else WriteResult.Failed(r.err.ifBlank { "Code ${r.code}" })
            }
            Route.SECURE_SETTINGS, Route.WRITE_SETTINGS -> runCatching {
                val ok = when (k.ns) {
                    Ns.SYSTEM -> Settings.System.putString(cr, k.key, value)
                    Ns.SECURE -> Settings.Secure.putString(cr, k.key, value)
                    Ns.GLOBAL -> Settings.Global.putString(cr, k.key, value)
                }
                if (ok) WriteResult.Ok else WriteResult.Failed("Refusé par le système")
            }.getOrElse { WriteResult.Failed(it.message ?: "Refusé par le système") }
            Route.NONE -> WriteResult.Failed(
                if (k.ns == Ns.SYSTEM && !k.public) "Nécessite Shizuku / Shevery"
                else if (k.ns == Ns.SYSTEM) "Autorisez la modification des paramètres système"
                else "Nécessite Shizuku ou WRITE_SECURE_SETTINGS (ADB)",
            )
        }
    }

    /** Lists every key of a namespace (Shizuku only), used to detect OEM refresh-rate keys. */
    suspend fun list(ns: Ns): Map<String, String> {
        if (!ShizukuManager.status.value.ready) return emptyMap()
        val r = ShizukuManager.exec("settings list ${ns.shell}")
        if (!r.ok) return emptyMap()
        return r.out.lineSequence().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()
    }

    companion object {
        private val SAFE_VALUE = Regex("^[A-Za-z0-9._:\\-]{1,253}$")
    }
}
