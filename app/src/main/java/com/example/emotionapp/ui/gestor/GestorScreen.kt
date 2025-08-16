package com.example.emotionapp.ui.gestor

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.data.EmotionEntry
import com.example.emotionapp.data.deleteEmotionAndMedia
import com.example.emotionapp.data.parseBaseName
import com.google.gson.Gson
import java.io.File

@Composable
fun GestorScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current

    // -------- Reproductor global (evitar solapes) --------
    var playingBase by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            try { player?.stop(); player?.release() } catch (_: Exception) {}
            player = null
        }
    }

    // -------- Estado de lista / búsqueda / diálogos --------
    var rows by remember { mutableStateOf(loadRows(context)) }
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<RowItem?>(null) }
    var toView by remember { mutableStateOf<RowItem?>(null) }

    fun refresh() { rows = loadRows(context) }

    val filtered = remember(rows, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) rows else rows.filter { r ->
            val inBase = r.baseName.lowercase().contains(q)
            val inPlace = r.place.lowercase().contains(q)
            val e = r.entry
            inBase || inPlace ||
                    (runCatching { e?.people?.lowercase()?.contains(q) == true }.getOrDefault(false)) ||
                    (runCatching { e?.topic?.lowercase()?.contains(q) == true }.getOrDefault(false)) ||
                    (runCatching { e?.thoughts?.lowercase()?.contains(q) == true }.getOrDefault(false)) ||
                    (runCatching { e?.notes?.lowercase()?.contains(q) == true }.getOrDefault(false))
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gestor de entradas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Buscar (fecha, lugar, tema, personas, notas)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (filtered.isEmpty()) {
            Text("No hay entradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.baseName }) { row ->
                    ElevatedCard {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { toView = row } // tocar fuera de botones => ver detalle
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Línea 1: fecha (dd/mm/aaaa) + lugar
                            val dateOnly = formatDateDdMmYyyy(row.dateStamp)
                            val place = row.place.ifBlank { "—" }
                            Text(
                                text = "$dateOnly   $place",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Línea 2: acciones: Reproducir (si hay audio) + borrar (icono)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (row.hasAudio) {
                                    val isPlaying = playingBase == row.baseName
                                    OutlinedButton(
                                        onClick = {
                                            if (!isPlaying) {
                                                try {
                                                    player?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
                                                    val p = MediaPlayer().apply {
                                                        setDataSource(row.audioFile!!.absolutePath)
                                                        prepare()
                                                        start()
                                                        setOnCompletionListener { playingBase = null }
                                                    }
                                                    player = p
                                                    playingBase = row.baseName
                                                } catch (_: Exception) {
                                                    playingBase = null
                                                    try { player?.release() } catch (_: Exception) {}
                                                    player = null
                                                    Toast.makeText(context, "No se pudo reproducir.", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                try { player?.pause() } catch (_: Exception) {}
                                                playingBase = null
                                            }
                                        }
                                    ) { Text(if (isPlaying) "Pausar" else "Reproducir") }
                                } else {
                                    Text("Sin audio", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                IconButton(
                                    onClick = { toDelete = row },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Borrar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // -------- Ventana flotante con texto (detalle) --------
    val viewing = toView
    if (viewing != null) {
        val entry = viewing.entry
        AlertDialog(
            onDismissRequest = { toView = null },
            title = { Text("Detalle") },
            text = {
                if (entry == null) {
                    Text("Esta entrada no tiene texto (solo audio).")
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fun <T> safeOrNull(get: () -> T?): T? = runCatching { get() }.getOrNull()

                        // Cadenas como String? para evitar NPE y usar isNullOrBlank
                        val dateFull: String? = safeOrNull { viewing.dateStamp }
                        val placeText: String? = safeOrNull { entry.place }
                        val topicText: String? = safeOrNull { entry.topic }
                        val peopleText: String? = safeOrNull { entry.people }
                        val thoughtsText: String? = safeOrNull { entry.thoughts }
                        val actionsText: String? = safeOrNull { entry.actions }
                        val notesText: String? = safeOrNull { entry.notes }

                        dateFull?.takeIf { it.isNotBlank() }?.let { Text("Fecha-hora: $it") }
                        placeText?.takeIf { it.isNotBlank() }?.let { Text("Lugar: $it") }
                        topicText?.takeIf { it.isNotBlank() }?.let { Text("Tema: $it") }
                        peopleText?.takeIf { it.isNotBlank() }?.let { Text("Personas: $it") }
                        thoughtsText?.takeIf { it.isNotBlank() }?.let { Text("Pensamientos: $it") }
                        actionsText?.takeIf { it.isNotBlank() }?.let { Text("Acciones: $it") }
                        notesText?.takeIf { it.isNotBlank() }?.let { Text("Notas: $it") }

                        val emos = runCatching { entry.emotions }.getOrElse { emptyList() }
                        if (emos.isNotEmpty()) {
                            Text("Emociones: " + emos.joinToString { "${it.label}(${it.intensity})" })
                        }
                        val facts: String? = safeOrNull { entry.situationFacts }
                        facts?.takeIf { it.isNotBlank() }?.let {
                            Text("Situación y hechos:\n$it")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { toView = null }) { Text("Cerrar") } }
        )
    }

    // -------- Confirmación de borrado --------
    val deleting = toDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Borrar entrada") },
            text = { Text("¿Seguro que quieres borrar “${deleting.baseName}”? Se eliminará también el audio asociado si existe.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ok = runCatching { deleteEmotionAndMedia(context, deleting.baseName) }.getOrDefault(false)
                        toDelete = null
                        if (ok) {
                            Toast.makeText(context, "Entrada borrada.", Toast.LENGTH_SHORT).show()
                            if (playingBase == deleting.baseName) {
                                try { player?.stop(); player?.release() } catch (_: Exception) {}
                                player = null
                                playingBase = null
                            }
                            rows = loadRows(context) // refrescar
                        } else {
                            Toast.makeText(context, "No se pudo borrar.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}

/* ==================== Datos para la lista ==================== */

private data class RowItem(
    val baseName: String,
    val hasJson: Boolean,
    val hasAudio: Boolean,
    val jsonFile: File?,
    val audioFile: File?,
    val dateStamp: String,
    val place: String,
    val entry: EmotionEntry? // null si no hay JSON
)

private fun loadRows(context: android.content.Context): List<RowItem> {
    val dir = File(context.filesDir, "entries")
    if (!dir.exists()) return emptyList()

    // Agrupar por base
    data class Acc(var json: File? = null, var audio: File? = null)
    val map = mutableMapOf<String, Acc>()

    dir.listFiles()?.forEach { f ->
        if (!f.isFile) return@forEach
        val name = f.name
        when {
            name.startsWith("e_") && name.endsWith(".json") -> {
                val base = name.removeSuffix(".json")
                map.getOrPut(base) { Acc() }.json = f
            }
            name.startsWith("e_") && name.endsWith(".m4a") -> {
                val base = name.removeSuffix(".m4a")
                map.getOrPut(base) { Acc() }.audio = f
            }
            name.startsWith("a_") && name.endsWith(".m4a") -> {
                val base = name.removeSuffix(".m4a")
                map.getOrPut(base) { Acc() }.audio = f
            }
        }
    }

    val gson = Gson()
    val rows = map.map { (base, acc) ->
        val parsed = parseBaseName(base)
        val entry = acc.json?.let { runCatching { gson.fromJson(it.readText(), EmotionEntry::class.java) }.getOrNull() }
        val place = runCatching { entry?.place?.takeIf { it != null && it.isNotBlank() } ?: parsed.place }.getOrDefault(parsed.place)
        val ts = parsed.dateStamp
        val lastMod = maxOf(acc.json?.lastModified() ?: 0L, acc.audio?.lastModified() ?: 0L)
        Triple(
            lastMod,
            base,
            RowItem(
                baseName = base,
                hasJson = acc.json != null,
                hasAudio = acc.audio != null,
                jsonFile = acc.json,
                audioFile = acc.audio,
                dateStamp = ts,
                place = place ?: "—",
                entry = entry
            )
        )
    }.sortedByDescending { it.first }.map { it.third }

    return rows
}

/* ==================== Utilidades ==================== */

// Convierte "YYYYMMDD_HHMM" o "YYYY-MM-DD HH:MM" a "dd/mm/aaaa"
private fun formatDateDdMmYyyy(raw: String): String {
    val datePart = raw.substringBefore('_').substringBefore(' ').trim()
    return when {
        // Caso "YYYYMMDD"
        datePart.length == 8 && datePart.all { it.isDigit() } -> {
            val y = datePart.substring(0, 4)
            val m = datePart.substring(4, 6)
            val d = datePart.substring(6, 8)
            "$d/$m/$y"
        }
        // Caso "YYYY-MM-DD"
        datePart.count { it == '-' } == 2 -> {
            val (y, m, d) = datePart.split('-')
            "$d/$m/$y"
        }
        else -> datePart // fallback
    }
}
