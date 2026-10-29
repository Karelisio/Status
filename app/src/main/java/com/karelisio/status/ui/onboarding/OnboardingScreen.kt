package com.karelisio.status.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.karelisio.status.AppGraph
import com.karelisio.status.data.prefs.SettingsStore
import com.karelisio.status.permissions.AppPermission
import com.karelisio.status.permissions.PermissionCatalog
import com.karelisio.status.ui.components.PermissionHandle
import com.karelisio.status.ui.components.rememberPermissionHandle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private sealed interface Page {
    data object Welcome : Page
    data object Features : Page
    data class Perm(val p: AppPermission) : Page
    data object Done : Page
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = remember { listOf(Page.Welcome, Page.Features) + PermissionCatalog.all().map { Page.Perm(it) } + Page.Done }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    fun next() = scope.launch {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pages.lastIndex))
    }
    val finish: () -> Unit = {
        scope.launch {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            AppGraph.settings.set(SettingsStore.Keys.onboardingDone, true)
            AppGraph.ensureMonitor()
            onDone()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                if (pager.currentPage < pages.lastIndex) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages.lastIndex) } }) { Text("Passer") }
                else Spacer(Modifier.height(48.dp))
            }
            HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1) { index ->
                val offset = pager.pageOffset(index)
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        // Depth-style transition: outgoing page shrinks and fades.
                        val f = 1f - offset.absoluteValue.coerceIn(0f, 1f)
                        alpha = lerp(0.3f, 1f, f)
                        val s = lerp(0.88f, 1f, f)
                        scaleX = s; scaleY = s
                    },
                ) {
                    when (val page = pages[index]) {
                        Page.Welcome -> InfoPage(
                            illustration = { WelcomeIllustration() },
                            title = "Bienvenue dans Status",
                            text = "Le tableau de bord vivant de votre téléphone : batterie, réseau, data, temps d'écran et tweaks système. 100 % local, sans compte.",
                            button = "Commencer", onButton = { next() },
                        )
                        Page.Features -> InfoPage(
                            illustration = {
                                FeaturesIllustration(
                                    listOf(Icons.Rounded.BatteryChargingFull, Icons.Rounded.NetworkCheck, Icons.Rounded.Insights, Icons.Rounded.NotificationsActive, Icons.Rounded.Tune, Icons.Rounded.Widgets),
                                )
                            },
                            title = "Tout, en temps réel",
                            text = "Puissance de charge réelle, débit en direct, historique sur 30 jours, alertes intelligentes, tuiles et widget. Quelques autorisations sont nécessaires, une par une.",
                            button = "Suivant", onButton = { next() },
                        )
                        is Page.Perm -> PermissionPage(page.p, onNext = { next() }, active = pager.currentPage == index)
                        Page.Done -> InfoPage(
                            illustration = { PermissionIllustration(Icons.Rounded.Celebration, granted = pager.currentPage == index) },
                            title = "C'est prêt",
                            text = "Vous pourrez revoir chaque autorisation dans Plus › Autorisations.",
                            button = "Ouvrir Status", onButton = finish,
                        )
                    }
                }
            }
            PageIndicator(pager, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp))
        }
    }
}

private fun PagerState.pageOffset(page: Int): Float = (currentPage - page) + currentPageOffsetFraction

@Composable
private fun InfoPage(illustration: @Composable () -> Unit, title: String, text: String, button: String, onButton: () -> Unit, secondary: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { illustration() }
        Text(title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onButton, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(button, style = MaterialTheme.typography.titleMedium) }
        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) { secondary?.invoke() }
    }
}

@Composable
private fun PermissionPage(p: AppPermission, onNext: () -> Unit, active: Boolean) {
    val handle: PermissionHandle = rememberPermissionHandle(p)
    val haptics = LocalHapticFeedback.current
    // Auto-advance shortly after the permission becomes granted while this page is visible.
    LaunchedEffect(handle.granted, active) {
        if (handle.granted && active) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(900)
            onNext()
        }
    }
    InfoPage(
        illustration = { PermissionIllustration(p.icon, handle.granted) },
        title = p.title,
        text = p.description,
        button = when {
            handle.granted -> "Suivant"
            handle.confirmManually != null -> "Ouvrir les réglages"
            else -> "Autoriser"
        },
        onButton = { if (handle.granted) onNext() else handle.request() },
        secondary = {
            AnimatedContent(handle.granted, transitionSpec = { (fadeIn() + scaleIn()) togetherWith fadeOut() }, label = "sec") { granted ->
                when {
                    granted -> Text("Accordée", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    handle.confirmManually != null -> TextButton(onClick = { handle.confirmManually.invoke() }) { Text("C'est fait") }
                    p.optional -> TextButton(onClick = onNext) { Text("Plus tard") }
                    else -> TextButton(onClick = onNext) { Text("Ignorer") }
                }
            }
        },
    )
}

@Composable
private fun PageIndicator(pager: PagerState, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pager.pageCount) { i ->
            val selected = pager.currentPage == i
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, spring(dampingRatio = 0.6f, stiffness = 400f), label = "w")
            val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "c")
            Box(Modifier.height(8.dp).width(width).background(color, CircleShape))
        }
    }
}
