package com.example.emotionapp.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlin.math.roundToInt

/** Item de patrón (nombre, recuento y media de intensidad) */
data class PatternItem(val name: String, val count: Int, val avgIntensity: Double)

/** Informe completo de patrones para UI */
data class PatternsReport(
    val totalEntries: Int,
    val places: List<PatternItem>,
    val people: List<PatternItem>,
    val sensations: List<PatternItem>,
    val emotions: List<PatternItem>
)

/* ===== Preferencias internas para “mostrar sugerencias de patrones una vez” ===== */
private const val PREFS_ANALYTICS = "analytics_prefs"
private const val KEY_PATTERNS_SHOWN_AT9 = "patterns_shown_once_after_8"

/** Muestra sugerencias automáticamente cuando haya ≥ 9 entradas y aún no se mostró. */
fun shouldShowPatternsNow(context: Context): Boolean {
    val total = listEmotionFiles(context).size
    val shown = context.getSharedPreferences(PREFS_ANALYTICS, Context.MODE_PRIVATE)
        .getBoolean(KEY_PATTERNS_SHOWN_AT9, false)
    return total >= 9 && !shown
}

/** Marca que ya se mostraron las sugerencias automáticas. */
fun markPatternsShown(context: Context) {
    context.getSharedPreferences(PREFS_ANALYTICS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_PATTERNS_SHOWN_AT9, true).apply()
}

/* ===== Utilidades internas ===== */

private fun clamp1to5(v: Int): Int = when {
    v < 1 -> 1
    v > 5 -> 5
    else -> v
}

private fun List<Int>.averageOrZero(): Double =
    if (isEmpty()) 0.0 else sum().toDouble() / size

private fun splitCsv(s: String?): List<String> =
    s.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }

/* ===== Cálculo principal de patrones ===== */

/**
 * Calcula patrones a partir de los ficheros de emociones guardados.
 * - Lugares: usa `entry.place` (vacíos → “sin lugar”).
 * - Personas: CSV en `entry.people`.
 * - Sensaciones: se intenta leer `sensations` (si existe en el JSON);
 *                si no, se derivan de `entry.notes` separado por comas.
 * - Emociones: `entry.emotions` con intensidad 0..5.
 */
fun computePatterns(context: Context, minCount: Int = 8): PatternsReport {
    val files = listEmotionFiles(context)
    val total = files.size
    val gson = Gson()

    val placeCount = mutableMapOf<String, MutableList<Int>>()
    val peopleCount = mutableMapOf<String, MutableList<Int>>()
    val sensCount = mutableMapOf<String, MutableList<Int>>()
    val emoCount = mutableMapOf<String, MutableList<Int>>()

    for (ef in files) {
        // Carga robusta de la entrada
        val entry = runCatching { loadEmotionEntry(context, ef.file) }.getOrNull() ?: continue
        val intensity = clamp1to5(entry.generalIntensity)

        // Lugar
        val lugar = entry.place.trim().ifEmpty { "sin lugar" }
        placeCount.getOrPut(lugar) { mutableListOf() }.add(intensity)

        // Personas
        splitCsv(entry.people).forEach { p ->
            peopleCount.getOrPut(p) { mutableListOf() }.add(intensity)
        }

        // Sensaciones: intentar leer del JSON, si no, de notes
        val text = runCatching { ef.file.readText() }.getOrNull().orEmpty()
        val sensations: List<String> = runCatching {
            val obj = JsonParser.parseString(text).asJsonObject
            if (obj.has("sensations") && obj.get("sensations").isJsonArray) {
                gson.fromJson(obj.get("sensations"), object : TypeToken<List<String>>() {}.type)
            } else splitCsv(entry.notes)
        }.getOrElse { splitCsv(entry.notes) }
        sensations.forEach { s -> sensCount.getOrPut(s) { mutableListOf() }.add(intensity) }

        // Emociones
        entry.emotions.forEach { e ->
            emoCount.getOrPut(e.label) { mutableListOf() }.add((e.intensity).coerceIn(0, 5))
        }
    }

    fun mapToItems(m: Map<String, List<Int>>): List<PatternItem> =
        m.entries
            .map { (k, v) ->
                val avg = (v.averageOrZero() * 10.0).roundToInt() / 10.0
                PatternItem(k, v.size, avg)
            }
            .filter { it.count >= minCount }
            .sortedWith(compareByDescending<PatternItem> { it.count }.thenByDescending { it.avgIntensity })

    return PatternsReport(
        totalEntries = total,
        places = mapToItems(placeCount),
        people = mapToItems(peopleCount),
        sensations = mapToItems(sensCount),
        emotions = mapToItems(emoCount)
    )
}

/**
 * Construye un texto corto para enseñar en UI con los top-k de cada grupo.
 */
fun buildPatternsMessage(report: PatternsReport, topPerGroup: Int = 3): String {
    if (report.totalEntries < 8) {
        return "Aún no hay suficientes entradas para proponer patrones (necesitamos al menos 8)."
    }
    val sb = StringBuilder()

    fun addBlock(title: String, list: List<PatternItem>) {
        val top = list.take(topPerGroup)
        if (top.isNotEmpty()) {
            sb.appendLine("• $title")
            for (it in top) {
                sb.appendLine("   - ${it.name}: ${it.count} apariciones (intensidad media ${"%.1f".format(it.avgIntensity)})")
            }
        }
    }

    addBlock("Lugares frecuentes", report.places)
    addBlock("Personas asociadas", report.people)
    addBlock("Sensaciones corporales repetidas", report.sensations)
    addBlock("Emociones predominantes", report.emotions)

    return if (sb.isEmpty()) {
        "Hay suficientes entradas, pero aún no se detectan patrones claros con ≥8 observaciones."
    } else sb.toString()
}
