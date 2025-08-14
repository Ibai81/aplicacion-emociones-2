package com.example.emotionapp.ui.configuracion

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.data.*

/* === Mover enum a top-level (fuera del @Composable) === */
enum class EditTarget { PLACE, PERSON, SENSATION }

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

    // ===== Estado editor Lugares / Personas / Sensaciones =====
    var places by remember { mutableStateOf(loadPlaceSuggestions(context).toMutableList()) }
    var people by remember { mutableStateOf(loadPeopleSuggestions(context).toMutableList()) }
    var sensations by remember { mutableStateOf(loadSensationsSuggestions(context).toMutableList()) }

    var newPlace by remember { mutableStateOf("") }
    var newPerson by remember { mutableStateOf("") }
    var newSensation by remember { mutableStateOf("") }

    // Diálogo de edición (compartido para los 3 tipos)
    var showEdit by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf(EditTarget.PLACE) }
    var editIndex by remember { mutableStateOf(-1) }
    var editText by remember { mutableStateOf("") }

    fun openEditPlace(index: Int, value: String) {
        editTarget = EditTarget.PLACE
        editIndex = index
        editText = value
        showEdit = true
    }
    fun openEditPerson(index: Int, value: String) {
        editTarget = EditTarget.PERSON
        editIndex = index
        editText = value
        showEdit = true
    }
    fun openEditSensation(index: Int, value: String) {
        editTarget = EditTarget.SENSATION
        editIndex = index
        editText = value
        showEdit = true
    }

    fun saveEdit() {
        val txt = editText.trim()
        if (txt.isEmpty() || editIndex !in 0..Int.MAX_VALUE) return
        when (editTarget) {
            EditTarget.PLACE -> {
                val list = places.toMutableList()
                if (editIndex in list.indices) {
                    list[editIndex] = txt
                    places = list
                    replacePlaceSuggestions(context, places)
                    Toast.makeText(context, "Lugar guardado", Toast.LENGTH_SHORT).show()
                }
            }
            EditTarget.PERSON -> {
                val list = people.toMutableList()
                if (editIndex in list.indices) {
                    list[editIndex] = txt
                    people = list
                    replacePeopleSuggestions(context, people)
                    Toast.makeText(context, "Persona guardada", Toast.LENGTH_SHORT).show()
                }
            }
            EditTarget.SENSATION -> {
                val list = sensations.toMutableList()
                if (editIndex in list.indices) {
                    list[editIndex] = txt
                    sensations = list
                    replaceSensationsSuggestions(context, sensations)
                    Toast.makeText(context, "Sensación guardada", Toast.LENGTH_SHORT).show()
                }
            }
        }
        showEdit = false
    }

    fun deleteEdit() {
        when (editTarget) {
            EditTarget.PLACE -> {
                val list = places.toMutableList()
                if (editIndex in list.indices) {
                    list.removeAt(editIndex)
                    places = list
                    replacePlaceSuggestions(context, places)
                    Toast.makeText(context, "Lugar eliminado", Toast.LENGTH_SHORT).show()
                }
            }
            EditTarget.PERSON -> {
                val list = people.toMutableList()
                if (editIndex in list.indices) {
                    list.removeAt(editIndex)
                    people = list
                    replacePeopleSuggestions(context, people)
                    Toast.makeText(context, "Persona eliminada", Toast.LENGTH_SHORT).show()
                }
            }
            EditTarget.SENSATION -> {
                val list = sensations.toMutableList()
                if (editIndex in list.indices) {
                    list.removeAt(editIndex)
                    sensations = list
                    replaceSensationsSuggestions(context, sensations)
                    Toast.makeText(context, "Sensación eliminada", Toast.LENGTH_SHORT).show()
                }
            }
        }
        showEdit = false
    }

    fun String.splitClean(): List<String> =
        this.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Configuración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        /* ----- Color de la aplicación ----- */
        item {
            Text("Color de la aplicación", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowColorGrid(colors = palette, selected = primaryColor, onPick = onPickPrimary)
            Spacer(Modifier.height(6.dp))
            Text("Elige el color principal de la interfaz.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item { Divider() }

        /* ====== Lugares ====== */
        item { Text("Lugares guardados", style = MaterialTheme.typography.titleMedium) }
        item {
            CompactTagGrid(
                items = places,
                onTap = { idx, value -> openEditPlace(idx, value) }
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
                    val items = newPlace.splitClean()
                    if (items.isNotEmpty()) {
                        val base = places.toMutableList()
                        val seen = base.map { it.lowercase() }.toMutableSet()
                        for (v in items) if (seen.add(v.lowercase())) base.add(0, v)
                        places = base
                        replacePlaceSuggestions(context, places)
                        newPlace = ""
                    }
                }) { Text("Añadir") }
            }
        }

        item { Divider() }

        /* ====== Personas ====== */
        item { Text("Personas guardadas", style = MaterialTheme.typography.titleMedium) }
        item {
            CompactTagGrid(
                items = people,
                onTap = { idx, value -> openEditPerson(idx, value) }
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
                    val items = newPerson.splitClean()
                    if (items.isNotEmpty()) {
                        val base = people.toMutableList()
                        val seen = base.map { it.lowercase() }.toMutableSet()
                        for (v in items) if (seen.add(v.lowercase())) base.add(0, v)
                        people = base
                        replacePeopleSuggestions(context, people)
                        newPerson = ""
                    }
                }) { Text("Añadir") }
            }
        }

        item { Divider() }

        /* ====== Sensaciones corporales ====== */
        item { Text("Sensaciones corporales guardadas", style = MaterialTheme.typography.titleMedium) }
        item {
            CompactTagGrid(
                items = sensations,
                onTap = { idx, value -> openEditSensation(idx, value) }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSensation,
                    onValueChange = { newSensation = it },
                    label = { Text("Añadir sensación (puedes separar con comas)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = {
                    val items = newSensation.splitClean()
                    if (items.isNotEmpty()) {
                        val base = sensations.toMutableList()
                        val seen = base.map { it.lowercase() }.toMutableSet()
                        for (v in items) if (seen.add(v.lowercase())) base.add(0, v)
                        sensations = base
                        replaceSensationsSuggestions(context, sensations)
                        newSensation = ""
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { editingEmotion = emo }
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(emo.label, fontWeight = FontWeight.SemiBold)
                            Text("Toca para elegir color", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(current)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onResetAll() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Restaurar todos") }
                }
            }
        }
    }

    // ===== Diálogo de edición de color por emoción =====
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

    // ===== Diálogo de edición de texto (lugar/persona/sensación) =====
    if (showEdit) {
        val title = when (editTarget) {
            EditTarget.PLACE -> "Editar lugar"
            EditTarget.PERSON -> "Editar persona"
            EditTarget.SENSATION -> "Editar sensación"
        }
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text(title) },
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

/* =================== UI helpers =================== */

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
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onPick(c) }
            )
        }
    }
}

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
