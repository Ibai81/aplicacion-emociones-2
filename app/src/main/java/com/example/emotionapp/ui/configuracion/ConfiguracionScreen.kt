package com.example.emotionapp.ui.configuracion

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.defaultEmotionPalette
import com.example.emotionapp.data.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(
    primaryColor: Color,
    onColorSelected: (Color) -> Unit,
    emotionColors: SnapshotStateMap<String, Color>,
    onPickForEmotion: (String, Color) -> Unit,
    palette: List<Color>,
    onResetAll: () -> Unit
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    // Cargas iniciales
    var topics by remember { mutableStateOf(loadTopicSuggestions(context)) }
    var places by remember { mutableStateOf(loadPlaceSuggestions(context)) }
    var people by remember { mutableStateOf(loadPeopleSuggestions(context)) }
    var sensations by remember { mutableStateOf(loadSensationsSuggestions(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        // -------------------- Color primario de la app --------------------
        Card {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Color primario de la app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text(
                    "La barra de estado (hora/batería) cambia al color primario.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // -------------------- Colores por emoción (botón + desplegable de opciones) --------------------
        Card {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Colores por emoción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                defaultEmotionPalette.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { def ->
                            val current = emotionColors[def.key] ?: def.color
                            EmotionColorButton(
                                label = def.label,
                                currentColor = current,
                                palette = palette,
                                onPick = { picked -> onPickForEmotion(def.key, picked) }
                            )
                        }
                    }
                }

                OutlinedButton(onClick = onResetAll) { Text("Restablecer todo") }
            }
        }

        // -------------------- Gestores con desplegable + añadir + guardar + borrar/seleccionar --------------------
        ManageListSection(
            title = "Temas",
            items = topics,
            onItemsChange = { topics = it; replaceTopicSuggestions(context, it) }
        )

        ManageListSection(
            title = "Lugares",
            items = places,
            onItemsChange = { places = it; replacePlaceSuggestions(context, it) }
        )

        ManageListSection(
            title = "Personas",
            items = people,
            onItemsChange = { people = it; replacePeopleSuggestions(context, it) }
        )

        ManageListSection(
            title = "Sensaciones",
            items = sensations,
            onItemsChange = { sensations = it; replaceSensationsSuggestions(context, it) }
        )
    }
}

/* =================== Subcomponentes =================== */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EmotionColorButton(
    label: String,
    currentColor: Color,
    palette: List<Color>,
    onPick: (Color) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(containerColor = currentColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(label)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // SOLO opciones de color
            Column(Modifier.padding(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable {
                                    onPick(c)
                                    expanded = false
                                }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageListSection(
    title: String,
    items: List<String>,
    onItemsChange: (List<String>) -> Unit
) {
    val context = LocalContext.current
    var list by remember(items) { mutableStateOf(items) }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }

    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            // Desplegable para ver los que hay
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selected.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Listado") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (list.isEmpty()) {
                        DropdownMenuItem(text = { Text("— vacío —") }, onClick = { })
                    } else {
                        list.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    selected = item
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Botones: borrar / seleccionar
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val sel = selected
                        if (sel.isNullOrBlank()) {
                            Toast.makeText(context, "Elige un elemento.", Toast.LENGTH_SHORT).show(); return@OutlinedButton
                        }
                        val newList = list.filter { !it.equals(sel, ignoreCase = true) }
                        list = newList
                        onItemsChange(newList)
                        if (input.equals(sel, ignoreCase = true)) input = ""
                        selected = null
                        Toast.makeText(context, "Borrado: $sel", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Borrar") }

                Button(
                    onClick = {
                        val sel = selected
                        if (sel.isNullOrBlank()) {
                            Toast.makeText(context, "Elige un elemento.", Toast.LENGTH_SHORT).show(); return@Button
                        }
                        // “Seleccionar” = volcamos al cuadro de texto para editar o guardar
                        input = sel
                        Toast.makeText(context, "Seleccionado: $sel", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Seleccionar") }
            }

            // Cuadro para añadir/editar + Guardar
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Añadir / Editar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val v = input.trim()
                        if (v.isEmpty()) {
                            Toast.makeText(context, "Escribe algo.", Toast.LENGTH_SHORT).show(); return@Button
                        }
                        val exists = list.any { it.equals(v, ignoreCase = true) }
                        val newList = if (exists) {
                            list.filterNot { it.equals(v, ignoreCase = true) }.let { listOf(v) + it }
                        } else listOf(v) + list
                        list = newList
                        onItemsChange(newList)
                        Toast.makeText(context, "Guardado.", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Guardar") }

                OutlinedButton(
                    onClick = {
                        // Actualizar desde almacenamiento (por si cambió en otras pantallas)
                        when (title) {
                            "Temas" -> list = loadTopicSuggestions(context)
                            "Lugares" -> list = loadPlaceSuggestions(context)
                            "Personas" -> list = loadPeopleSuggestions(context)
                            "Sensaciones" -> list = loadSensationsSuggestions(context)
                        }
                        Toast.makeText(context, "Lista actualizada.", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Actualizar lista") }
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
    ConfiguracionScreen(
        primaryColor = primaryColor,
        onColorSelected = onPickPrimary,
        emotionColors = emotionColors,
        onPickForEmotion = onPickForEmotion,
        palette = palette,
        onResetAll = onResetAll
    )
}
