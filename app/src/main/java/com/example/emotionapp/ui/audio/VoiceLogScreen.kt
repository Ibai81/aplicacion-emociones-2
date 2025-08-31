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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.net.toUri
import com.example.emotionapp.data.addPlaceSuggestion
import com.example.emotionapp.data.loadPlaceSuggestions
import com.example.emotionapp.data.saveAudioEntryFiles
import java.io.File
import com.example.emotionapp.data.UiPrefs
import androidx.compose.runtime.collectAsState



@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceLogScreen() {
    val context = LocalContext.current
    val showHints by UiPrefs.observeShowHints(context).collectAsState(initial = true)


    var audioUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var place by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var generalIntensity by remember { mutableStateOf(3) }

    // Motor de grabación (RecorderEngine.kt)
    val engine = remember { AudioRecorderEngine(context) }
    var isRecording by remember { mutableStateOf(false) }

    // Refuerzo SOLO +9 / +18 dB
    var boostDb by remember { mutableStateOf(9) }
    var currentOutputFile by remember { mutableStateOf<File?>(null) }

    val scroll = rememberScrollState()
    var placeSugg by remember { mutableStateOf(loadPlaceSuggestions(context)) }

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

    fun stopRecording() { runCatching { engine.stop() }; isRecording = false }
    DisposableEffect(Unit) { onDispose { if (isRecording) stopRecording() } }

    val micPermission = Manifest.permission.RECORD_AUDIO
    val hasMicPermission: () -> Boolean =
        { ContextCompat.checkSelfPermission(context, micPermission) == PermissionChecker.PERMISSION_GRANTED }
    val requestMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() else Toast.makeText(context, "Permiso de micrófono denegado.", Toast.LENGTH_SHORT).show() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Registro rápido de voz", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        // Controles de grabación + guardar
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (!isRecording) {
                        if (hasMicPermission()) startRecording() else requestMicPermission.launch(micPermission)
                    } else stopRecording()
                }
            ) { Text(if (isRecording) "Detener" else "Grabar") }

            Button(
                enabled = audioUri != null && !isRecording,
                onClick = {
                    val u = audioUri ?: return@Button
                    try {
                        saveAudioEntryFiles(
                            context = context,
                            source = u,
                            description = description.text,
                            generalIntensity = generalIntensity,
                            place = place.text
                        )
                        runCatching { currentOutputFile?.delete(); currentOutputFile = null }
                        addPlaceSuggestion(context, place.text)
                        placeSugg = loadPlaceSuggestions(context)
                        Toast.makeText(context, "Audio guardado.", Toast.LENGTH_SHORT).show()
                        audioUri = null
                        description = TextFieldValue("")
                        place = TextFieldValue("")
                        generalIntensity = 3
                    } catch (_: Exception) {
                        Toast.makeText(context, "Error al guardar.", Toast.LENGTH_SHORT).show()
                    }
                }
            ) { Text("Guardar registro") }
        }

        // Botón DESCARTAR abajo (ancho completo)
        OutlinedButton(
            enabled = audioUri != null && !isRecording,
            onClick = {
                runCatching { currentOutputFile?.delete(); currentOutputFile = null }
                audioUri = null
                Toast.makeText(context, "Descartado.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Descartar") }

        // Refuerzo de grabación: SOLO +9 / +18 dB
        Text("Refuerzo grabación:")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(9, 18).forEach { db ->
                FilterChip(
                    selected = boostDb == db,
                    onClick = { if (!isRecording) boostDb = db },
                    label = { Text("+$db dB") },         // <- sin símbolo $ sobrante
                    enabled = !isRecording
                )
            }
        }

        AudioInfo(uri = audioUri, isRecording = isRecording)
        if (audioUri != null && !isRecording) {
            MiniPlayer(source = audioUri!!)
        }

        // Lugar
        OutlinedTextField(
            value = place, onValueChange = { place = it },
            label = { Text("Lugar") }, singleLine = true,
            placeholder = { if (showHints) Text("Ej.: bar, casa, trabajo…") },
            modifier = Modifier.fillMaxWidth()
        )
        run {
            val typed = place.text.trim()
            val visible = placeSugg
                .filter { it.isNotBlank() }
                .filter { typed.isEmpty() || it.contains(typed, ignoreCase = true) }
                .take(12)
            if (visible.isNotEmpty()) {
                Text("Sugerencias de lugares", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    visible.forEach { s ->
                        AssistChip(onClick = { place = TextFieldValue(s) }, label = { Text(s) })
                    }
                }
            }
        }

        // Descripción
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("Descripción (opcional)") }, minLines = 3,
            placeholder = { if (showHints) Text("¿Qué se oye? ¿Qué pasó?") },
            modifier = Modifier.fillMaxWidth()
        )

        // Intensidad general
        Text("Intensidad general", style = MaterialTheme.typography.titleMedium)
        NumberPickerRow(selected = generalIntensity) { picked -> generalIntensity = picked }
    }
}

/* =================== Auxiliares =================== */

@Composable
private fun AudioInfo(uri: Uri?, isRecording: Boolean) {
    val color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    val label = if (isRecording) "Grabando…" else if (uri != null) "Audio preparado" else "Sin audio"
    Surface(tonalElevation = 1.dp, shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = color, fontWeight = FontWeight.SemiBold)
            if (uri != null && !isRecording) Text(uri.toString().takeLast(24), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniPlayer(source: Uri) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(source) {
        onDispose {
            try { player?.stop(); player?.release() } catch (_: Exception) {}
            player = null
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = {
            if (!playing) {
                try {
                    val p = MediaPlayer().apply {
                        setDataSource(context, source)
                        prepare()
                        start()
                        setOnCompletionListener { playing = false }
                    }
                    player = p
                    playing = true
                } catch (_: Exception) {
                    playing = false
                }
            } else {
                try { player?.stop() } catch (_: Exception) {}
                playing = false
            }
        }) { Text(if (playing) "Parar" else "Reproducir") }
    }
}

@Composable
private fun NumberPickerRow(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { n ->
            val sel = selected == n
            val bg = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(bg, RoundedCornerShape(10.dp))
                    .clickable { onPick(n) },
                contentAlignment = Alignment.Center
            ) { Text("$n", color = fg, fontWeight = FontWeight.Bold) } // <- sin ${'$'}n
        }
    }
}
