package com.karelisio.status.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute
@Serializable data object HistoryRoute
@Serializable data object UsageRoute
@Serializable data object TweaksRoute
@Serializable data object MoreRoute

@Serializable data object BatteryDetailRoute
@Serializable data object AlertsRoute
@Serializable data object PermissionsRoute
@Serializable data object AdvancedAccessRoute
@Serializable data object SystemAppsRoute
@Serializable data object ForceStopRoute
@Serializable data class PresetEditRoute(val id: String)
@Serializable data object OnboardingRoute
@Serializable data object AboutRoute
