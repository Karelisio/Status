package com.karelisio.status.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.karelisio.status.AppGraph
import com.karelisio.status.MainActivity
import com.karelisio.status.ui.theme.TealDark
import com.karelisio.status.ui.theme.TealLight
import com.karelisio.status.util.Format

private data class WidgetData(
    val level: Int,
    val charging: Boolean,
    val tempC: Float,
    val powerW: Float?,
    val dataLeft: Long?,
    val planRatio: Float?,
)

class StatusWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        AppGraph.init(context)
        val b = AppGraph.metrics.battery.read()
        val plan = runCatching { AppGraph.dataUsage.plan(AppGraph.settings.current()) }.getOrNull()
        val data = WidgetData(b.level, b.charging, b.tempC, b.powerW, plan?.remainingBytes, plan?.ratio)
        provideContent {
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else ColorProviders(TealLight, TealDark)) {
                Content(data)
            }
        }
    }

    @Composable
    private fun Content(d: WidgetData) {
        val size = LocalSize.current
        val c = GlanceTheme.colors
        Box(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
                .background(c.widgetBackground)
                .clickable(actionStartActivity<MainActivity>())
                .padding(14.dp),
        ) {
            if (size.width < WIDE.width) Small(d) else Wide(d, size.height >= LARGE.height)
        }
    }

    @Composable
    private fun Small(d: WidgetData) {
        val c = GlanceTheme.colors
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (d.charging) "⚡ ${d.level} %" else "${d.level} %",
                style = TextStyle(color = c.primary, fontSize = 30.sp, fontWeight = FontWeight.Bold),
            )
            LinearProgressIndicator(
                progress = d.level / 100f,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = c.primary,
                backgroundColor = c.secondaryContainer,
            )
            Spacer(GlanceModifier.height(8.dp))
            Text(Format.celsius(d.tempC), style = TextStyle(color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Text(d.dataLeft?.let { "${Format.bytes(it, 0)} restants" } ?: "Data —", style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp))
        }
    }

    @Composable
    private fun Wide(d: WidgetData, large: Boolean) {
        val c = GlanceTheme.colors
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("Batterie", style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp))
                    Text("${d.level} %", style = TextStyle(color = c.primary, fontSize = if (large) 40.sp else 32.sp, fontWeight = FontWeight.Bold))
                    Text(
                        if (d.charging) "En charge" + (d.powerW?.let { " · ${Format.watts(kotlin.math.abs(it))}" } ?: "") else "Sur batterie",
                        style = TextStyle(color = c.onSurface, fontSize = 12.sp),
                    )
                }
                Spacer(GlanceModifier.width(12.dp))
                Pill("Temp.", Format.celsius(d.tempC))
                Spacer(GlanceModifier.width(8.dp))
                Pill("Data", d.dataLeft?.let { Format.bytes(it, 0) } ?: "—")
            }
            Spacer(GlanceModifier.height(10.dp))
            LinearProgressIndicator(
                progress = d.level / 100f,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                color = c.primary,
                backgroundColor = c.secondaryContainer,
            )
            if (large && d.planRatio != null) {
                Spacer(GlanceModifier.height(12.dp))
                Text("Forfait : ${Format.pct(d.planRatio)} utilisé", style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp))
                Spacer(GlanceModifier.height(4.dp))
                LinearProgressIndicator(
                    progress = d.planRatio.coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                    color = if (d.planRatio >= 0.95f) c.error else c.tertiary,
                    backgroundColor = c.tertiaryContainer,
                )
            }
        }
    }

    @Composable
    private fun Pill(label: String, value: String) {
        val c = GlanceTheme.colors
        Column(
            GlanceModifier.background(c.secondaryContainer).cornerRadius(16.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = TextStyle(color = c.onSecondaryContainer, fontSize = 10.sp))
            Text(value, style = TextStyle(color = c.onSecondaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(220.dp, 110.dp)
        val LARGE = DpSize(220.dp, 180.dp)

        @Volatile private var lastUpdate = 0L

        suspend fun updateAll(context: Context, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastUpdate < 60_000) return
            lastUpdate = now
            runCatching { StatusWidget().updateAll(context) }
        }
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()
}
