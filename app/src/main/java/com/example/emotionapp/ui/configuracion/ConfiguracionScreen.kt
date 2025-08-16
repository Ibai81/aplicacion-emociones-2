package com.example.emotionapp.ui.configuracion

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.defaultEmotionPalette
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.data.loadTopicSuggestions
import com.example.emotionapp.data.replaceTopicSuggestions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfiguracionScreen(
    primaryColor: Color,
    onColorSelected: (Color) -> Unit,
    emotionColors: SnapshotStateMap<String, Color>,
    onResetAll: () -> Unit
) {
    val context = LocalContext.current

    // ====== Estado local de temas ======
    var topics by remember { mutableStateOf(loadTopicSuggestions(context).toMutableList()) }
    var newTopic by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        // -------------------- Color primario (ejemplo simple) --------------------
        Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Color primario de la app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val palette = listOf(
                        Color(0xFF6A1B9A), Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF00ACC1),
                        Color(0xFF43A047), Color(0xFFF4511E), Color(0xFFFB8C00), Color(0xFFFDD835),
                        Color(0xFF546E7A), Color(0xFF8D6E63)
                    )
                    palette.forEach { c ->
                        val sel = c == primaryColor
                        FilledTonalButton(onClick = { onColorSelected(c) }) {
                            Box(
                                Modifier
                                    .size(if (sel) 28.dp else 20.dp)
                                    .background(c, shape = MaterialTheme.shapes.small)
                            )
                        }
                    }
                }
                OutlinedButton(onClick = onResetAll) { Text("Restablecer colores de emociones") }
            }
        }

        // -------------------- Colores por emoción (opcional) --------------------
        Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Colores por emoción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    defaultEmotionPalette.forEach { def: EmotionDef ->
                        val color = emotionColors[def.key] ?: def.color
                        ElevatedButton(onClick = {
                            // Rotación simple de tonos / placeholder
                            emotionColors[def.key] = color
                        }) { Text(def.label) }
                    }
                }
            }
        }

        // -------------------- Temas / contextos (Editor) --------------------
        Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Temas / contextos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTopic,
                        onValueChange = { newTopic = it },
                        label = { Text("Añadir tema (ej. trabajo, familia, proyecto)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val t = newTopic.trim()
                        if (t.isEmpty()) return@Button
                        if (topics.any { it.equals(t, ignoreCase = true) }) {
                            Toast.makeText(context, "Ya existe.", Toast.LENGTH_SHORT).show()
                        } else {
                            topics = (topics + t).toMutableList()
                            replaceTopicSuggestions(context, topics) // persistimos
                            newTopic = ""
                        }
                    }) { Text("Añadir") }
                }

                if (topics.isEmpty()) {
                    Text("Aún no hay temas guardados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // Lista de temas con botón borrar
                    topics.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(onClick = { /* no-op */ }, label = { Text(t) })
                            TextButton(onClick = {
                                topics = topics.filter { !it.equals(t, ignoreCase = true) }.toMutableList()
                                replaceTopicSuggestions(context, topics)
                            }) { Text("Borrar") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            topics = loadTopicSuggestions(context).toMutableList()
                            Toast.makeText(context, "Recargado.", Toast.LENGTH_SHORT).show()
                        }) { Text("Recargar") }
                        Button(onClick = {
                            replaceTopicSuggestions(context, topics)
                            Toast.makeText(context, "Temas guardados.", Toast.LENGTH_SHORT).show()
                        }) { Text("Guardar cambios") }
                    }
                }
            }
        }
    }
}

/* Compat: si tu MainActivity usa SettingsScreen, lo exponemos también */
@Composable
fun SettingsScreen(
    primaryColor: Color,
    onPickPrimary: (Color) -> Unit,
    palette: List<Color>,
    defaultPalette: List<EmotionDef>,
    emotionColors: SnapshotStateMap<String, Color>,
    onPickForEmotion: (String, Color) -> Unit,
    onResetAll: () -> Unit
) {
    // Reutiliza la UI anterior (mapeando parámetros)
    ConfiguracionScreen(
        primaryColor = primaryColor,
        onColorSelected = onPickPrimary,
        emotionColors = emotionColors,
        onResetAll = onResetAll
    )
}
