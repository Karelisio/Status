package com.karelisio.status.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.karelisio.status.ui.components.LocalNavAnimatedScope
import com.karelisio.status.ui.components.LocalSharedScope
import com.karelisio.status.ui.nav.DashboardRoute
import com.karelisio.status.ui.nav.HistoryRoute
import com.karelisio.status.ui.nav.MoreRoute
import com.karelisio.status.ui.nav.TweaksRoute
import com.karelisio.status.ui.nav.UsageRoute
import com.karelisio.status.ui.screens.DashboardScreen
import com.karelisio.status.ui.screens.HistoryScreen
import com.karelisio.status.ui.screens.UsageScreen
import com.karelisio.status.ui.screens.BatteryDetailScreen
import com.karelisio.status.ui.nav.BatteryDetailRoute
import com.karelisio.status.ui.screens.PlaceholderScreen
import kotlin.reflect.KClass

private data class TopLevel(val route: Any, val klass: KClass<*>, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val topLevels = listOf(
    TopLevel(DashboardRoute, DashboardRoute::class, "Live", Icons.Outlined.Speed, Icons.Rounded.Speed),
    TopLevel(HistoryRoute, HistoryRoute::class, "Historique", Icons.Outlined.Insights, Icons.Rounded.Insights),
    TopLevel(UsageRoute, UsageRoute::class, "Usage", Icons.Outlined.DataUsage, Icons.Rounded.DataUsage),
    TopLevel(TweaksRoute, TweaksRoute::class, "Tweaks", Icons.Outlined.Tune, Icons.Rounded.Tune),
    TopLevel(MoreRoute, MoreRoute::class, "Plus", Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz),
)

@Composable
fun StatusRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val haptics = LocalHapticFeedback.current
    val showBar = topLevels.any { entry?.destination?.hasRoute(it.klass) == true }

    Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalSharedScope provides this) {
                NavHost(
                    navController = nav,
                    startDestination = DashboardRoute,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(320), initialScale = 0.96f) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 1.04f) },
                    popExitTransition = { fadeOut(tween(200)) + scaleOut(tween(300), targetScale = 0.92f) },
                ) {
                    statusGraph(nav)
                }
            }
        }
        AnimatedVisibility(
            visible = showBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                topLevels.forEach { item ->
                    val selected = entry?.destination?.hierarchy()?.any { it.hasRoute(item.klass) } == true
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            nav.navigateTopLevel(item.route)
                        },
                        icon = { Icon(if (selected) item.selectedIcon else item.icon, null) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
    }
}

private fun androidx.navigation.NavDestination.hierarchy() = generateSequence(this) { it.parent }

private fun NavHostController.navigateTopLevel(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Wraps a destination so its content can use shared elements with the nav animation scope. */
inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit) {
    composable<T>(
        enterTransition = null,
    ) { backStackEntry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(backStackEntry) }
    }
}

private fun NavGraphBuilder.statusGraph(nav: NavHostController) {
    screen<DashboardRoute> { DashboardScreen(onOpen = { nav.navigate(it) }) }
    screen<HistoryRoute> { HistoryScreen() }
    screen<BatteryDetailRoute> { BatteryDetailScreen(onBack = { nav.popBackStack() }) }
    screen<UsageRoute> { UsageScreen() }
    screen<TweaksRoute> { PlaceholderScreen("Tweaks") }
    screen<MoreRoute> { PlaceholderScreen("Plus") }
}
