package com.karelisio.status

import android.os.Bundle
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.karelisio.status.ui.StatusRoot
import com.karelisio.status.ui.theme.StatusTheme
import com.karelisio.status.update.UpdateController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val onboardingDone = MutableStateFlow<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Keep the animated splash until we know whether to show onboarding.
        splash.setKeepOnScreenCondition { onboardingDone.value == null }
        splash.setOnExitAnimationListener { provider ->
            val duration = 380L
            provider.iconView.animate()
                .scaleX(1.6f).scaleY(1.6f).alpha(0f)
                .setDuration(duration)
                .setInterpolator(AnticipateInterpolator(1.4f))
                .start()
            provider.view.animate().alpha(0f).setStartDelay(120).setDuration(duration).withEndAction { provider.remove() }.start()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            onboardingDone.value = AppGraph.settings.current().onboardingDone
            UpdateController.checkIfDue()
        }

        setContent {
            StatusTheme {
                val done = onboardingDone.value
                if (done != null) StatusRoot(startWithOnboarding = !done)
            }
        }
    }
}
