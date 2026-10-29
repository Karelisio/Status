package com.karelisio.status

import android.app.Application
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.karelisio.status.data.db.SnapshotEntity
import com.karelisio.status.metrics.BatteryInfo
import com.karelisio.status.metrics.CoreInfo
import com.karelisio.status.metrics.CpuInfo
import com.karelisio.status.metrics.LiveMetrics
import com.karelisio.status.metrics.MemoryInfo
import com.karelisio.status.metrics.NetworkInfo
import com.karelisio.status.metrics.StorageInfo
import com.karelisio.status.metrics.ThermalInfo
import com.karelisio.status.metrics.Transport
import com.karelisio.status.ui.onboarding.OnboardingScreen
import com.karelisio.status.ui.screens.DashboardContent
import com.karelisio.status.ui.screens.HistoryScreen
import com.karelisio.status.ui.screens.MoreScreen
import com.karelisio.status.ui.screens.TweaksScreen
import com.karelisio.status.ui.theme.StatusTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/** Plain application so Robolectric doesn't start WorkManager & co. */
class TestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}

/**
 * Renders key screens to PNG (app/screenshots) with Roborazzi, to review the UI without a device:
 * ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*' -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun fake(charging: Boolean, temp: Float) = LiveMetrics(
        timestamp = 0,
        battery = BatteryInfo(
            level = 76, status = if (charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING,
            charging = charging, plugged = if (charging) BatteryManager.BATTERY_PLUGGED_AC else 0, health = BatteryManager.BATTERY_HEALTH_GOOD,
            tempC = temp, voltageMv = 4_312, currentMa = if (charging) 4_120 else -640, powerW = if (charging) 17.8f else -2.7f,
            technology = "Li-poly", chargeCounterMah = 3_800, designCapacityMah = 5_000, cycleCount = 212,
        ),
        cpu = CpuInfo((0 until 8).map { CoreInfo(it, true, 600_000L + it * 290_000L, 300_000, 2_800_000) }, 0.42f, true, 48.5f),
        memory = MemoryInfo(12_000_000_000, 1_500_000_000, false),
        storage = StorageInfo(256_000_000_000, 21_000_000_000),
        thermal = ThermalInfo(if (temp > 42) 2 else 0, 0.4f),
        uptimeMs = 3 * 86_400_000L + 5 * 3_600_000L,
        network = NetworkInfo(Transport.CELLULAR, true, true, 2_400_000, 180_000, "5G", -92, 3, "Free", null, null, null, null, "10.24.8.17", null),
        rxHistory = List(60) { i -> listOf(20_000L, 1_200_000L, 300_000L, 3_000_000L, 60_000L, 900_000L, 0L)[i % 7] },
        txHistory = List(60) { i -> listOf(5_000L, 90_000L, 30_000L, 200_000L, 10_000L)[i % 5] },
    )

    private fun shot(name: String, dark: Boolean = false, advanceMs: Long = 2_500, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { StatusTheme(darkTheme = dark) { content() } }
        repeat(10) {
            compose.mainClock.advanceTimeBy(advanceMs / 10)
            Thread.sleep(150)
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Test @Config(qualifiers = "w411dp-h1900dp-xxhdpi")
    fun dashboardCharging() = shot("dashboard_light") { DashboardContent(fake(true, 33.4f), 42f) {} }

    @Test @Config(qualifiers = "w411dp-h1900dp-xxhdpi")
    fun dashboardDarkHot() = shot("dashboard_dark", dark = true) { DashboardContent(fake(false, 43.2f), 42f) {} }

    @Test fun onboardingWelcome() = shot("onboarding_welcome", advanceMs = 1_200) { OnboardingScreen {} }

    @Test fun onboardingWelcomeDark() = shot("onboarding_welcome_dark", dark = true, advanceMs = 3_000) { OnboardingScreen {} }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun tweaks() = shot("tweaks", advanceMs = 3_000) { TweaksScreen {} }

    @Test fun more() = shot("more", dark = true) { MoreScreen {} }

    @Test @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun history() {
        runBlocking {
            val dao = AppGraph.database.snapshots()
            val now = System.currentTimeMillis()
            var rx = 0L; var mob = 0L
            for (i in 0 until 96) {
                val t = now - (96 - i) * 15 * 60_000L
                rx += 5_000_000L + (i % 9) * 3_000_000L; mob += 2_000_000L + (i % 5) * 1_500_000L
                val level = (60 + 35 * sin(i / 12.0)).toInt().coerceIn(5, 100)
                dao.insert(
                    SnapshotEntity(
                        timestamp = t, event = if (i == 40) 1 else if (i == 60) 2 else 0, batteryLevel = level,
                        batteryTempC = 30f + 6f * sin(i / 8.0).toFloat(), voltageMv = 4200, currentMa = 0, powerW = 0f,
                        charging = i in 40..60, plugType = 0, totalRxBytes = rx, totalTxBytes = rx / 10,
                        mobileRxBytes = mob, mobileTxBytes = mob / 10, bootTime = now - 10 * 86_400_000L,
                        storageFreeBytes = 1, ramAvailBytes = 1,
                    ),
                )
            }
        }
        shot("history", advanceMs = 6_000) { HistoryScreen() }
    }
}
