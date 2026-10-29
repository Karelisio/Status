package com.karelisio.status.update

import android.content.Context
import com.karelisio.status.BuildConfig
import com.karelisio.status.data.prefs.SettingsStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GhAsset> = emptyList(),
) {
    val version: String get() = tag.removePrefix("v")
    val apk: GhAsset? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

/** Checks GitHub Releases (releases/latest) and downloads the APK. No account, no backend. */
class UpdateRepository(private val context: Context, private val store: SettingsStore) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun isCheckDue(): Boolean {
        val last = store.get(SettingsStore.Keys.lastUpdateCheck) ?: 0L
        return System.currentTimeMillis() - last >= CHECK_INTERVAL
    }

    /** Returns the latest release if it is newer than the installed version. */
    suspend fun check(): Result<GhRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://api.github.com/repos/${BuildConfig.RELEASES_REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Status/${BuildConfig.VERSION_NAME}")
            }
            try {
                when (conn.responseCode) {
                    200 -> {
                        val release = json.decodeFromString(GhRelease.serializer(), conn.inputStream.bufferedReader().readText())
                        store.set(SettingsStore.Keys.lastUpdateCheck, System.currentTimeMillis())
                        release.takeIf { !it.draft && it.apk != null && isNewer(it.version, BuildConfig.VERSION_NAME) }
                    }
                    404 -> { store.set(SettingsStore.Keys.lastUpdateCheck, System.currentTimeMillis()); null }
                    else -> error("GitHub a répondu ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun skipped(): String? = store.get(SettingsStore.Keys.skippedVersion)
    suspend fun skip(version: String) = store.set(SettingsStore.Keys.skippedVersion, version)

    /** Downloads the APK into the cache, reporting progress in 0..1. */
    suspend fun download(asset: GhAsset, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "status-update.apk")
        val conn = (URL(asset.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Status/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode !in 200..299) error("Téléchargement refusé (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: asset.size
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        target
    }

    companion object {
        const val CHECK_INTERVAL = 6 * 3_600_000L

        /** Semantic comparison of "1.10.2" style versions; suffixes like "-debug" are ignored. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
