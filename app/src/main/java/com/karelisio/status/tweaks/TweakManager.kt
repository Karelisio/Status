package com.karelisio.status.tweaks

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.karelisio.status.data.prefs.SettingsStore
import com.karelisio.status.shizuku.ShizukuManager
import com.karelisio.status.shizuku.ExecResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** Snapshot of the tweakable state, for the UI. */
data class TweakState(
    val animScale: Float?,
    val peakRefresh: Float?,
    val minRefresh: Float?,
    val measuredHz: Int,
    val supportedHz: List<Int>,
    val timeoutMs: Int?,
    val brightness: Int?,
    val autoBrightness: Boolean?,
    val autoRotate: Boolean?,
    val dnsMode: String?,
    val dnsHost: String?,
    val caffeine: Boolean,
    val caffeineUntil: Long?,
    val activePreset: String?,
    val backupCount: Int,
    val disabledCount: Int,
) {
    val dnsProvider: DnsProvider
        get() = when (dnsMode) {
            null, "off" -> DnsProvider.OFF
            "opportunistic" -> DnsProvider.AUTO
            "hostname" -> DnsProvider.entries.firstOrNull { it.host != null && it.host == dnsHost } ?: DnsProvider.CUSTOM
            else -> DnsProvider.OFF
        }
}

data class OemRefreshKey(val key: SettingKey, val value: String)

class TweakManager(
    private val context: Context,
    val backend: SettingsBackend,
    private val store: SettingsStore,
    val backup: TweakBackup,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val presetsSer = ListSerializer(Preset.serializer())
    private val stateSer = MapSerializer(String.serializer(), String.serializer().nullable)
    private val mutex = Mutex()
    @Volatile var oemRefreshKeys: List<OemRefreshKey> = emptyList()
        private set

    // ---- Generic write with backup ----

    suspend fun set(key: SettingKey, value: String?): WriteResult {
        val current = backend.read(key)
        backup.rememberOriginal(key, current)
        if (current == value) return WriteResult.Ok
        return backend.write(key, value)
    }

    private suspend fun setAll(values: List<Pair<SettingKey, String?>>): WriteResult {
        var result: WriteResult = WriteResult.Ok
        values.forEach { (k, v) -> set(k, v).let { if (it is WriteResult.Failed) result = it } }
        return result
    }

    // ---- Individual tweaks ----

    suspend fun setAnimationScale(scale: Float): WriteResult {
        val v = if (scale == 0f) "0" else scale.toString()
        return setAll(TweakKeys.animationKeys.map { it to v })
    }

    fun display(): Display? = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    fun measuredRefreshHz(): Int = display()?.refreshRate?.roundToInt() ?: 60

    fun supportedRefreshRates(): List<Int> {
        val d = display() ?: return listOf(60)
        val mode = d.mode
        return d.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { it.refreshRate.roundToInt() }
            .filter { it in 50..165 }
            .distinct().sorted()
            .ifEmpty { listOf(60) }
    }

    /** Finds OEM-specific refresh-rate settings (e.g. MagicOS), via `settings list`. */
    suspend fun detectOemRefreshKeys(): List<OemRefreshKey> {
        val found = Ns.entries.flatMap { ns ->
            backend.list(ns).filterKeys { TweakKeys.refreshKeyPattern.containsMatchIn(it) }
                .map { (k, v) -> OemRefreshKey(SettingKey(ns, k), v) }
        }.filterNot { it.key == TweakKeys.peakRefresh || it.key == TweakKeys.minRefresh }
        oemRefreshKeys = found
        return found
    }

    /**
     * Forces the refresh rate: AOSP peak/min keys, plus detected OEM keys whose current value looks
     * like a rate. Returns the rate measured on the display afterwards, to verify the effect.
     */
    suspend fun setRefreshRate(hz: Int): Pair<WriteResult, Int> {
        val f = "$hz.0"
        val writes = mutableListOf<Pair<SettingKey, String?>>(TweakKeys.peakRefresh to f, TweakKeys.minRefresh to f)
        oemRefreshKeys.forEach { oem ->
            val num = oem.value.toFloatOrNull()
            if (num != null && num.roundToInt() in setOf(60, 90, 120, 144)) {
                writes += oem.key to if (oem.value.contains('.')) f else hz.toString()
            }
        }
        val result = setAll(writes)
        delay(700)
        return result to measuredRefreshHz()
    }

    suspend fun setScreenTimeout(ms: Int) = set(TweakKeys.screenOffTimeout, ms.toString())
    suspend fun setBrightness(level: Int) = set(TweakKeys.brightness, level.coerceIn(1, 255).toString())
    suspend fun setAutoBrightness(on: Boolean) = set(TweakKeys.brightnessMode, if (on) "1" else "0")
    suspend fun setAutoRotate(on: Boolean) = set(TweakKeys.autoRotate, if (on) "1" else "0")

    suspend fun setDns(provider: DnsProvider, custom: String? = null): WriteResult {
        val result = when (provider) {
            DnsProvider.OFF -> set(TweakKeys.privateDnsMode, "off")
            DnsProvider.AUTO -> set(TweakKeys.privateDnsMode, "opportunistic")
            DnsProvider.CUSTOM -> {
                val host = custom?.trim()?.takeIf { HOST.matches(it) } ?: return WriteResult.Failed("Nom d'hôte invalide")
                store.set(SettingsStore.Keys.customDns, host)
                setAll(listOf(TweakKeys.privateDnsSpecifier to host, TweakKeys.privateDnsMode to "hostname"))
            }
            else -> setAll(listOf(TweakKeys.privateDnsSpecifier to provider.host, TweakKeys.privateDnsMode to "hostname"))
        }
        if (result is WriteResult.Ok && provider != DnsProvider.OFF) {
            store.set(SettingsStore.Keys.lastDnsSpecifier, provider.name)
        }
        return result
    }

    // ---- Caffeine ----

    suspend fun setCaffeine(on: Boolean, durationMin: Int? = null): WriteResult = mutex.withLock {
        val active = store.get(SettingsStore.Keys.caffeineActive) == true
        if (on) {
            if (!active) {
                val current = backend.read(TweakKeys.screenOffTimeout)?.toIntOrNull() ?: 60_000
                store.set(SettingsStore.Keys.caffeineOriginalTimeout, current)
            }
            val r = set(TweakKeys.screenOffTimeout, CAFFEINE_TIMEOUT.toString())
            if (r is WriteResult.Ok) {
                store.set(SettingsStore.Keys.caffeineActive, true)
                val until = durationMin?.let { System.currentTimeMillis() + it * 60_000L }
                store.set(SettingsStore.Keys.caffeineUntil, until)
                CaffeineScheduler.schedule(context, durationMin)
                CaffeineScheduler.showNotification(context, until)
            }
            r
        } else {
            val original = store.get(SettingsStore.Keys.caffeineOriginalTimeout) ?: 60_000
            val r = if (active) backend.write(TweakKeys.screenOffTimeout, original.toString()) else WriteResult.Ok
            store.edit {
                it.remove(SettingsStore.Keys.caffeineActive)
                it.remove(SettingsStore.Keys.caffeineUntil)
            }
            CaffeineScheduler.cancel(context)
            r
        }
    }

    // ---- Presets ----

    val presets: Flow<List<Preset>> = store.raw.map { p -> decodePresets(p[SettingsStore.Keys.presetsJson]) }

    private fun decodePresets(raw: String?): List<Preset> =
        raw?.let { runCatching { json.decodeFromString(presetsSer, it) }.getOrNull() } ?: DefaultPresets.all

    suspend fun loadPresets(): List<Preset> = decodePresets(store.get(SettingsStore.Keys.presetsJson))

    suspend fun savePreset(preset: Preset) {
        val list = loadPresets().toMutableList()
        val i = list.indexOfFirst { it.id == preset.id }
        if (i >= 0) list[i] = preset else list += preset
        store.set(SettingsStore.Keys.presetsJson, json.encodeToString(presetsSer, list))
    }

    suspend fun deletePreset(id: String) {
        val list = loadPresets().filterNot { it.id == id }
        store.set(SettingsStore.Keys.presetsJson, json.encodeToString(presetsSer, list))
    }

    suspend fun resetPresets() = store.set(SettingsStore.Keys.presetsJson, null)

    private fun keysFor(v: PresetValues): List<SettingKey> = buildList {
        if (v.animScale != null) addAll(TweakKeys.animationKeys)
        if (v.refreshHz != null) { add(TweakKeys.peakRefresh); add(TweakKeys.minRefresh); addAll(oemRefreshKeys.map { it.key }) }
        if (v.timeoutMs != null) add(TweakKeys.screenOffTimeout)
        if (v.brightness != null) add(TweakKeys.brightness)
        if (v.autoBrightness != null || v.brightness != null) add(TweakKeys.brightnessMode)
        if (v.autoRotate != null) add(TweakKeys.autoRotate)
        if (v.dns != null) { add(TweakKeys.privateDnsMode); add(TweakKeys.privateDnsSpecifier) }
    }

    /** Applies a preset, saving the exact previous state of every key it touches. */
    suspend fun applyPreset(id: String): WriteResult {
        val preset = loadPresets().firstOrNull { it.id == id } ?: return WriteResult.Failed("Préréglage inconnu")
        if (store.get(SettingsStore.Keys.activePresetId) != null) restorePreset()
        if (preset.values.refreshHz != null && oemRefreshKeys.isEmpty()) detectOemRefreshKeys()
        val v = preset.values
        val previous = keysFor(v).associate { it.id to backend.read(it) }
        store.set(SettingsStore.Keys.prePresetJson, json.encodeToString(stateSer, previous))
        store.set(SettingsStore.Keys.activePresetId, id)

        var result: WriteResult = WriteResult.Ok
        fun track(r: WriteResult) { if (r is WriteResult.Failed) result = r }
        v.animScale?.let { track(setAnimationScale(it)) }
        v.refreshHz?.let { hz ->
            val supported = supportedRefreshRates()
            val target = supported.lastOrNull { it <= hz } ?: supported.first()
            track(setRefreshRate(target).first)
        }
        v.timeoutMs?.let { track(setScreenTimeout(it)) }
        if (v.brightness != null) {
            track(setAutoBrightness(false)); track(setBrightness(v.brightness))
        } else v.autoBrightness?.let { track(setAutoBrightness(it)) }
        v.autoRotate?.let { track(setAutoRotate(it)) }
        v.dns?.let { track(setDns(it, v.dnsCustom)) }
        return result
    }

    /** Puts back the state saved just before the active preset was applied. */
    suspend fun restorePreset(): WriteResult {
        val raw = store.get(SettingsStore.Keys.prePresetJson)
        val previous = raw?.let { runCatching { json.decodeFromString(stateSer, it) }.getOrNull() }.orEmpty()
        var result: WriteResult = WriteResult.Ok
        previous.forEach { (id, value) ->
            SettingKey.parse(id)?.let { k -> backend.write(k, value).let { if (it is WriteResult.Failed) result = it } }
        }
        store.edit {
            it.remove(SettingsStore.Keys.prePresetJson)
            it.remove(SettingsStore.Keys.activePresetId)
        }
        return result
    }

    // ---- Packages ----

    suspend fun disablePackage(pkg: String): ExecResult {
        if (!PKG.matches(pkg)) return ExecResult(-1, "", "Paquet invalide")
        val r = ShizukuManager.exec("pm disable-user --user 0 $pkg")
        if (r.ok) backup.markDisabled(pkg, true)
        return r
    }

    suspend fun enablePackage(pkg: String): ExecResult {
        if (!PKG.matches(pkg)) return ExecResult(-1, "", "Paquet invalide")
        val r = ShizukuManager.exec("pm enable --user 0 $pkg")
        if (r.ok) backup.markDisabled(pkg, false)
        return r
    }

    suspend fun forceStop(pkg: String): ExecResult {
        if (!PKG.matches(pkg)) return ExecResult(-1, "", "Paquet invalide")
        return ShizukuManager.exec("am force-stop $pkg")
    }

    // ---- Restore everything ----

    suspend fun restoreAll(): List<String> {
        val errors = mutableListOf<String>()
        if (store.get(SettingsStore.Keys.caffeineActive) == true) CaffeineScheduler.cancel(context)
        store.edit {
            it.remove(SettingsStore.Keys.caffeineActive)
            it.remove(SettingsStore.Keys.caffeineUntil)
            it.remove(SettingsStore.Keys.prePresetJson)
            it.remove(SettingsStore.Keys.activePresetId)
        }
        backup.load().forEach { (id, value) ->
            val k = SettingKey.parse(id) ?: return@forEach
            val r = backend.write(k, value)
            if (r is WriteResult.Failed) errors += "${k.key} : ${r.reason}"
        }
        if (errors.isEmpty()) backup.clear()
        backup.loadDisabled().forEach { pkg ->
            val r = enablePackage(pkg)
            if (!r.ok) errors += "$pkg : ${r.err}"
        }
        return errors
    }

    suspend fun state(): TweakState {
        val p = store.raw.first()
        return TweakState(
            animScale = backend.read(TweakKeys.animatorDuration)?.toFloatOrNull(),
            peakRefresh = backend.read(TweakKeys.peakRefresh)?.toFloatOrNull(),
            minRefresh = backend.read(TweakKeys.minRefresh)?.toFloatOrNull(),
            measuredHz = measuredRefreshHz(),
            supportedHz = supportedRefreshRates(),
            timeoutMs = backend.read(TweakKeys.screenOffTimeout)?.toIntOrNull(),
            brightness = backend.read(TweakKeys.brightness)?.toIntOrNull(),
            autoBrightness = backend.read(TweakKeys.brightnessMode)?.let { it == "1" },
            autoRotate = backend.read(TweakKeys.autoRotate)?.let { it == "1" },
            dnsMode = backend.read(TweakKeys.privateDnsMode),
            dnsHost = backend.read(TweakKeys.privateDnsSpecifier),
            caffeine = p[SettingsStore.Keys.caffeineActive] == true,
            caffeineUntil = p[SettingsStore.Keys.caffeineUntil],
            activePreset = p[SettingsStore.Keys.activePresetId],
            backupCount = backup.load().size,
            disabledCount = backup.loadDisabled().size,
        )
    }

    companion object {
        const val CAFFEINE_TIMEOUT = Int.MAX_VALUE
        private val HOST = Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")
        private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    }
}
