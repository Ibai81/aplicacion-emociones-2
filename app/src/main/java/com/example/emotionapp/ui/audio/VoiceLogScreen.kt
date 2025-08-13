package com.example.emotionapp.ui.audio

import android.Manifest
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.net.toUri
import com.example.emotionapp.data.saveAudioEntryFiles
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class) // por si FilterChip está marcado experimental en tu versión
@Composable
fun VoiceLogScreen() {
    val context = LocalContext.current

    var audioUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var place by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var generalIntensity by remember { mutableStateOf(3) }

    // ---- NUEVO: engine AudioRecord + AGC + boost ----
    val engine = remember { AudioRecorderEngine(context) }
    var isRecording by remember { mutableStateOf(false) }
    var boostDb by remember { mutableStateOf(6) } // 0 / 6 / 9
    var currentOutputFile by remember { mutableStateOf<File?>(null) }

    /* ---------- Funciones locales: declarar ANTES de usarlas ---------- */
    fun startRecording() {
        try {
            val f = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
                "tmp_record_${System.currentTimeMillis()}.m4a"
            )
            engine.start(f, boostDb)
            currentOutputFile = f
            audioUri = f.toUri()
            isRecording = true
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo iniciar la grabación.", Toast.LENGTH_SHORT).show()
            isRecording = false
            currentOutputFile = null
            audioUri = null
        }
    }

    fun stopRecording() {
        try { engine.stop() } catch (_: Exception) {}
        isRecording = false
        // audioUri ya apunta al archivo final
    }

    DisposableEffect(Unit) { onDispose { if (isRecording) stopRecording() } }

    /* ---------- Permiso micrófono (ahora ya puede llamar a startRecording()) ---------- */
    val micPermission = Manifest.permission.RECORD_AUDIO
    val hasMicPermission: () -> Boolean =
        { ContextCompat.checkSelfPermission(context, micPermission) == PermissionChecker.PERMISSION_GRANTED }

    val requestMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "Permiso de micrófono denegado.", Toast.LENGTH_SHORT).show()
    }

    /* ---------- UI ---------- */
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Registro de audio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        GuideCard()

        // BOTONES: Guardar (izq) / Grabar-Detener (dcha)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = {
                    if (isRecording) {
                        Toast.makeText(context, "Detén la grabación antes de guardar.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val src = audioUri ?: run {
                        Toast.makeText(context, "Aún no hay audio. Graba primero.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val lugar = place.text.ifBlank { "sin lugar" }
                    runCatching {
                        saveAudioEntryFiles(
                            context = context,
                            source = src,
                            description = description.text,
                            generalIntensity = generalIntensity,
                            place = lugar
                        )
                    }.onSuccess {
                        Toast.makeText(context, "Audio guardado en gestor.", Toast.LENGTH_LONG).show()
                        // reset UI
                        audioUri = null
                        place = TextFieldValue("")
                        description = TextFieldValue("")
                        generalIntensity = 3
                        currentOutputFile = null
                    }.onFailure {
                        Toast.makeText(context, "Error al guardar: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !isRecording && audioUri != null
            ) { Text("Guardar") }

            Button(onClick = {
                if (isRecording) {
                    stopRecording()
                } else {
                    if (hasMicPermission()) startRecording() else requestMicPermission.launch(micPermission)
                }
            }) { Text(if (isRecording) "Detener" else "Grabar audio") }
        }

        // Selector de Refuerzo (afecta al archivo final)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Refuerzo grabación:")
            listOf(0, 6, 9).forEach { db ->
                FilterChip(
                    selected = boostDb == db,
                    onClick = { if (!isRecording) boostDb = db },
                    label = { Text(if (db == 0) "0 dB" else "+$db dB") },
                    enabled = !isRecording
                )
            }
        }

        // Info + pre-escucha (desactiva mientras graba)
        AudioInfo(uri = audioUri, isRecording = isRecording)
        if (audioUri != null && !isRecording) {
            MiniPlayer(source = audioUri!!)
        }

        // Lugar
        OutlinedTextField(
            value = place, onValueChange = { place = it },
            label = { Text("Lugar") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        // Descripción
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("Descripción (opcional)") }, minLines = 3, modifier = Modifier.fillMaxWidth()
        )
        // Intensidad
        Text("Intensidad general", style = MaterialTheme.typography.titleMedium)
        NumberPickerRow(selected = generalIntensity) { generalIntensity = it }
    }
}

@Composable private fun GuideCard() {
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Guía para la grabación:", fontWeight = FontWeight.SemiBold)
        Text("• Lugar"); Text("• Personas"); Text("• Situación y hechos")
        Text("• Qué has pensado"); Text("• Qué has hecho")
    }
}

@Composable private fun AudioInfo(uri: Uri?, isRecording: Boolean) {
    val label = when {
        isRecording -> "Grabando…"
        uri == null -> "Sin audio seleccionado."
        else -> "Audio listo."
    }
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/* Mini reproductor (solo pre-escucha local) */
@Composable private fun MiniPlayer(source: Uri) {
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
            mp.release(); player = null
        }
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    LaunchedEffect(playing) {
        while (playing) { val p = player?.currentPosition ?: 0; if (!dragging) position = p; delay(200) }
    }

    val can = prepared && player != null && duration > 0

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (!can) return@Button
                if (playing) { player?.pause(); playing = false }
                else { if (position >= duration) { player?.seekTo(0); position = 0 }; player?.start(); playing = true }
            }, enabled = can) { Text(if (playing) "Pausar" else "Reproducir") }

            OutlinedButton(onClick = {
                if (!can) return@OutlinedButton
                player?.pause(); player?.seekTo(0); position = 0; playing = false
            }, enabled = can) { Text("Parar") }
        }

        val value = if (duration <= 0) 0f else (position.coerceIn(0, duration).toFloat() / duration)
        Slider(
            value = value,
            onValueChange = { v -> if (can) { dragging = true; position = (v * duration).toInt() } },
            onValueChangeFinished = { if (can) { player?.seekTo(position); dragging = false } },
            enabled = can
        )
        Text("${format(position)} / ${format(duration)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun NumberPickerRow(selected: Int, onSelect: (Int) -> Unit) {
    val nums = (1..5).toList()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        nums.forEach { n ->
            val isSel = n == selected
            val bg = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier.width(48.dp).height(40.dp).background(bg, RoundedCornerShape(10.dp)).clickable { onSelect(n) },
                contentAlignment = Alignment.Center
            ) { Text("$n", color = fg, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun format(ms: Int): String {
    val sec = (ms / 1000).coerceAtLeast(0); val m = sec / 60; val s = sec % 60
    return "%d:%02d".format(m, s)
}
