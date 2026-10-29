package com.karelisio.status.tiles

import com.karelisio.status.AppGraph
import com.karelisio.status.R
import com.karelisio.status.data.prefs.SettingsStore
import com.karelisio.status.tweaks.DnsProvider
import com.karelisio.status.tweaks.TweakKeys

class CaffeineTile : BaseTile() {
    override val label = "Caféine"
    override val iconRes = R.drawable.ic_tile_caffeine
    override suspend fun read(): TileUi {
        val active = AppGraph.settings.get(SettingsStore.Keys.caffeineActive) == true
        return TileUi(active, if (active) "Écran allumé" else "Désactivée", AppGraph.tweaks.backend.canWrite(TweakKeys.screenOffTimeout))
    }
    override suspend fun click() {
        val active = AppGraph.settings.get(SettingsStore.Keys.caffeineActive) == true
        AppGraph.tweaks.setCaffeine(!active, null)
    }
}

class DnsTile : BaseTile() {
    override val label = "DNS privé"
    override val iconRes = R.drawable.ic_tile_dns
    override suspend fun read(): TileUi {
        val st = AppGraph.tweaks.state()
        val p = st.dnsProvider
        val on = p != DnsProvider.OFF
        return TileUi(on, if (p == DnsProvider.CUSTOM) st.dnsHost else p.label, AppGraph.tweaks.backend.canWrite(TweakKeys.privateDnsMode))
    }
    override suspend fun click() {
        val st = AppGraph.tweaks.state()
        if (st.dnsProvider != DnsProvider.OFF) {
            AppGraph.tweaks.setDns(DnsProvider.OFF)
        } else {
            val last = AppGraph.settings.get(SettingsStore.Keys.lastDnsSpecifier)
                ?.let { n -> DnsProvider.entries.firstOrNull { it.name == n } } ?: DnsProvider.ADGUARD
            AppGraph.tweaks.setDns(last, AppGraph.settings.get(SettingsStore.Keys.customDns))
        }
    }
}

class AnimationTile : BaseTile() {
    override val label = "Animations"
    override val iconRes = R.drawable.ic_tile_animation
    private val cycle = listOf(1f, 0.5f, 0f)
    override suspend fun read(): TileUi {
        val s = AppGraph.tweaks.state().animScale ?: 1f
        return TileUi(s != 1f, label(s), AppGraph.tweaks.backend.canWrite(TweakKeys.windowAnim))
    }
    override suspend fun click() {
        val s = AppGraph.tweaks.state().animScale ?: 1f
        val i = cycle.indexOfFirst { it == s }.let { if (it < 0) 0 else it }
        AppGraph.tweaks.setAnimationScale(cycle[(i + 1) % cycle.size])
    }
    private fun label(s: Float) = when (s) { 0f -> "Désactivées"; 0.5f -> "0,5x"; 1f -> "1x"; else -> "${s}x" }
}

class RefreshTile : BaseTile() {
    override val label = "Fréquence"
    override val iconRes = R.drawable.ic_tile_refresh
    override suspend fun read(): TileUi {
        val st = AppGraph.tweaks.state()
        val forced = st.peakRefresh?.toInt()?.takeIf { st.minRefresh?.toInt() == it }
        return TileUi(forced != null, "${st.measuredHz} Hz", AppGraph.tweaks.backend.canWrite(TweakKeys.peakRefresh))
    }
    override suspend fun click() {
        val rates = AppGraph.tweaks.supportedRefreshRates().filter { it in listOf(60, 90, 120) }.ifEmpty { listOf(60) }
        val current = AppGraph.tweaks.measuredRefreshHz()
        val next = rates.firstOrNull { it > current } ?: rates.first()
        if (AppGraph.tweaks.oemRefreshKeys.isEmpty()) AppGraph.tweaks.detectOemRefreshKeys()
        AppGraph.tweaks.setRefreshRate(next)
    }
}

class PresetTile : BaseTile() {
    override val label = "Préréglage"
    override val iconRes = R.drawable.ic_tile_preset
    private suspend fun target() = AppGraph.tweaks.loadPresets().let { list ->
        val id = AppGraph.settings.get(SettingsStore.Keys.tilePresetId)
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }
    override suspend fun read(): TileUi {
        val active = AppGraph.settings.get(SettingsStore.Keys.activePresetId)
        val presets = AppGraph.tweaks.loadPresets()
        val shown = presets.firstOrNull { it.id == active } ?: target()
        return TileUi(active != null, shown?.name, AppGraph.tweaks.backend.canWrite(TweakKeys.screenOffTimeout) && shown != null)
    }
    override suspend fun click() {
        if (AppGraph.settings.get(SettingsStore.Keys.activePresetId) != null) AppGraph.tweaks.restorePreset()
        else target()?.let { AppGraph.tweaks.applyPreset(it.id) }
    }
}
