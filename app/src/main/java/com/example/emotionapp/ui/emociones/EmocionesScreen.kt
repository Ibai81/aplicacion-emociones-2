package com.example.emotionapp.ui.emociones

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.emotionapp.defaultEmotionPalette
import com.example.emotionapp.data.saveEmotionEntryFile

/* ===== Modelos ===== */
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

/* ===== Pantalla ===== */

@Composable
fun EmotionScreen(getEmotionColor: (String) -> Color) {
    val context = LocalContext.current

    val selected: SnapshotStateMap<String, Int> = remember { mutableStateMapOf() }
    var generalIntensity by remember { mutableStateOf(3) }

    var place by remember { mutableStateOf(TextFieldValue("")) }
    var people by remember { mutableStateOf(TextFieldValue("")) }
    var thoughts by remember { mutableStateOf(TextFieldValue("")) }
    var actions by remember { mutableStateOf(TextFieldValue("")) }
    var notes by remember { mutableStateOf(TextFieldValue("")) }
    var situationFacts by remember { mutableStateOf(TextFieldValue("")) }

    var editingKey by remember { mutableStateOf<String?>(null) }
    val currentIntensity = editingKey?.let { selected[it] } ?: 3

    val conf = LocalConfiguration.current
    val screenWidthDp = conf.screenWidthDp.dp
    val paddingExterior = 16.dp
    val espacioEntreBotones = 8.dp
    val botonesPorFila = if (conf.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 6 else 4
    val buttonWidth = calcularAnchoBoton(screenWidthDp, paddingExterior, espacioEntreBotones, botonesPorFila)
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
                Text("Seleccionadas", modifier = Modifier.padding(horizontal = paddingExterior, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = buttonWidth),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = paddingExterior)
                        .heightIn(min = buttonHeight + 12.dp, max = buttonHeight * 3 + 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                    verticalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                    userScrollEnabled = true
                ) {
                    items(selected.keys.toList()) { key ->
                        val def = defaultEmotionPalette.firstOrNull { it.key == key }
                        val level = selected[key] ?: 3
                        EmotionBarButton(
                            label = def?.label ?: key,
                            baseColor = getEmotionColor(key),
                            width = buttonWidth,
                            height = buttonHeight,
                            intensityLevel = level,
                            onTap = { editingKey = key },
                            onLongPress = { selected.remove(key) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = paddingExterior, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Registra tu entrada", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            item {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = buttonWidth),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = buttonHeight * 2 + 16.dp, max = buttonHeight * 4 + 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                    verticalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                    userScrollEnabled = true
                ) {
                    items(defaultEmotionPalette) { emo ->
                        val levelIfSelected = selected[emo.key]
                        EmotionBarButton(
                            label = emo.label,
                            baseColor = getEmotionColor(emo.key),
                            width = buttonWidth,
                            height = buttonHeight,
                            intensityLevel = levelIfSelected,
                            onTap = {
                                if (levelIfSelected == null) selected[emo.key] = 3
                                editingKey = emo.key
                            },
                            onLongPress = { if (levelIfSelected != null) selected.remove(emo.key) }
                        )
                    }
                }
            }

            item { Text("Intensidad general", style = MaterialTheme.typography.titleMedium) }
            item { NumberPickerRow(selected = generalIntensity, onSelect = { generalIntensity = it }) }

            item { OutlinedTextField(place, { place = it }, label = { Text("Lugar") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(people, { people = it }, label = { Text("Personas") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(thoughts, { thoughts = it }, label = { Text("Pensamientos") }, modifier = Modifier.fillMaxWidth(), maxLines = 4) }
            item { OutlinedTextField(actions, { actions = it }, label = { Text("Lo que hiciste") }, modifier = Modifier.fillMaxWidth(), maxLines = 4) }
            item { OutlinedTextField(situationFacts, { situationFacts = it }, label = { Text("Situación y hechos") }, modifier = Modifier.fillMaxWidth(), maxLines = 6) }
            item { OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth(), maxLines = 4) }

            item {
                Button(
                    onClick = {
                        val items = selected.map { (key, level) ->
                            val def = defaultEmotionPalette.firstOrNull { it.key == key }
                            EmotionItem(key, def?.label ?: key, level)
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
                        runCatching { saveEmotionEntryFile(context, entry) }
                            .onSuccess {
                                Toast.makeText(context, "Emoción guardada en gestor.", Toast.LENGTH_LONG).show()
                                // reset
                                selected.clear()
                                generalIntensity = 3
                                place = TextFieldValue("")
                                people = TextFieldValue("")
                                thoughts = TextFieldValue("")
                                actions = TextFieldValue("")
                                notes = TextFieldValue("")
                                situationFacts = TextFieldValue("")
                            }
                            .onFailure {
                                Toast.makeText(context, "Error al guardar: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar en gestor") }
            }
        }
    }

    val key = editingKey
    if (key != null) {
        val def = defaultEmotionPalette.firstOrNull { it.key == key }
        var temp by remember(key) { mutableStateOf(currentIntensity.coerceIn(1, 5)) }

        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text("Intensidad: ${def?.label ?: key}") },
            text = { NumberPickerRow(selected = temp, onSelect = { temp = it }) },
            dismissButton = {
                TextButton(onClick = { selected.remove(key); editingKey = null }) { Text("Deseleccionar") }
            },
            confirmButton = {
                TextButton(onClick = { selected[key] = temp; editingKey = null }) { Text("Aceptar") }
            }
        )
    }
}

/* ---- UI auxiliares (igual que antes) ---- */

@Composable
private fun EmotionBarButton(
    label: String,
    baseColor: Color,
    width: Dp,
    height: Dp,
    intensityLevel: Int?, // null si no seleccionada
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val level = intensityLevel?.coerceIn(1, 5) ?: 0
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, Color.Black, shape)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) }
    ) {
        Row(modifier = Modifier.matchParentSize()) {
            if (level > 0) {
                Box(
                    modifier = Modifier
                        .weight(level.toFloat())
                        .fillMaxHeight()
                        .background(baseColor)
                ) {
                    if (level < 5) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Color.Black.copy(alpha = 0.28f))
                        )
                    }
                }
                val rem = 5 - level
                if (rem > 0) {
                    Spacer(
                        modifier = Modifier
                            .weight(rem.toFloat())
                            .fillMaxHeight()
                    )
                }
            } else {
                Spacer(
                    modifier = Modifier
                        .weight(5f)
                        .fillMaxHeight()
                )
            }
        }
        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            AutoResizeText(
                text = label.uppercase(),
                color = MaterialTheme.colorScheme.onSurface,
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
        onTextLayout = { r -> if (r.hasVisualOverflow && textSize > minTextSize) textSize = (textSize.value - step.value).sp }
    )
}

@Composable
private fun NumberPickerRow(selected: Int, onSelect: (Int) -> Unit) {
    val nums = (1..5).toList()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    .pointerInput(Unit) { detectTapGestures(onTap = { onSelect(n) }) },
                contentAlignment = Alignment.Center
            ) { Text("$n", color = fg, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun calcularAnchoBoton(screenWidthDp: Dp, paddingHorizontal: Dp, espacioEntreBotones: Dp, botonesPorFila: Int): Dp {
    val anchoDisponible = screenWidthDp - (paddingHorizontal * 2)
    val totalEspacios = espacioEntreBotones * (botonesPorFila - 1)
    return (anchoDisponible - totalEspacios) / botonesPorFila
}
