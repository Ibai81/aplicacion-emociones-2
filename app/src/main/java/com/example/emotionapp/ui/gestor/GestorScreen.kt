package com.example.emotionapp.ui.gestor

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun GestorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    /* ------- Datos ------- */
    var emotions by remember { mutableStateOf(listEmotionFiles(context)) }
    var audios by remember { mutableStateOf(listAudioFiles(context)) }

    fun reload() {
        emotions = listEmotionFiles(context)
        audios = listAudioFiles(context)
    }

    /* ------- Filtros ------- */
    data class Parsed(
        val type: Char,
        val year: Int?, val month: Int?, val day: Int?,
        val hour: Int?, val minute: Int?,
        val placeShort: String, val stamp: String
    )

    fun parseBase(base: String): Parsed {
        val parts = base.split("_")
        val type = parts.getOrNull(0)?.firstOrNull() ?: 'e'
        val isOld = parts.size >= 4 && parts[1].length == 8 && parts[2].length == 4
        val isNew = parts.size >= 4 && parts[parts.size - 2].length == 8 && parts[parts.size - 1].length == 4
        val (date, time, place) = when {
            isOld -> Triple(parts[1], parts[2], parts.drop(3).joinToString("_").ifBlank { "sinlugar" })
            isNew -> {
                val d = parts[parts.size - 2]; val t = parts[parts.size - 1]
                val p = parts.drop(1).dropLast(2).joinToString("_").ifBlank { "sinlugar" }
                Triple(d, t, p)
            }
            else -> Triple("", "", "sinlugar")
        }
        val y = date.take(4).toIntOrNull()
        val m = date.drop(4).take(2).toIntOrNull()
        val d = date.drop(6).take(2).toIntOrNull()
        val hh = time.take(2).toIntOrNull()
        val mm = time.drop(2).take(2).toIntOrNull()
        return Parsed(type, y, m, d, hh, mm, place, if (date.isNotEmpty() && time.isNotEmpty()) "${date}_${time}" else "")
    }

    val allParsed by remember(emotions, audios) {
        mutableStateOf((emotions + audios).associate { it.baseName to parseBase(it.baseName) })
    }

    val allYears = remember(allParsed) { allParsed.values.mapNotNull { it.year }.distinct().sortedDescending() }
    val allMonths = (1..12).toList()
    val allPlaces = remember(allParsed) { allParsed.values.map { it.placeShort }.distinct().sorted() }

    var yearSel by remember { mutableStateOf<Int?>(null) }
    var monthSel by remember { mutableStateOf<Int?>(null) }
    var placeSel by remember { mutableStateOf<String?>(null) }

    fun List<EntryFile>.applyFilters(): List<EntryFile> = filter { ef ->
        val p = allParsed[ef.baseName] ?: return@filter true
        val okY = yearSel?.let { it == p.year } ?: true
        val okM = monthSel?.let { it == p.month } ?: true
        val okP = placeSel?.let { it.equals(p.placeShort, ignoreCase = true) } ?: true
        okY && okM && okP
    }

    val emotionsFiltered = remember(emotions, yearSel, monthSel, placeSel) { emotions.applyFilters() }
    val audiosFiltered = remember(audios, yearSel, monthSel, placeSel) { audios.applyFilters() }

    /* ------- Importar/Exportar ------- */
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            runCatching { exportEntriesToZip(context, uri) }
                .onSuccess { scope.launch { snackbar.showSnackbar("Exportado (selector).") } }
                .onFailure { e -> scope.launch { snackbar.showSnackbar("Error al exportar: ${e.message}") } }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { importEntriesFromZip(context, uri) }
                .onSuccess { reload(); scope.launch { snackbar.showSnackbar("Importado correctamente.") } }
                .onFailure { e -> scope.launch { snackbar.showSnackbar("Error al importar: ${e.message}") } }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Gestor de entradas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            // Acciones en 2 filas (para que no se corten)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) { Text("Importar (ZIP)") }
                Button(onClick = {
                    val suggested = "emociones_backup_${System.currentTimeMillis()}.zip"
                    exportLauncher.launch(suggested)
                }) { Text("Exportar (selector)") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    runCatching { exportEntriesToLocalZip(context) }
                        .onSuccess { f -> scope.launch { snackbar.showSnackbar("ZIP local: ${f.name}") } }
                        .onFailure { e -> scope.launch { snackbar.showSnackbar("Error: ${e.message}") } }
                }) { Text("Exportar local") }
                OutlinedButton(onClick = {
                    runCatching { copySummaryToClipboard(context) }
                        .onSuccess { scope.launch { snackbar.showSnackbar("Resumen copiado.") } }
                        .onFailure { e -> scope.launch { snackbar.showSnackbar("Error: ${e.message}") } }
                }) { Text("Copiar resumen (IA)") }
                OutlinedButton(onClick = { reload() }) { Text("Recargar") }
            }

            // Filtros en 2 filas
            FiltersRow2Lines(
                years = allYears,
                months = allMonths,
                places = allPlaces,
                yearSel = yearSel,
                monthSel = monthSel,
                placeSel = placeSel,
                onYear = { yearSel = it },
                onMonth = { monthSel = it },
                onPlace = { placeSel = it },
                onClear = { yearSel = null; monthSel = null; placeSel = null }
            )

            // ===== EMOCIONES =====
            Text("Emociones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (emotionsFiltered.isEmpty()) {
                Text("No hay entradas de emociones (con estos filtros).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 520.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(emotionsFiltered, key = { it.file.name }) { ef ->
                        EmotionTextRow(file = ef.file, baseName = ef.baseName)
                    }
                }
            }

            // ===== AUDIO =====
            Text("Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (audiosFiltered.isEmpty()) {
                Text("No hay entradas de audio (con estos filtros).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 520.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(audiosFiltered, key = { it.file.name }) { ef ->
                        AudioTextRow(audioFile = ef.file, baseName = ef.baseName)
                    }
                }
            }
        }
    }
}

/* ------------ Filtros en 2 filas ------------ */
@Composable
private fun FiltersRow2Lines(
    years: List<Int>,
    months: List<Int>,
    places: List<String>,
    yearSel: Int?,
    monthSel: Int?,
    placeSel: String?,
    onYear: (Int?) -> Unit,
    onMonth: (Int?) -> Unit,
    onPlace: (String?) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DropdownFilter(
            label = "Año",
            current = yearSel?.toString() ?: "Todos",
            options = years.map { it.toString() },
            onPick = { s -> onYear(s.toIntOrNull()) },
            onClear = { onYear(null) }
        )
        DropdownFilter(
            label = "Mes",
            current = monthSel?.let { String.format(Locale.getDefault(), "%02d", it) } ?: "Todos",
            options = months.map { String.format(Locale.getDefault(), "%02d", it) },
            onPick = { s -> onMonth(s.toIntOrNull()) },
            onClear = { onMonth(null) }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DropdownFilter(
            label = "Lugar",
            current = placeSel ?: "Todos",
            options = places,
            onPick = { onPlace(it) },
            onClear = { onPlace(null) }
        )
        TextButton(onClick = onClear) { Text("Limpiar filtros") }
    }
}

@Composable
private fun DropdownFilter(
    label: String,
    current: String,
    options: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .widthIn(min = 140.dp)
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = { onClear(); expanded = false })
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = {
                    onPick(opt); expanded = false
                })
            }
        }
    }
}

/* ---------- Fila de texto EMOCIÓN ---------- */
@Composable
private fun EmotionTextRow(file: File, baseName: String) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }

    Text(
        text = baseName,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 6.dp, vertical = 8.dp)
    )

    val entry by remember(file) { mutableStateOf(runCatching { loadEmotionEntry(context, file) }.getOrNull()) }

    if (open && entry != null) {
        val scroll = rememberScrollState()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Detalle de entrada") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .heightIn(min = 360.dp, max = 640.dp)
                        .verticalScroll(scroll)
                ) {
                    Text("Lugar: ${entry!!.place}")
                    Text("Personas: ${entry!!.people}")
                    Text("Situación y hechos: ${entry!!.situationFacts}")
                    Text("Pensamientos: ${entry!!.thoughts}")
                    Text("Acciones: ${entry!!.actions}")

                    // Sensaciones corporales desde notas separadas por comas
                    val sensations = entry!!.notes
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (sensations.isNotEmpty()) {
                        Text("Sensaciones corporales:", fontWeight = FontWeight.SemiBold)
                        sensations.forEach { Text("• $it") }
                    }

                    Divider()
                    Text("Emociones:", fontWeight = FontWeight.SemiBold)
                    entry!!.emotions.forEach { Text("- ${it.label}: ${it.intensity}/5") }
                    Spacer(Modifier.height(8.dp))
                    Text("Intensidad general: ${entry!!.generalIntensity}/5")
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar") } }
        )
    }
}

/* ---------- Fila de texto AUDIO ---------- */
@Composable
private fun AudioTextRow(audioFile: File, baseName: String) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }

    // Cargar meta una vez (no necesitamos State<>)
    val meta = remember(baseName) { loadAudioMeta(context, baseName) }

    Text(
        text = baseName,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 6.dp, vertical = 8.dp)
    )

    if (open) {
        val scroll = rememberScrollState()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Detalle de audio") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .heightIn(min = 360.dp, max = 640.dp)
                        .verticalScroll(scroll)
                ) {
                    if (meta != null) {
                        Text("Lugar: ${meta.place}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Intensidad general: ${meta.generalIntensity}/5", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (meta.description.isNotBlank())
                            Text("Descripción: ${meta.description}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Sin metadatos (json).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MiniAudioPlayer(source = fileUri(audioFile))
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar") } }
        )
    }
}

/* ---------- Mini reproductor ---------- */
@Composable
private fun MiniAudioPlayer(source: Uri) {
    val context = LocalContext.current
    var player by remember(source) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var position by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        player?.release()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(context, source)
            mp.setOnPreparedListener {
                prepared = true
                duration = it.duration.coerceAtLeast(0)
                position = 0
            }
            mp.setOnCompletionListener { playing = false; position = duration }
            mp.prepareAsync()
            player = mp
        } catch (_: Exception) {
            mp.release(); player = null; prepared = false
        }
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }

    LaunchedEffect(playing) {
        while (playing) {
            val p = player?.currentPosition ?: 0
            if (!dragging) position = p
            delay(200)
        }
    }

    val can = prepared && player != null && duration > 0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (!can) return@Button
                if (playing) { player?.pause(); playing = false }
                else {
                    if (position >= duration) { player?.seekTo(0); position = 0 }
                    player?.start(); playing = true
                }
            }, enabled = can) { Text(if (playing) "Pausar" else "Reproducir") }

            OutlinedButton(onClick = {
                if (!can) return@OutlinedButton
                player?.pause(); player?.seekTo(0); position = 0; playing = false
            }, enabled = can) { Text("Parar") }
        }

        val value = if (duration <= 0) 0f else (position.coerceIn(0, duration).toFloat() / duration)
        Slider(
            value = value,
            onValueChange = { v ->
                if (!can) return@Slider
                dragging = true
                position = (v * duration).toInt()
            },
            onValueChangeFinished = {
                if (!can) return@Slider
                player?.seekTo(position)
                dragging = false
            },
            enabled = can
        )
        Text("${format(position)} / ${format(duration)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun format(ms: Int): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
