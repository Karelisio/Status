package com.karelisio.status.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.stacked
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.toShaderProvider
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

/** Smooth area line chart. [x] are arbitrary numeric positions (e.g. hours since range start). */
@Composable
fun AreaLineChart(
    x: List<Double>,
    y: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    minY: Double? = null,
    maxY: Double? = null,
    xFormatter: (Double) -> String = { it.toInt().toString() },
    yFormatter: (Double) -> String = { it.toInt().toString() },
    labelSpacing: Int = 1,
) {
    if (x.size < 2) {
        EmptyChart(modifier.height(height))
        return
    }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(x, y) { producer.runTransaction { lineSeries { series(x, y) } } }
    val label = rememberTextComponent(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(color)),
        areaFill = LineCartesianLayer.AreaFill.single(
            fill(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)).toShaderProvider()),
            splitY = { minY ?: 0.0 },
        ),
        pointConnector = LineCartesianLayer.PointConnector.cubic(),
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(line),
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = minY, maxY = maxY),
            ),
            startAxis = VerticalAxis.rememberStart(
                label = label,
                line = null,
                tick = null,
                valueFormatter = CartesianValueFormatter { _, v, _ -> yFormatter(v) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = label,
                line = null,
                tick = null,
                guideline = null,
                valueFormatter = CartesianValueFormatter { _, v, _ -> xFormatter(v) },
                itemPlacer = remember(labelSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }) },
            ),
        ),
        modelProducer = producer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        modifier = modifier.fillMaxWidth().height(height),
    )
}

/** Stacked rounded columns (e.g. mobile + Wi-Fi data per bucket). */
@Composable
fun StackedColumnChart(
    series: List<List<Double>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    xFormatter: (Double) -> String = { it.toInt().toString() },
    yFormatter: (Double) -> String = { it.toInt().toString() },
    labelSpacing: Int = 1,
) {
    if (series.isEmpty() || series.first().isEmpty() || series.all { s -> s.all { it == 0.0 } }) {
        EmptyChart(modifier.height(height))
        return
    }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(series) { producer.runTransaction { columnSeries { series.forEach { series(it) } } } }
    val label = rememberTextComponent(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val thickness = if (series.first().size > 12) 6.dp else 14.dp
    val columns = colors.map { c ->
        rememberLineComponent(fill = fill(c), thickness = thickness, shape = CorneredShape.rounded(40))
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(columns),
                mergeMode = { ColumnCartesianLayer.MergeMode.stacked() },
            ),
            startAxis = VerticalAxis.rememberStart(
                label = label, line = null, tick = null,
                valueFormatter = CartesianValueFormatter { _, v, _ -> yFormatter(v) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = label, line = null, tick = null, guideline = null,
                valueFormatter = CartesianValueFormatter { _, v, _ -> xFormatter(v) },
                itemPlacer = remember(labelSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }) },
            ),
        ),
        modelProducer = producer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        modifier = modifier.fillMaxWidth().height(height),
    )
}

@Composable
private fun EmptyChart(modifier: Modifier) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.material3.Text(
            "Pas encore assez de relevés",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Places time-stamped points on a quarter-unit grid (unit = [unitMs]) so Vico's x step stays
 * regular even with irregular plug/unplug events; keeps the last point of each slot.
 */
fun <T> timeSeries(points: List<T>, from: Long, unitMs: Long, ts: (T) -> Long, value: (T) -> Double): Pair<List<Double>, List<Double>> {
    val slots = LinkedHashMap<Double, Double>()
    points.forEach { p -> slots[kotlin.math.round((ts(p) - from).toDouble() / unitMs * 4) / 4] = value(p) }
    val sorted = slots.entries.sortedBy { it.key }
    return sorted.map { it.key } to sorted.map { it.value }
}
