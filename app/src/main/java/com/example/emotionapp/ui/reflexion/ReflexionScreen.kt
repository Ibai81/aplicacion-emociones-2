package com.example.emotionapp.ui.reflexion

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Pantalla de reflexión en texto.
 * - Campo grande "Reflexión".
 * - Sugerencias de patrones (si las funciones de data existen).
 * - Guarda como EmotionEntry (paquete data) con momentType="reflexion", captureMode="texto".
 */
@Composable
fun ReflexionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ⚠️ TextFieldValue con Saver para evitar crashes al rotar/recuperar
    var reflexion by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var place by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    // -------- Sugerencias de patrones (protegido: si no existen las funciones, no crashea) --------
    data class SafeReport(
        val message: String,
        val shouldShow: Boolean
    )
    val scroll = rememberScrollState()
    val report by remember {
        mutableStateOf(
            runCatching {
                val r = com.example.emotionapp.data.computePatterns(context, minCount = 4)
                val msg = com.example.emotionapp.data.buildPatternsMessage(r)
                val show = com.example.emotionapp.data.shouldShowPatternsNow(context)
                SafeReport(message = msg, shouldShow = show)
            }.getOrElse { SafeReport(message = "", shouldShow = false) }
        )
    }

    LaunchedEffect(report.shouldShow) {
        if (report.shouldShow) {
            runCatching { com.example.emotionapp.data.markPatternsShown(context) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(0.dp))
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Reflexión en texto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        if (report.message.isNotBlank()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Posibles patrones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(report.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("Lugar (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reflexion,
                    onValueChange = { reflexion = it },
                    label = { Text("Reflexión") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 12
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    val entry = com.example.emotionapp.data.EmotionEntry(
                        emotions = emptyList(),        // aquí solo guardamos el texto de reflexión
                        generalIntensity = 3,          // o ajusta si quieres
                        place = place.text,
                        people = "",
                        thoughts = "",
                        actions = "",
                        notes = reflexion.text,        // guardamos el texto de reflexión en 'notes'
                        situationFacts = ""
                    )
                    runCatching {
                        com.example.emotionapp.data.saveEmotionEntryFileWithMoment(
                            context = context,
                            entry = entry,
                            momentType = "reflexion",
                            captureMode = "texto"
                        )
                    }.onSuccess {
                        Toast.makeText(context, "Reflexión guardada.", Toast.LENGTH_LONG).show()
                        reflexion = TextFieldValue("")
                        place = TextFieldValue("")
                    }.onFailure {
                        Toast.makeText(context, "Error al guardar: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }
    }
}
