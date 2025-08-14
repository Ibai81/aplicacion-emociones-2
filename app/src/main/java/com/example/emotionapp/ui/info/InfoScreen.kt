package com.example.emotionapp.ui.info

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.RowScope
import com.example.emotionapp.data.listEmotionFiles
import com.example.emotionapp.data.loadEmotionEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/* ===== Tipos top-level ===== */
enum class Metric { INTENSIDAD_MEDIA, MAS_USADAS, MAS_INTENSAS }
data class EmotionItem(val key: String, val label: String, val intensity: Int)

/* ===== Tabla breve de referencia ===== */
data class InfoRow(val emocion: String, val descripcion: String, val sensaciones: String)
private val infoRows = listOf(
    InfoRow("Alegría","Bienestar y expansión.","Ligereza, energía, calor."),
    InfoRow("Tristeza","Respuesta a pérdida.","Opresión, cansancio, lágrimas."),
    InfoRow("Miedo","Alarma ante amenaza.","Aceleración, nudo, sudor."),
    InfoRow("Ira","Poner límites.","Calor, tensión, enrojecimiento."),
    InfoRow("Asco","Rechazo.","Náusea, retirada."),
    InfoRow("Sorpresa","Orientación rápida.","Sobresalto, ojos abiertos."),
    InfoRow("Ansiedad","Alerta anticipatoria.","Inquietud, mariposas, sudor."),
    InfoRow("Calma","Seguridad y suficiencia.","Respiración profunda, relajación.")
)

/* ===== Pantalla ===== */
@Composable
fun InfoScreen() {
    val scroll = rememberScrollState()
    val context = LocalContext.current

    // Controles de rango
    var byMonths by remember { mutableStateOf(false) } // false=días, true=meses
    var daysSel by remember { mutableStateOf(7) }
    var monthsSel by remember { mutableStateOf(3) }

    // Métrica
    var metric by remember { mutableStateOf(Metric.INTENSIDAD_MEDIA) }

    // Datos
    val files = remember { listEmotionFiles(context).map { it.file } }
    val model = remember(files, byMonths, daysSel, monthsSel, metric) {
        buildChartModel(context, files, byMonths, if (byMonths) monthsSel else daysSel, metric)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Información y gráficos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gráfico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                // Selector periodo
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !byMonths, onClick = { byMonths = false }, label = { Text("Por días") })
                    FilterChip(selected = byMonths, onClick = { byMonths = true }, label = { Text("Por meses") })
                    if (!byMonths) {
                        SegmentedNumberPicker(listOf(7, 14, 30), daysSel) { daysSel = it }
                    } else {
                        SegmentedNumberPicker(listOf(3, 6, 12), monthsSel) { monthsSel = it }
                    }
                }

                // Selector métrica
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = metric == Metric.INTENSIDAD_MEDIA, onClick = { metric = Metric.INTENSIDAD_MEDIA }, label = { Text("Intensidad media") })
                    FilterChip(selected = metric == Metric.MAS_USADAS, onClick = { metric = Metric.MAS_USADAS }, label = { Text("Más usadas") })
                    FilterChip(selected = metric == Metric.MAS_INTENSAS, onClick = { metric = Metric.MAS_INTENSAS }, label = { Text("Más intensas") })
                }

                // Gráfico único con eje Y numérico
                if (model.labels.isNotEmpty()) {
                    BarChart(
                        labels = model.labels,
                        values = model.values,
                        height = 220.dp,
                        yMax = model.yMax,
                        yTicks = model.yTicks
                    )
                    Text(model.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("No hay datos en el periodo seleccionado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Tabla breve de referencia
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth()) {
                TableRow("Emoción", "Descripción", "Sensaciones", true)
                Spacer(Modifier.height(4.dp))
                for (r in infoRows) {
                    TableRow(r.emocion, r.descripcion, r.sensaciones)
                }
            }
        }
    }
}

/* ===== Selector numérico compacto ===== */
@Composable
private fun SegmentedNumberPicker(options: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (n in options) {
            val sel = n == selected
            FilterChip(selected = sel, onClick = { onPick(n) }, label = { Text(n.toString()) })
        }
    }
}

/* ===== Modelo del gráfico ===== */
private data class ChartModel(
    val labels: List<String>,
    val values: List<Float>,
    val yMax: Float,
    val yTicks: Int,
    val caption: String
)

private fun buildChartModel(
    context: Context,
    files: List<File>,
    byMonths: Boolean,
    span: Int,
    metric: Metric
): ChartModel {
    data class E(val date: Date, val general: Int, val emotions: List<EmotionItem>)

    val rx = Regex("""\d{8}_\d{4}""")
    val fmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
    val entries: List<E> = files.mapNotNull { f ->
        try {
            val entry = loadEmotionEntry(context, f)
            val stamp: String = rx.find(f.nameWithoutExtension)?.value ?: return@mapNotNull null
            val date: Date = fmt.parse(stamp) ?: return@mapNotNull null
            val list: List<EmotionItem> = entry.emotions.map { EmotionItem(it.key, it.label, it.intensity) }
            E(date, entry.generalIntensity.coerceIn(1, 5), list)
        } catch (_: Exception) { null }
    }

    if (entries.isEmpty()) return ChartModel(emptyList(), emptyList(), 1f, 4, "")

    val cal = Calendar.getInstance()
    val now = cal.time

    fun inWindow(d: Date, start: Date, end: Date) = d >= start && d < end

    if (metric == Metric.INTENSIDAD_MEDIA) {
        val labels = mutableListOf<String>()
        val values = mutableListOf<Float>()
        val sdfDay = SimpleDateFormat("dd/MM", Locale.getDefault())

        if (!byMonths) {
            for (i in span - 1 downTo 0) {
                cal.time = now
                cal.add(Calendar.DAY_OF_YEAR, -i)
                val start = cal.apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.time
                val end = Calendar.getInstance().apply { time = start; add(Calendar.DAY_OF_YEAR,1) }.time
                val list = entries.filter { inWindow(it.date, start, end) }
                labels.add(sdfDay.format(start))
                val avg = (list.map { it.general }.average().takeIf { !it.isNaN() } ?: 0.0).toFloat()
                values.add(avg)
            }
            return ChartModel(labels, values, 5f, 5, "Intensidad general media por día")
        } else {
            val sdfMonth = SimpleDateFormat("LLL/yy", Locale.getDefault())
            for (i in span - 1 downTo 0) {
                cal.time = now
                cal.add(Calendar.MONTH, -i)
                val start = cal.apply { set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.time
                val end = Calendar.getInstance().apply { time = start; add(Calendar.MONTH,1) }.time
                val list = entries.filter { inWindow(it.date, start, end) }
                val label = sdfMonth.format(start).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                labels.add(label)
                val avg = (list.map { it.general }.average().takeIf { !it.isNaN() } ?: 0.0).toFloat()
                values.add(avg)
            }
            return ChartModel(labels, values, 5f, 5, "Intensidad general media por mes")
        }
    } else {
        // Top-8 por emoción en todo el periodo seleccionado
        val periodStart: Date
        val periodEnd: Date
        if (!byMonths) {
            cal.time = now
            cal.add(Calendar.DAY_OF_YEAR, -span + 1)
            periodStart = cal.apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.time
            periodEnd = Date()
        } else {
            cal.time = now
            cal.add(Calendar.MONTH, -span + 1)
            periodStart = cal.apply { set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.time
            periodEnd = Date()
        }
        val inPeriod: List<E> = entries.filter { it.date >= periodStart && it.date <= periodEnd }

        val byLabel = mutableMapOf<String, MutableList<Int>>() // label -> intensidades
        for (e in inPeriod) {
            for (emo in e.emotions) {
                val list = byLabel.getOrPut(emo.label) { mutableListOf() }
                list.add(emo.intensity.coerceIn(1, 5))
            }
        }

        if (byLabel.isEmpty()) return ChartModel(emptyList(), emptyList(), 1f, 4, "")

        val pairs: List<Pair<String, Float>> = when (metric) {
            Metric.MAS_USADAS -> byLabel.map { it.key to it.value.size.toFloat() }
            Metric.MAS_INTENSAS -> byLabel.map { it.key to (it.value.average().toFloat()) }
            else -> emptyList()
        }.sortedByDescending { it.second }
            .take(8)

        val labels = pairs.map { it.first }
        val values = pairs.map { it.second }

        val yMax: Float = when (metric) {
            Metric.MAS_USADAS -> ceil((values.maxOrNull() ?: 1f).coerceAtLeast(1f))
            Metric.MAS_INTENSAS -> 5f
            else -> 5f
        }.toFloat()

        val caption = when (metric) {
            Metric.MAS_USADAS -> "Top emociones por uso en el periodo"
            Metric.MAS_INTENSAS -> "Top emociones por intensidad media en el periodo"
            else -> ""
        }

        return ChartModel(labels, values, yMax, 5, caption)
    }
}

/* ===== Gráfico de barras con eje Y ===== */
@Composable
private fun BarChart(
    labels: List<String>,
    values: List<Float>,
    height: Dp = 200.dp,
    yMax: Float = 5f,
    yTicks: Int = 5,
    barSpacing: Dp = 8.dp
) {
    val axisWidth = 36.dp
    val eachWeight: Float = if (labels.isEmpty()) 1f else 1f / labels.size.toFloat()

    // ⚠️ Colores tomados FUERA del Canvas (evita @Composable en drawScope)
    val guideColor = MaterialTheme.colorScheme.outline
    val barColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        // Eje Y
        Column(
            modifier = Modifier.width(axisWidth),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in yTicks downTo 0) {
                val v = (yMax * i / yTicks.toFloat())
                val txt = if (yMax <= 5f) String.format(Locale.getDefault(), "%.0f", v) else v.toInt().toString()
                Text(text = txt, fontSize = 11.sp)
            }
        }

        // Lienzo de barras + guías
        val n = values.size.coerceAtLeast(1)
        val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { barSpacing.toPx() }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(height - 10.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Líneas guía
                val steps = yTicks
                for (i in 0..steps) {
                    val y = size.height * (i / steps.toFloat())
                    drawLine(
                        color = guideColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }

                // Barras
                val barWidth = (size.width - spacingPx * (n + 1)) / n
                for ((i, v) in values.withIndex()) {
                    val clamped = v.coerceAtLeast(0f).coerceAtMost(yMax)
                    val h = if (yMax == 0f) 0f else (clamped / yMax) * size.height
                    val left = spacingPx + i * (barWidth + spacingPx)
                    val top = size.height - h
                    drawRect(
                        color = barColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, h)
                    )
                }
            }
        }
    }

    // Etiquetas
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val maxChars = 8
        for (lab in labels) {
            Text(
                text = lab.take(maxChars),
                modifier = Modifier.weight(eachWeight),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ===== Utils tabla ===== */
@Composable
private fun TableRow(e: String, d: String, s: String, header: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    val bg = if (header) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(width = if (header) 0.dp else 1.dp, color = MaterialTheme.colorScheme.outline, shape = shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Cell(e, weight = 0.22f, bold = header); Spacer(Modifier.width(8.dp))
        Cell(d, weight = 0.38f, bold = header); Spacer(Modifier.width(8.dp))
        Cell(s, weight = 0.40f, bold = header)
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, bold: Boolean) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontSize = if (bold) 15.sp else 14.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = if (bold) 1 else Int.MAX_VALUE,
        lineHeight = 20.sp,
        overflow = if (bold) TextOverflow.Ellipsis else TextOverflow.Clip
    )
}
