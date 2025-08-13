package com.example.emotionapp.ui.emociones

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.defaultEmotionPalette
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/* ===== Modelos de registro (propios de esta pantalla) ===== */

data class EmotionItem(val key: String, val label: String, val intensity: Int)
data class EmotionEntry(
    val emotions: List<EmotionItem>,
    val generalIntensity: Int,
    val place: String,
    val people: String,
    val thoughts: String,
    val actions: String,
    val notes: String,
    val situationFacts: String = ""
)

/* ===== Pantalla de Emociones ===== */

@Composable
fun EmotionScreen(
    getEmotionColor: (String) -> Color
) {
    val context = LocalContext.current

    // Estado: emoción -> intensidad (1..5)
    var selected: Map<String, Int> by remember { mutableStateOf(emptyMap()) }
    var generalIntensity by remember { mutableStateOf(3) }

    // Campos
    var place by remember { mutableStateOf(TextFieldValue("")) }
    var people by remember { mutableStateOf(TextFieldValue("")) }
    var thoughts by remember { mutableStateOf(TextFieldValue("")) }
    var actions by remember { mutableStateOf(TextFieldValue("")) }
    var notes by remember { mutableStateOf(TextFieldValue("")) }
    var situationFacts by remember { mutableStateOf(TextFieldValue("")) }

    // Diálogo para ajustar intensidad por emoción
    var editingKey by remember { mutableStateOf<String?>(null) }
    val currentIntensity = selected[editingKey] ?: 3

    // Tamaños unificados de botones
    val conf = LocalConfiguration.current
    val screenWidthDp = conf.screenWidthDp.dp
    val paddingExterior = 16.dp
    val espacioEntreBotones = 8.dp
    val botonesPorFila = if (conf.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 6 else 4
    val buttonWidth = calcularAnchoBoton(
        screenWidthDp, paddingExterior, espacioEntreBotones, botonesPorFila
    )
    val buttonHeight = 48.dp

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider()
                Text(
                    "Seleccionadas",
                    modifier = Modifier.padding(horizontal = paddingExterior, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = buttonWidth),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = paddingExterior)
                        .heightIn(
                            min = buttonHeight + 12.dp,
                            max = buttonHeight * 3 + 24.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                    verticalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                    userScrollEnabled = true
                ) {
                    items(selected.keys.toList()) { key ->
                        val def = defaultEmotionPalette.first { it.key == key }
                        val level = selected[key] ?: 3
                        EmotionSquareButton(
                            label = def.label,
                            color = getEmotionColor(def.key),
                            width = buttonWidth,
                            height = buttonHeight,
                            onClick = { editingKey = key },
                            onLongClick = { selected = selected - key },
                            intensityBg = level
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = paddingExterior, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Registra tu entrada",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = buttonWidth),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = buttonHeight * 2 + 16.dp,
                            max = buttonHeight * 4 + 32.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                    verticalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                    userScrollEnabled = true
                ) {
                    items(defaultEmotionPalette) { emo ->
                        val levelIfSelected = selected[emo.key]
                        EmotionSquareButton(
                            label = emo.label,
                            color = getEmotionColor(emo.key),
                            width = buttonWidth,
                            height = buttonHeight,
                            onClick = {
                                if (levelIfSelected != null) {
                                    editingKey = emo.key
                                } else {
                                    if (selected.size < 10) {
                                        selected = selected + (emo.key to 3)
                                    } else {
                                        Toast.makeText(context, "Máximo 10 emociones.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onLongClick = {
                                if (levelIfSelected != null) selected = selected - emo.key
                            },
                            intensityBg = levelIfSelected
                        )
                    }
                }
            }

            item { Text("Intensidad general", style = MaterialTheme.typography.titleMedium) }
            item {
                NumberPickerRow(
                    selected = generalIntensity,
                    onSelect = { generalIntensity = it }
                )
            }

            item {
                OutlinedTextField(
                    value = place, onValueChange = { place = it },
                    label = { Text("Lugar") }, modifier = Modifier.fillMaxWidth(), maxLines = 1
                )
            }
            item {
                OutlinedTextField(
                    value = people, onValueChange = { people = it },
                    label = { Text("Personas") }, modifier = Modifier.fillMaxWidth(), maxLines = 1
                )
            }
            item {
                OutlinedTextField(
                    value = thoughts, onValueChange = { thoughts = it },
                    label = { Text("Pensamientos") }, modifier = Modifier.fillMaxWidth(), maxLines = 4
                )
            }
            item {
                OutlinedTextField(
                    value = actions, onValueChange = { actions = it },
                    label = { Text("Lo que hiciste") }, modifier = Modifier.fillMaxWidth(), maxLines = 4
                )
            }
            item {
                OutlinedTextField(
                    value = situationFacts, onValueChange = { situationFacts = it },
                    label = { Text("Situación y hechos") }, modifier = Modifier.fillMaxWidth(), maxLines = 6
                )
            }
            item {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notas") }, modifier = Modifier.fillMaxWidth(), maxLines = 4
                )
            }

            item {
                Button(
                    onClick = {
                        val items = selected.map { (key, level) ->
                            val def = defaultEmotionPalette.first { it.key == key }
                            EmotionItem(def.key, def.label, level)
                        }
                        val entry = EmotionEntry(
                            emotions = items,
                            generalIntensity = generalIntensity,
                            place = place.text,
                            people = people.text,
                            thoughts = thoughts.text,
                            actions = actions.text,
                            notes = notes.text,
                            situationFacts = situationFacts.text
                        )
                        saveEntry(context, entry)
                        Toast.makeText(
                            context,
                            "Registro guardado (${entry.emotions.size} emociones)",
                            Toast.LENGTH_LONG
                        ).show()

                        // reset
                        selected = emptyMap()
                        generalIntensity = 3
                        place = TextFieldValue("")
                        people = TextFieldValue("")
                        thoughts = TextFieldValue("")
                        actions = TextFieldValue("")
                        notes = TextFieldValue("")
                        situationFacts = TextFieldValue("")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar registro") }
            }
        }
    }

    // Diálogo de intensidad por números
    if (editingKey != null) {
        val def = defaultEmotionPalette.first { it.key == editingKey }
        var temp by remember(editingKey) { mutableStateOf(currentIntensity) }

        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text("Intensidad: ${def.label}") },
            text = {
                Column {
                    NumberPickerRow(
                        selected = temp,
                        onSelect = { temp = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selected = selected + (def.key to temp)
                    editingKey = null
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { editingKey = null }) { Text("Cancelar") }
            }
        )
    }
}

/* ---------- Composables auxiliares ---------- */

@Composable
private fun EmotionSquareButton(
    label: String,
    color: Color,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    intensityBg: Int? = null
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(width)
            .height(height)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongClick() }) },
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (intensityBg != null) {
                Text(
                    text = intensityBg.toString(),
                    fontSize = (height.value * 0.85f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black, // negro sólido
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-(height.value * 0.12f)).dp) // un pelín arriba
                )
            }
            AutoResizeText(
                text = label.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                minTextSize = 10.sp,
                maxTextSize = 18.sp
            )
        }
    }
}

@Composable
private fun AutoResizeText(
    text: String,
    color: Color,
    fontWeight: FontWeight,
    maxLines: Int,
    minTextSize: TextUnit = 10.sp,
    maxTextSize: TextUnit = 18.sp,
    step: TextUnit = 1.sp
) {
    var textSize by remember(text) { mutableStateOf(maxTextSize) }
    Text(
        text = text,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        softWrap = false,
        fontSize = textSize,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { r ->
            if (r.hasVisualOverflow && textSize > minTextSize) {
                textSize = (textSize.value - step.value).sp
            }
        }
    )
}

@Composable
private fun NumberPickerRow(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val nums = (1..5).toList()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        nums.forEach { n ->
            val isSel = n == selected
            val bg = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelect(n) },
                contentAlignment = Alignment.Center
            ) { Text("$n", color = fg, fontWeight = FontWeight.Bold) }
        }
    }
}

/* ===== Persistencia de registros ===== */

private fun saveEntry(context: Context, entry: EmotionEntry) {
    val prefs = context.getSharedPreferences("emotion_data", Context.MODE_PRIVATE)
    val gson = Gson()
    val type = object : TypeToken<MutableList<EmotionEntry>>() {}.type

    val currentJson = prefs.getString("entries", null)
    val currentList: MutableList<EmotionEntry> =
        if (currentJson.isNullOrBlank()) mutableListOf()
        else gson.fromJson(currentJson, type)

    currentList.add(entry)
    prefs.edit().putString("entries", gson.toJson(currentList)).apply()
}

@Suppress("unused")
private fun loadEntries(context: Context): List<EmotionEntry> {
    val prefs = context.getSharedPreferences("emotion_data", Context.MODE_PRIVATE)
    val json = prefs.getString("entries", null) ?: return emptyList()
    val type = object : TypeToken<List<EmotionEntry>>() {}.type
    return Gson().fromJson(json, type)
}

/* ===== Util ===== */

private fun calcularAnchoBoton(
    screenWidthDp: Dp,
    paddingHorizontal: Dp,
    espacioEntreBotones: Dp,
    botonesPorFila: Int
): Dp {
    val anchoDisponible = screenWidthDp - (paddingHorizontal * 2)
    val totalEspacios = espacioEntreBotones * (botonesPorFila - 1)
    return (anchoDisponible - totalEspacios) / botonesPorFila
}
