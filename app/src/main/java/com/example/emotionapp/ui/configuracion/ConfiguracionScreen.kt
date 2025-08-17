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

        Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Color primario de la app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    appPalette.forEach { c ->
                        val sel = c == primaryColor
                        FilledTonalButton(
                            onClick = { onColorSelected(c); Toast.makeText(context, "Color principal actualizado", Toast.LENGTH_SHORT).show() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(Modifier.size(if (sel) 36.dp else 26.dp).background(c, RoundedCornerShape(8.dp)))
                        }
                    }
                }

                OutlinedButton(onClick = { onResetAll(); Toast.makeText(context, "Colores de emociones restablecidos", Toast.LENGTH_SHORT).show() }) {
                    Text("Restablecer colores de emociones")
                }
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Colores por emoción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    defaultEmotionPalette.forEach { def: EmotionDef ->
                        val current = emotionColors[def.key] ?: def.color
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
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        SuggestionSectionCompact("Lugares", places) { places = it; replacePlaceSuggestions(context, it) }
        SuggestionSectionCompact("Personas", people) { people = it; replacePeopleSuggestions(context, it) }
        SuggestionSectionCompact("Sensaciones corporales", sensations) { sensations = it; replaceSensationsSuggestions(context, it) }
        SuggestionSectionCompact("Temas", topics) { topics = it; replaceTopicSuggestions(context, it) }
    }
}

/* ====== Sección compacta con selector táctil + eliminar ====== */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionSectionCompact(
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

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    placeholder = { Text("Añadir $title") },
                    singleLine = true,
                    modifier = Modifier.weight(1f, fill = false).widthIn(min = 180.dp).fillMaxWidth()
                )
                AssistChip(onClick = {
                    val v = newItem.trim()
                    if (v.isNotEmpty()) {
                        list = (listOf(v) + list).distinctBy { it.lowercase() }.take(30)
                        newItem = ""
                        onReplaceAll(list)
                        Toast.makeText(context, "Añadido", Toast.LENGTH_SHORT).show()
                    }
                }, label = { Text("Agregar") })
                AssistChip(onClick = { showPicker = true }, label = { Text("Seleccionar") })
            }

            if (showPicker) {
                AlertDialog(
                    onDismissRequest = { showPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val selected = picked.filter { it.value }.keys.toList()
                            editors = selected.associateWith { it }
                            showPicker = false
                        }) { Text("Aceptar") }
                    },
                    dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
                    title = { Text("Selecciona $title") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (list.isEmpty()) Text("No hay elementos.")
                            list.forEach { s ->
                                val selected = picked[s] == true
                                val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg, RoundedCornerShape(8.dp))
                                        .clickable { picked[s] = !selected }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(s, color = fg, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { pendingDelete = s }) { Text("Eliminar", color = fg) }
                                }
                            }
                        }
                    }
                )
            }

            val toDelete = pendingDelete
            if (toDelete != null) {
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    confirmButton = {
                        TextButton(onClick = {
                            val newList = list.filterNot { it.equals(toDelete, ignoreCase = true) }
                            list = newList
                            onReplaceAll(newList)
                            val newPicked = picked.toMutableMap().apply { remove(toDelete) }
                            picked = newPicked
                            pendingDelete = null
                            Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show()
                        }) { Text("Eliminar") }
                    },
                    dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
                    title = { Text("Eliminar elemento") },
                    text = { Text("¿Seguro que quieres eliminar “$toDelete”?") }
                )
            }

            // Editores: al GUARDAR, quitar de la lista de edición
            if (editors.isNotEmpty()) {
                val editorEntries = editors.entries.toList()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    editorEntries.forEach { (original, textNow) ->
                        var localText by remember(original) { mutableStateOf(textNow) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = localText,
                                onValueChange = { localText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(original) }
                            )
                            AssistChip(onClick = {
                                val newV = localText.trim()
                                if (newV.isNotEmpty()) {
                                    val without = list.filterNot { it.equals(original, ignoreCase = true) }
                                    val newList = (listOf(newV) + without).distinctBy { it.lowercase() }.take(30)
                                    list = newList
                                    onReplaceAll(newList)
                                    // quitar de la lista de edición
                                    val newEditors = editors.toMutableMap()
                                    newEditors.remove(original)
                                    editors = newEditors
                                    Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show()
                                }
                            }, label = { Text("Guardar") })
                        }
                    }
                }
            }
        }
    }
}

/* Compat SettingsScreen */
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
