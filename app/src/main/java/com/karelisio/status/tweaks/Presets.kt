package com.karelisio.status.tweaks

import kotlinx.serialization.Serializable

@Serializable
data class PresetValues(
    val animScale: Float? = null,
    val refreshHz: Int? = null,
    val timeoutMs: Int? = null,
    val brightness: Int? = null,
    val autoBrightness: Boolean? = null,
    val autoRotate: Boolean? = null,
    val dns: DnsProvider? = null,
    val dnsCustom: String? = null,
)

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val icon: String,
    val values: PresetValues,
    val builtIn: Boolean = false,
)

object DefaultPresets {
    val game = Preset(
        id = "game", name = "Jeu", icon = "game", builtIn = true,
        values = PresetValues(animScale = 0.5f, refreshHz = 120, timeoutMs = 10 * 60_000, autoRotate = true, dns = DnsProvider.ADGUARD),
    )
    val eco = Preset(
        id = "eco", name = "Économie", icon = "eco", builtIn = true,
        values = PresetValues(animScale = 0.5f, refreshHz = 60, timeoutMs = 30_000, autoBrightness = true, autoRotate = false),
    )
    val night = Preset(
        id = "night", name = "Nuit", icon = "night", builtIn = true,
        values = PresetValues(refreshHz = 60, timeoutMs = 30_000, autoBrightness = false, brightness = 18, autoRotate = false),
    )
    val all = listOf(game, eco, night)
}
