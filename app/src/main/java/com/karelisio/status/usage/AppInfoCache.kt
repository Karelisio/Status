package com.karelisio.status.usage

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

data class AppLabel(val packageName: String, val label: String, val system: Boolean)

/** Resolves package names to labels / icons, cached for the process lifetime. */
class AppInfoCache(private val context: Context) {
    private val pm = context.packageManager
    private val labels = ConcurrentHashMap<String, AppLabel>()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    fun label(pkg: String): AppLabel = labels.getOrPut(pkg) {
        runCatching {
            val info = pm.getApplicationInfo(pkg, 0)
            AppLabel(pkg, pm.getApplicationLabel(info).toString(), info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0)
        }.getOrElse { AppLabel(pkg, pkg, false) }
    }

    fun icon(pkg: String): ImageBitmap? = icons[pkg] ?: runCatching {
        val d: Drawable = pm.getApplicationIcon(pkg)
        d.toBitmap(96, 96).asImageBitmap().also { icons[pkg] = it }
    }.getOrNull()

    fun packagesForUid(uid: Int): List<String> = pm.getPackagesForUid(uid)?.toList().orEmpty()

    fun isInstalled(pkg: String): Boolean = runCatching { pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS); true }.getOrDefault(false)
}
