package com.example.emotionapp.ui.emociones

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.defaultEmotionPalette
import com.example.emotionapp.data.saveEmotionEntryFile
import com.example.emotionapp.data.addPeopleSuggestions
import com.example.emotionapp.data.addPlaceSuggestion
import com.example.emotionapp.data.loadPeopleSuggestions
import com.example.emotionapp.data.loadPlaceSuggestions
import android.content.res.Configuration
import androidx.compose.ui.input.pointer.pointerInput

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
@OptIn(ExperimentalLayoutApi::class)
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

    // Sugerencias persistentes (lugares/personas)
    var placeSugg by remember { mutableStateOf(loadPlaceSuggestions(context)) }
    var peopleSugg by remember { mutableStateOf(loadPeopleSuggestions(context)) }

    var editingKey by remember { mutableStateOf<String?>(null) }
    val currentIntensity = editingKey?.let { selected[it] } ?: 3

    val conf = LocalConfiguration.current
    val screenWidthDp = conf.screenWidthDp.dp
    val paddingExterior = 16.dp
    val espacioEntreBotones = 8.dp
    val botonesPorFila =
        if (conf.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 4
    val buttonWidth =
        calcularAnchoBoton(screenWidthDp, paddingExterior, espacioEntreBotones, botonesPorFila)
    val buttonHeight = 48.dp

    // Scroll para el contenido + padding del teclado
    val scroll = rememberScrollState()

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scroll)     // ← toda la pantalla hace scroll
                .imePadding()
                .padding(horizontal = paddingExterior, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Registra tu entrada", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            // ⬇️ Grid de emociones con ALTURA ACOTADA (clave para evitar el crash)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = buttonWidth),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(                       // ← le damos límites: no infinito
                        min = buttonHeight * 2 + 16.dp,
                        max = buttonHeight * 4 + 32.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                verticalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                userScrollEnabled = false           // ← sin scroll interno; scrollea la página
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

            Text("Intensidad general", style = MaterialTheme.typography.titleMedium)
            NumberPickerRow(selected = generalIntensity, onSelect = { generalIntensity = it })

            /* --------- Lugar + sugerencias --------- */
            OutlinedTextField(
                place,
                { place = it },
                label = { Text("Lugar") },
                singleLine = true,
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
                            AssistChip(
                                onClick = { place = TextFieldValue(s) },
                                label = { Text(s) }
                            )
                        }
                    }
                }
            }

            /* --------- Personas + sugerencias (autocompleta la última palabra) --------- */
            OutlinedTextField(
                people,
                { people = it },
                label = { Text("Personas (separadas por comas)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            run {
                val currentNames = parsePeople(people.text)
                val typedLast = people.text.substringAfterLast(",").trim()
                val visible = peopleSugg
                    .filter { it.isNotBlank() && it !in currentNames }
                    .filter { typedLast.isEmpty() || it.contains(typedLast, ignoreCase = true) }
                    .take(16)

                if (visible.isNotEmpty()) {
                    Text("Sugerencias de personas", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        visible.forEach { name ->
                            AssistChip(
                                onClick = {
                                    val parts = people.text.split(",")
                                    val trimmed = parts.map { it.trim() }.toMutableList()
                                    if (trimmed.isEmpty() || (trimmed.size == 1 && trimmed[0].isEmpty())) {
                                        people = TextFieldValue(name)
                                    } else {
                                        if (typedLast.isNotEmpty()) {
                                            trimmed[trimmed.lastIndex] = name
                                        } else {
                                            if (trimmed.none { it.equals(name, ignoreCase = true) }) {
                                                trimmed.add(name)
                                            }
                                        }
                                        val seen = mutableSetOf<String>()
                                        val final = mutableListOf<String>()
                                        for (p in trimmed) {
                                            if (p.isNotEmpty()) {
                                                val k = p.lowercase()
                                                if (seen.add(k)) final.add(p)
                                            }
                                        }
                                        people = TextFieldValue(final.joinToString(", "))
                                    }
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }
            }

            /* --------- Situación y hechos (debajo de Personas) --------- */
            OutlinedTextField(
                situationFacts,
                { situationFacts = it },
                label = { Text("Situación y hechos") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6
            )

            /* --------- Resto de campos --------- */
            OutlinedTextField(thoughts, { thoughts = it }, label = { Text("Pensamientos") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            OutlinedTextField(actions, { actions = it }, label = { Text("Lo que hiciste") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)

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
                            val p = place.text.trim()
                            if (p.isNotEmpty()) addPlaceSuggestion(context, p)
                            val ppl = parsePeople(people.text)
                            if (ppl.isNotEmpty()) addPeopleSuggestions(context, ppl)

                            placeSugg = loadPlaceSuggestions(context)
                            peopleSugg = loadPeopleSuggestions(context)

                            Toast.makeText(context, "Emoción guardada en gestor.", Toast.LENGTH_LONG).show()
                            selected.clear(); generalIntensity = 3
                            place = TextFieldValue(""); people = TextFieldValue("")
                            thoughts = TextFieldValue(""); actions = TextFieldValue("")
                            notes = TextFieldValue(""); situationFacts = TextFieldValue("")
                        }
                        .onFailure {
                            Toast.makeText(context, "Error al guardar: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar en gestor") }
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

/* ---- Utils de sugerencias ---- */
private fun parsePeople(raw: String): List<String> =
    raw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

/* ---- UI auxiliares ---- */
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
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)

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
                )
                val rem = 5 - level
                if (rem > 0) {
                    Spacer(
                        modifier = Modifier
                            .weight(rem.toFloat())
                            .fillMaxHeight()
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(5f).fillMaxHeight())
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
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(bg)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onSelect(n) }) },
                contentAlignment = Alignment.Center
            ) { Text("$n", color = fg, fontWeight = FontWeight.Bold) }
        }
    }
}

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
