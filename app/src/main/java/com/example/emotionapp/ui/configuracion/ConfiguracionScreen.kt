package com.example.emotionapp.ui.configuracion

import android.widget.Toast
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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.defaultEmotionPalette
import com.example.emotionapp.data.*
import com.example.emotionapp.data.UiPrefs
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.collectAsState

/* Color por defecto para cada emoción primaria (fallback si el usuario no personaliza) */
private fun defaultColorForEmotion(key: String): Color = when (key.lowercase()) {
    "miedo"     -> Color(0xFF64B5F6)
    "ira"       -> Color(0xFFE57373)
    "tristeza"  -> Color(0xFF90CAF9)
    "alegria"   -> Color(0xFFFFD54F)
    "asco"      -> Color(0xFF81C784)
    "sorpresa"  -> Color(0xFFFFB74D)
    else        -> Color(0xFF546E7A) // fallback neutro
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfiguracionScreen(
    primaryColor: Color,
    onColorSelected: (Color) -> Unit,
    emotionColors: SnapshotStateMap<String, Color>,
    onResetAll: () -> Unit
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    val appPalette = listOf(
        Color(0xFF6A1B9A), Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF00ACC1),
        Color(0xFF43A047), Color(0xFFF4511E), Color(0xFFFB8C00), Color(0xFFFDD835),
        Color(0xFF546E7A), Color(0xFF8D6E63)
    )
    val emoPalette = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF64B5F6), Color(0xFF4FC3F7),
        Color(0xFF4DB6AC), Color(0xFF81C784), Color(0xFFFFD54F), Color(0xFFFFB74D), Color(0xFFA1887F)
    )

    var places by remember { mutableStateOf(loadPlaceSuggestions(context)) }
    var people by remember { mutableStateOf(loadPeopleSuggestions(context)) }
    var topics by remember { mutableStateOf(loadTopicSuggestions(context)) }
    var sensations by remember { mutableStateOf(loadSensationsSuggestions(context)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        // ---- Descriptores (placeholders) en vivo ----
        val context = LocalContext.current
        val showHints by UiPrefs.observeShowHints(context).collectAsState(initial = true)

        Card {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Descriptores de campos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Muestra un texto guía dentro de cada cuadro hasta que escribes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = showHints, onCheckedChange = { UiPrefs.setShowHints(context, it) })
            }
        }

        // ---- Descriptores (placeholders) ----
        Card {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

        }

        // ---- Color primario de la app ----
        Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Color primario de la app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    appPalette.forEach { c ->
                        val sel = c == primaryColor
                        FilledTonalButton(
                            onClick = {
                                onColorSelected(c)
                                Toast.makeText(context, "Color principal actualizado", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(if (sel) 36.dp else 26.dp)
                                    .background(c, RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        onResetAll()
                        Toast.makeText(context, "Colores de emociones restablecidos", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Restablecer colores de emociones") }
            }
        }

        // ---- Colores por emoción ----
        Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Colores por emoción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    defaultEmotionPalette.forEach { def: EmotionDef ->
                        val current = emotionColors[def.key] ?: defaultColorForEmotion(def.key)
                        var expanded by remember(def.key) { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = current,
                                    contentColor = if (current.luminance() > 0.5f) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.defaultMinSize(minWidth = 120.dp)
                            ) { Text(def.label) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                emoPalette.forEach { c ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(Modifier.size(18.dp).background(c, RoundedCornerShape(4.dp)))
                                                Text("Cambiar a este color")
                                            }
                                        },
                                        onClick = {
                                            emotionColors[def.key] = c
                                            expanded = false
                                            Toast.makeText(context, "Color de ${def.label} actualizado", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        // ---- Listas de sugerencias editables ----
            SuggestionSectionCompact("Lugares", places) { updated ->
                places = updated
                replacePlaceSuggestions(context, updated)
            }
            SuggestionSectionCompact("Personas", people) { updated ->
                people = updated
                replacePeopleSuggestions(context, updated)
            }
            SuggestionSectionCompact("Sensaciones corporales", sensations) { updated ->
                sensations = updated
                replaceSensationsSuggestions(context, updated)
            }
            SuggestionSectionCompact("Temas", topics) { updated ->
                topics = updated
                replaceTopicSuggestions(context, updated)
            }

        }
}

/* ====== Sección compacta con selector táctil + eliminar ====== */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionSectionCompact(
    title: String,
    items: List<String>,
    onReplaceAll: (List<String>) -> Unit
) {
    val context = LocalContext.current
    var list by remember { mutableStateOf(items) }
    var newItem by remember { mutableStateOf("") }

    var showPicker by remember { mutableStateOf(false) }
    var picked by remember(list) { mutableStateOf(list.associateWith { false }.toMutableMap()) }

    // Editores (original -> texto editable)
    var editors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Confirmación para borrar
    var pendingDelete: String? by remember { mutableStateOf(null) }

    ElevatedCard {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            // Chips existentes (editar / borrar con confirmación)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                list.forEach { original ->
                    val editing = editors[original]
                    if (editing == null) {
                        AssistChip(
                            onClick = {
                                editors = editors + (original to original)
                            },
                            label = { Text(original) },
                            leadingIcon = null
                        )
                        AssistChip(
                            onClick = { pendingDelete = original },
                            label = { Text("✕") }
                        )
                    } else {
                        OutlinedTextField(
                            value = editing,
                            onValueChange = { editors = editors + (original to it) },
                            singleLine = true,
                            label = { Text("Editar") }
                        )
                        TextButton(onClick = {
                            val newVal = editing.trim()
                            if (newVal.isNotEmpty()) {
                                list = list.map { if (it == original) newVal else it }
                                editors = editors - original
                                onReplaceAll(list)
                                Toast.makeText(context, "Actualizado", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Guardar") }
                        TextButton(onClick = { editors = editors - original }) { Text("Cancelar") }
                    }
                }
            }

            // Añadir nuevo + selector múltiple
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    singleLine = true,
                    label = { Text("Añadir") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val v = newItem.trim()
                    if (v.isNotEmpty()) {
                        list = (listOf(v) + list).distinctBy { it.lowercase() }.take(30)
                        newItem = ""
                        onReplaceAll(list)
                        Toast.makeText(context, "Añadido", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Agregar") }

                AssistChip(onClick = { showPicker = true }, label = { Text("Seleccionar") })
            }

            if (showPicker) {
                AlertDialog(
                    onDismissRequest = { showPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val selected = picked.filter { it.value }.keys.toList()
                            if (selected.isNotEmpty()) {
                                list = (selected + list).distinctBy { it.lowercase() }.take(30)
                                onReplaceAll(list)
                            }
                            showPicker = false
                        }) { Text("Añadir") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPicker = false }) { Text("Cancelar") }
                    },
                    title = { Text("Seleccionar de la lista") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            list.forEach { s ->
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(s, modifier = Modifier.weight(1f))
                                    Checkbox(
                                        checked = picked[s] == true,
                                        onCheckedChange = { v -> picked[s] = v == true }
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // Confirmación borrado
            pendingDelete?.let { toDelete ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    confirmButton = {
                        TextButton(onClick = {
                            list = list.filterNot { it.equals(toDelete, ignoreCase = true) }
                            onReplaceAll(list)
                            pendingDelete = null
                            Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show()
                        }) { Text("Borrar") }
                    },
                    dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
                    title = { Text("¿Eliminar?") },
                    text = { Text(toDelete) }
                )
            }
        }
    }
}


/* Compat SettingsScreen (si la usabas en otra parte) */
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
    ConfiguracionScreen(
        primaryColor = primaryColor,
        onColorSelected = onPickPrimary,
        emotionColors = emotionColors,
        onResetAll = onResetAll
    )
}

