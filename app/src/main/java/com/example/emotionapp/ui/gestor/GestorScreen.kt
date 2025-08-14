package com.example.emotionapp.ui.gestor

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.data.*
import com.example.emotionapp.ui.emociones.EmotionEntry
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun GestorScreen() {
    val context = LocalContext.current
    var emotions by remember { mutableStateOf(listEmotionFiles(context)) }
    var audios by remember { mutableStateOf(listAudioFiles(context)) }

    fun reload() {
        emotions = listEmotionFiles(context)
        audios = listAudioFiles(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gestor de entradas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { reload() }) { Text("Recargar") }
        }

        // ===== EMOCIONES =====
        Text("Emociones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (emotions.isEmpty()) {
            Text("No hay entradas de emociones aún.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(emotions, key = { it.file.name }) { ef ->
                    EmotionRow(file = ef.file)
                }
            }
        }

        // ===== AUDIO =====
        Text("Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (audios.isEmpty()) {
            Text("No hay entradas de audio aún.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(audios, key = { it.file.name }) { ef ->
                    AudioRow(audioFile = ef.file, meta = loadAudioMeta(context, ef.baseName))
                }
            }
        }
    }
}

@Composable
private fun EmotionRow(file: File) {
    val context = LocalContext.current
    val entry = remember(file) { runCatching { loadEmotionEntry(context, file) }.getOrNull() }
    val title = file.nameWithoutExtension // e_YYYYMMDD_HHMM_lugar
    var showDetail by remember { mutableStateOf(false) }

    ElevatedCard(onClick = { showDetail = true }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (entry != null) {
                Text(
                    "Emociones: ${entry.emotions.size}  |  Intensidad general: ${entry.generalIntensity}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("No se pudo leer el contenido.", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDetail && entry != null) {
        AlertDialog(
            onDismissRequest = { showDetail = false },
            title = { Text("Detalle de entrada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Lugar: ${entry.place}")
                    Text("Personas: ${entry.people}")
                    Text("Situación y hechos: ${entry.situationFacts}")
                    Text("Pensamientos: ${entry.thoughts}")
                    Text("Acciones: ${entry.actions}")
                    Text("Notas: ${entry.notes}")
                    Divider()
                    Text("Emociones:", fontWeight = FontWeight.SemiBold)
                    entry.emotions.forEach {
                        Text("- ${it.label}: ${it.intensity}/5")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Intensidad general: ${entry.generalIntensity}/5")
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetail = false }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
private fun AudioRow(audioFile: File, meta: AudioMeta?) {
    val title = audioFile.nameWithoutExtension // a_YYYYMMDD_HHMM_lugar
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
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
}

/* ==== Reproductor mini ==== */

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
            mp.setOnCompletionListener {
                playing = false
                position = duration
            }
            mp.prepareAsync()
            player = mp
        } catch (_: Exception) {
            mp.release()
            player = null
            prepared = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

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
                if (playing) {
                    player?.pause()
                    playing = false
                } else {
                    if (position >= duration) {
                        player?.seekTo(0)
                        position = 0
                    }
                    player?.start()
                    playing = true
                }
            }, enabled = can) { Text(if (playing) "Pausar" else "Reproducir") }

            OutlinedButton(onClick = {
                if (!can) return@OutlinedButton
                player?.pause()
                player?.seekTo(0)
                position = 0
                playing = false
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
