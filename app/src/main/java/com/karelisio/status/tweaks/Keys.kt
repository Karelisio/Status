package com.karelisio.status.tweaks

/** Every system setting Status may touch. */
object TweakKeys {
    val windowAnim = SettingKey(Ns.GLOBAL, "window_animation_scale")
    val transitionAnim = SettingKey(Ns.GLOBAL, "transition_animation_scale")
    val animatorDuration = SettingKey(Ns.GLOBAL, "animator_duration_scale")
    val animationKeys = listOf(windowAnim, transitionAnim, animatorDuration)

    val peakRefresh = SettingKey(Ns.SYSTEM, "peak_refresh_rate")
    val minRefresh = SettingKey(Ns.SYSTEM, "min_refresh_rate")

    val screenOffTimeout = SettingKey(Ns.SYSTEM, "screen_off_timeout", public = true)
    val brightness = SettingKey(Ns.SYSTEM, "screen_brightness", public = true)
    val brightnessMode = SettingKey(Ns.SYSTEM, "screen_brightness_mode", public = true)
    val autoRotate = SettingKey(Ns.SYSTEM, "accelerometer_rotation", public = true)

    val privateDnsMode = SettingKey(Ns.GLOBAL, "private_dns_mode")
    val privateDnsSpecifier = SettingKey(Ns.GLOBAL, "private_dns_specifier")

    /** Matches OEM keys that look refresh-rate related (MagicOS / EMUI use their own). */
    val refreshKeyPattern = Regex("(refresh|fps|frame_rate|screen_rate|high_rate)", RegexOption.IGNORE_CASE)
}

enum class DnsProvider(val label: String, val host: String?) {
    OFF("Désactivé", null),
    AUTO("Automatique", null),
    ADGUARD("AdGuard", "dns.adguard-dns.com"),
    CLOUDFLARE("Cloudflare", "one.one.one.one"),
    QUAD9("Quad9", "dns.quad9.net"),
    CUSTOM("Personnalisé", null),
}
