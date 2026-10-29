package com.karelisio.status.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit

data class ShizukuStatus(
    /** A Shizuku-compatible service (Shizuku, Shevery, forks…) answered pingBinder(). */
    val running: Boolean = false,
    val permissionGranted: Boolean = false,
    val version: Int? = null,
    /** 0 = root, 2000 = adb shell. */
    val uid: Int? = null,
    val unsupported: Boolean = false,
) {
    val ready: Boolean get() = running && permissionGranted && !unsupported
    val label: String
        get() = when {
            !running -> "Service non détecté"
            unsupported -> "Version trop ancienne"
            !permissionGranted -> "Permission à accorder"
            uid == 0 -> "Actif (root)"
            else -> "Actif (ADB)"
        }
}

data class ExecResult(val code: Int, val out: String, val err: String) {
    val ok: Boolean get() = code == 0
}

/**
 * Shizuku-API client. The service is detected only through the binder (pingBinder / binder
 * listeners), never by package name, so it works with Shizuku, Shevery and any fork that
 * implements the same protocol.
 */
object ShizukuManager {
    private const val REQUEST_CODE = 4242
    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == REQUEST_CODE) refresh()
    }
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            _status.value = ShizukuStatus()
            return
        }
        val preV11 = runCatching { Shizuku.isPreV11() }.getOrDefault(false)
        val granted = !preV11 && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        _status.value = ShizukuStatus(
            running = true,
            permissionGranted = granted,
            version = runCatching { Shizuku.getVersion() }.getOrNull(),
            uid = runCatching { Shizuku.getUid() }.getOrNull(),
            unsupported = preV11,
        )
    }

    fun requestPermission() {
        refresh()
        val s = _status.value
        if (!s.running || s.unsupported || s.permissionGranted) return
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /** Runs a shell command through the Shizuku service (as shell or root). */
    suspend fun exec(command: String, timeoutSec: Long = 15): ExecResult = withContext(Dispatchers.IO) {
        if (!_status.value.ready) return@withContext ExecResult(-1, "", "Shizuku indisponible")
        runCatching {
            val process = newProcess(arrayOf("sh", "-c", command))
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) process.destroy()
            ExecResult(if (finished) process.exitValue() else -2, out.trim(), err.trim())
        }.getOrElse { ExecResult(-1, "", it.message ?: it.javaClass.simpleName) }
    }

    // Shizuku.newProcess is hidden in API 13 in favour of user services; it is still the simplest
    // way to run one-off shell commands and is supported by every Shizuku-compatible server.
    private fun newProcess(cmd: Array<String>): ShizukuRemoteProcess {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as ShizukuRemoteProcess
    }
}
