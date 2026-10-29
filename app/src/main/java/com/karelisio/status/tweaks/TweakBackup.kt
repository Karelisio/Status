package com.karelisio.status.tweaks

import com.karelisio.status.data.prefs.SettingsStore
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Remembers the factory value of every setting the first time Status changes it, and every
 * package it disabled, so that "Tout restaurer" can put the phone back exactly as it was.
 */
class TweakBackup(private val store: SettingsStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSer = MapSerializer(String.serializer(), String.serializer().nullable)
    private val setSer = SetSerializer(String.serializer())
    private val mutex = Mutex()

    val originals: Flow<Map<String, String?>> = store.raw.map { p ->
        p[SettingsStore.Keys.originalsJson]?.let { runCatching { json.decodeFromString(mapSer, it) }.getOrNull() } ?: emptyMap()
    }

    val disabledPackages: Flow<Set<String>> = store.raw.map { p ->
        p[SettingsStore.Keys.disabledByApp]?.let { runCatching { json.decodeFromString(setSer, it) }.getOrNull() } ?: emptySet()
    }

    suspend fun rememberOriginal(key: SettingKey, current: String?) = mutex.withLock {
        val map = load().toMutableMap()
        if (!map.containsKey(key.id)) {
            map[key.id] = current
            store.set(SettingsStore.Keys.originalsJson, json.encodeToString(mapSer, map))
        }
    }

    suspend fun load(): Map<String, String?> =
        store.get(SettingsStore.Keys.originalsJson)?.let { runCatching { json.decodeFromString(mapSer, it) }.getOrNull() } ?: emptyMap()

    suspend fun clear() = store.set(SettingsStore.Keys.originalsJson, null)

    suspend fun markDisabled(pkg: String, disabled: Boolean) = mutex.withLock {
        val set = loadDisabled().toMutableSet()
        if (disabled) set += pkg else set -= pkg
        store.set(SettingsStore.Keys.disabledByApp, json.encodeToString(setSer, set))
    }

    suspend fun loadDisabled(): Set<String> =
        store.get(SettingsStore.Keys.disabledByApp)?.let { runCatching { json.decodeFromString(setSer, it) }.getOrNull() } ?: emptySet()
}
