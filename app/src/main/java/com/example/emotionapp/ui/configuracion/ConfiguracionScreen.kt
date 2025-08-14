package com.example.emotionapp.ui.configuracion

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.defaultEmotionPalette
import com.example.emotionapp.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    primaryColor: Color,
    onPickPrimary: (Color) -> Unit,
    palette: List<Color>,
    defaultPalette: List<EmotionDef>,
    emotionColors: Map<String, Color>,
    onPickForEmotion: (String, Color) -> Unit,
    onResetAll: () -> Unit
) {
    val context = LocalContext.current
    var editingEmotion by remember { mutableStateOf<EmotionDef?>(null) }

    // Estado editor Lugares/Personas
    var places by remember { mutableStateOf(loadPlaceSuggestions(context).toMutableList()) }
    var people by remember { mutableStateOf(loadPeopleSuggestions(context).toMutableList()) }
    var newPlace by remember { mutableStateOf("") }
    var newPerson by remember { mutableStateOf("") }

    // Diálogo de edición compacta
    var showEdit by remember { mutableStateOf(false) }
    var editIsPlace by remember { mutableStateOf(true) }
    var editIndex by remember { mutableStateOf(-1) }
    var editText by remember { mutableStateOf("") }

    fun openEdit(isPlace: Boolean, index: Int, value: String) {
        editIsPlace = isPlace
        editIndex = index
        editText = value
        showEdit = true
    }

    fun saveEdit() {
        val txt = editText.trim()
        if (txt.isEmpty()) return
        if (editIsPlace) {
            val list = places.toMutableList()
            list[editIndex] = txt
            places = list
            replacePlaceSuggestions(context, places)
            Toast.makeText(context, "Lugar guardado", Toast.LENGTH_SHORT).show()
        } else {
            val list = people.toMutableList()
            list[editIndex] = txt
            people = list
            replacePeopleSuggestions(context, people)
            Toast.makeText(context, "Persona guardada", Toast.LENGTH_SHORT).show()
        }
        showEdit = false
    }

    fun deleteEdit() {
        if (editIsPlace) {
            val list = places.toMutableList()
            if (editIndex in list.indices) {
                list.removeAt(editIndex)
                places = list
                replacePlaceSuggestions(context, places)
                Toast.makeText(context, "Lugar eliminado", Toast.LENGTH_SHORT).show()
            }
        } else {
            val list = people.toMutableList()
            if (editIndex in list.indices) {
                list.removeAt(editIndex)
                people = list
                replacePeopleSuggestions(context, people)
                Toast.makeText(context, "Persona eliminada", Toast.LENGTH_SHORT).show()
            }
        }
        showEdit = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Configuración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        /* ----- Color general de la aplicación ----- */
        item {
            Text("Color de la aplicación", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowColorGrid(colors = palette, selected = primaryColor, onPick = onPickPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Se usa como color primario (botones, resaltados…).",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        item { Divider() }

        /* ====== Lugares (compacto con botones) ====== */
        item { Text("Lugares guardados", style = MaterialTheme.typography.titleMedium) }
        item {
            CompactTagGrid(
                items = places,
                onTap = { idx, value -> openEdit(true, idx, value) }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPlace,
                    onValueChange = { newPlace = it },
                    label = { Text("Añadir lugar") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = {
                    val v = newPlace.trim()
                    if (v.isNotEmpty() && places.none { it.equals(v, ignoreCase = true) }) {
                        places = (listOf(v) + places).toMutableList()
                        replacePlaceSuggestions(context, places)
                        newPlace = ""
                    }
                }) { Text("Añadir") }
            }
        }

        item { Divider() }

        /* ====== Personas (compacto con botones) ====== */
        item { Text("Personas guardadas", style = MaterialTheme.typography.titleMedium) }
        item {
            CompactTagGrid(
                items = people,
                onTap = { idx, value -> openEdit(false, idx, value) }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPerson,
                    onValueChange = { newPerson = it },
                    label = { Text("Añadir persona") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = {
                    val v = newPerson.trim()
                    if (v.isNotEmpty() && people.none { it.equals(v, ignoreCase = true) }) {
                        people = (listOf(v) + people).toMutableList()
                        replacePeopleSuggestions(context, people)
                        newPerson = ""
                    }
                }) { Text("Añadir") }
            }
        }

        item { Divider() }

        /* ----- Colores por emoción ----- */
        item {
            Text("Color de las emociones", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                defaultPalette.forEach { emo ->
                    val current = emotionColors[emo.key] ?: emo.color
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(current)
                                .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            onPickForEmotion(emo.key, emo.color)
                                            Toast.makeText(context, "Restaurado: ${emo.label}", Toast.LENGTH_SHORT).show()
                                        },
                                        onTap = { /* abrir diálogo abajo */ editingEmotion = emo }
                                    )
                                }
                        )
                        Text(emo.label, modifier = Modifier.weight(1f))
                        Text(
                            text = "#%08X".format(current.toArgb()),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        TextButton(onClick = { editingEmotion = emo }) { Text("Cambiar") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onResetAll,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) { Text("Restaurar todos") }
                }
            }
        }
    }

    // Diálogo de edición de color por emoción
    val emo = editingEmotion
    if (emo != null) {
        AlertDialog(
            onDismissRequest = { editingEmotion = null },
            title = { Text("Color para: ${emo.label}") },
            text = {
                FlowColorGrid(
                    colors = palette,
                    selected = emotionColors[emo.key] ?: emo.color,
                    onPick = { c -> onPickForEmotion(emo.key, c); editingEmotion = null }
                )
            },
            confirmButton = { TextButton(onClick = { editingEmotion = null }) { Text("Cerrar") } }
        )
    }

    // Diálogo compacto de edición (texto arriba, Eliminar izquierda, Guardar derecha)
    if (showEdit) {
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text(if (editIsPlace) "Editar lugar" else "Editar persona") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Texto") }
                )
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deleteEdit() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            confirmButton = {
                Button(onClick = { saveEdit() }) { Text("Guardar") }
            }
        )
    }
}

/* ---------- Paleta reutilizable ---------- */
@Composable
private fun FlowColorGrid(
    colors: List<Color>,
    selected: Color,
    onPick: (Color) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 44.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 240.dp)
    ) {
        gridItems(colors) { c ->
            val isSel = c.toArgb() == selected.toArgb()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (isSel) 3.dp else 1.dp,
                        color = if (isSel) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
                    .clickable { onPick(c) }
            )
        }
    }
}

/* ---------- Grid compacto de botones para tags ---------- */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactTagGrid(
    items: List<String>,
    onTap: (index: Int, value: String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEachIndexed { idx, value ->
            FilledTonalButton(
                onClick = { onTap(idx, value) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
