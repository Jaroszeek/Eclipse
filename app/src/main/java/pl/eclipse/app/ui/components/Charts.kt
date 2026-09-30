package pl.eclipse.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalBox
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import pl.eclipse.app.ui.theme.gradeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d.MM")

/**
 * Wykres średniej w czasie (SPEC 6.6, 12.4, 12.5): linie w kolorach przedmiotów, oś 0–100%,
 * progi ocen jako cienkie linie albo pasy w tle ([bands]).
 */
@Composable
fun AverageChart(lines: List<Pair<Color, List<Pair<LocalDate, Double>>>>, thresholds: Map<Int, Double>, modifier: Modifier = Modifier, bands: Boolean = false) {
    val visible = lines.filter { it.second.isNotEmpty() }
    if (visible.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(visible) {
        producer.runTransaction {
            lineModel {
                visible.forEach { (_, points) -> series(x = points.map { it.first.toEpochDay() }, y = points.map { it.second }) }
            }
        }
    }
    val sorted = thresholds.entries.sortedBy { it.value }
    val decorations = if (bands) {
        sorted.mapIndexed { index, (grade, from) ->
            val to = sorted.getOrNull(index + 1)?.value ?: 100.0
            HorizontalBox(y = { from..to }, box = rememberShapeComponent(fill = Fill(gradeColor(grade).copy(alpha = 0.07f))))
        }
    } else {
        sorted.filter { it.value < 100.0 }.map { (grade, value) ->
            HorizontalLine(y = { value }, line = rememberLineComponent(fill = Fill(gradeColor(grade).copy(alpha = 0.55f)), thickness = 1.dp))
        }
    }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        visible.map { (color, _) ->
                            LineCartesianLayer.rememberLine(
                                LineCartesianLayer.LineFill.single(Fill(color)),
                                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
                            )
                        },
                    ),
                    rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0) },
                ),
                startAxis = VerticalAxis.rememberStart(valueFormatter = remember { CartesianValueFormatter.decimal(decimalCount = 0, suffix = "%") }),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = remember { CartesianValueFormatter { _, x, _ -> LocalDate.ofEpochDay(x.toLong()).format(DAY_MONTH) } },
                ),
                decorations = decorations,
            ),
            modelProducer = producer,
            modifier = modifier.fillMaxWidth().height(220.dp),
            scrollState = rememberVicoScrollState(scrollEnabled = false),
        )
    }
}

/** Słupki na tydzień (SPEC 12.5): wyróżnione tygodnie w drugim kolorze (dwie serie złożone w stos). */
@Composable
fun WeekColumns(values: List<Int>, labels: List<String>, highlight: (Int) -> Boolean, normalColor: Color, highlightColor: Color, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        producer.runTransaction {
            columnModel {
                series(values.mapIndexed { i, v -> if (highlight(i)) 0 else v })
                series(values.mapIndexed { i, v -> if (highlight(i)) v else 0 })
            }
        }
    }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        listOf(
                            rememberLineComponent(fill = Fill(normalColor), thickness = 14.dp, shape = RoundedCornerShape(4.dp)),
                            rememberLineComponent(fill = Fill(highlightColor), thickness = 14.dp, shape = RoundedCornerShape(4.dp)),
                        ),
                    ),
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = remember { CartesianValueFormatter.decimal(decimalCount = 0) },
                    itemPlacer = remember { VerticalAxis.ItemPlacer.step({ 1.0 }) },
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = remember(labels) { CartesianValueFormatter { _, x, _ -> labels.getOrElse(x.toInt()) { "" } } },
                ),
            ),
            modelProducer = producer,
            modifier = modifier.fillMaxWidth().height(200.dp),
            scrollState = rememberVicoScrollState(scrollEnabled = false),
        )
    }
}
