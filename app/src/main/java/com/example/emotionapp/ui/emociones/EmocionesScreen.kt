package com.example.emotionapp.ui.emociones

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.defaultEmotionPalette
import com.example.emotionapp.data.EmotionEntry
import com.example.emotionapp.data.EmotionItem
import com.example.emotionapp.data.PatternsReport
import com.example.emotionapp.data.addPeopleSuggestions
import com.example.emotionapp.data.addPlaceSuggestion
import com.example.emotionapp.data.addSensationsSuggestions
import com.example.emotionapp.data.addTopicSuggestion
import com.example.emotionapp.data.buildPatternsMessage
import com.example.emotionapp.data.computePatterns
import com.example.emotionapp.data.getAdaptativeDefinition
import com.example.emotionapp.data.getBodySensations
import com.example.emotionapp.data.getCriticalDefinition
import com.example.emotionapp.data.getKeyPhrases
import com.example.emotionapp.data.getShowDefsOnSelect
import com.example.emotionapp.data.getUserEmotionDefinition
import com.example.emotionapp.data.loadPeopleSuggestions
import com.example.emotionapp.data.loadPlaceSuggestions
import com.example.emotionapp.data.loadSensationsSuggestions
import com.example.emotionapp.data.loadTopicSuggestions
import com.example.emotionapp.data.saveEmotionEntryFileWithMoment
import com.example.emotionapp.data.setShowDefsOnSelect
import com.example.emotionapp.ui.training.TrainingStrip
import androidx.compose.foundation.layout.imePadding

/* ===== Saver para mantener selección de intensidades por emoción ===== */
private val selectionSaver: Saver<SnapshotStateMap<String, Int>, Any> =
    Saver(
        save = { HashMap(it) },
        restore = { restored ->
            @Suppress("UNCHECKED_CAST")
            val map = restored as? Map<String, Int> ?: emptyMap()
            mutableStateMapOf<String, Int>().apply { putAll(map) }
        }
    )

/* ===== Modelito local para patrones del usuario y sugeridos ===== */
private const val PREFS_PATTERNS = "patterns_prefs"
private const val KEY_PATTERNS_JSON = "patterns_json"

data class EmotionPattern(
    val name: String,
    val generalIntensity: Int,
    val items: List<EmotionItem>
)

private fun loadPatterns(context: android.content.Context): MutableList<EmotionPattern> {
    val prefs = context.getSharedPreferences(PREFS_PATTERNS, android.content.Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_PATTERNS_JSON, "[]") ?: "[]"
    val type = object : com.google.gson.reflect.TypeToken<List<EmotionPattern>>() {}.type
    return (com.google.gson.Gson().fromJson<List<EmotionPattern>>(json, type) ?: emptyList()).toMutableList()
}

private fun savePatterns(context: android.content.Context, list: List<EmotionPattern>) {
    val prefs = context.getSharedPreferences(PREFS_PATTERNS, android.content.Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_PATTERNS_JSON, com.google.gson.Gson().toJson(list)).apply()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmotionScreen(getEmotionColor: (String) -> Color) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Selección estable y persistente (por emoción 0..5)
    val selected: SnapshotStateMap<String, Int> = rememberSaveable(saver = selectionSaver) { mutableStateMapOf() }
    var generalIntensity by rememberSaveable { mutableStateOf(3) } // 1..5

    // Campos de texto
    var place by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var topic by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var people by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var thoughts by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var actions by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var notes by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var situationFacts by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    // Sugerencias persistentes
    var placeSugg by remember { mutableStateOf(loadPlaceSuggestions(context)) }
    var topicsSugg by remember { mutableStateOf(loadTopicSuggestions(context)) }
    var peopleSugg by remember { mutableStateOf(loadPeopleSuggestions(context)) }
    var sensationsSugg by remember { mutableStateOf(loadSensationsSuggestions(context)) }

    // Patrones del usuario
    var patterns by remember { mutableStateOf(loadPatterns(context)) }
    var selectedPatternName by rememberSaveable { mutableStateOf<String?>(null) }

    // Patrones sugeridos por la app (ligeros)
    var suggested by remember { mutableStateOf<List<EmotionPattern>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { computePatterns(context, minCount = 4) }
            .onSuccess { report ->
                val list = report.emotions
                    .take(3)
                    .mapNotNull { item ->
                        val def = defaultEmotionPalette.firstOrNull { def -> def.label.equals(item.name, ignoreCase = true) }
                        def?.let { EmotionPattern("Sugerido: ${def.label}", 3, listOf(EmotionItem(def.key, def.label, 3))) }
                    }
                suggested = list
            }
            .onFailure { suggested = emptyList() }
    }

    // Clave en edición para el diálogo de intensidad
    var editingKey by remember { mutableStateOf<String?>(null) }

    // Layout responsivo
    val conf = LocalConfiguration.current
    val screenWidthDp = conf.screenWidthDp.dp
    val paddingExterior = 16.dp
    val espacioEntreBotones = 8.dp
    val botonesPorFila = if (conf.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 4
    val buttonWidth = calcularAnchoBoton(screenWidthDp, paddingExterior, espacioEntreBotones, botonesPorFila)
    val buttonHeight = 48.dp

    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {

        // -------- CONTENIDO PRINCIPAL (scrolleable) --------
        Column(
            modifier = Modifier
                .matchParentSize()
                .verticalScroll(scroll)
                .imePadding()
                .padding(horizontal = paddingExterior, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Registro completo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            // Grid de emociones
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = buttonWidth),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = buttonHeight * 2 + 16.dp, max = buttonHeight * 4 + 32.dp),
                horizontalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                verticalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                userScrollEnabled = false
            ) {
                items(
                    items = defaultEmotionPalette,
                    key = { it.key }
                ) { emo: EmotionDef ->
                    val levelIfSelected = selected[emo.key]
                    EmotionBarButton(
                        label = emo.label,
                        baseColor = getEmotionColor(emo.key),
                        width = buttonWidth,
                        height = buttonHeight,
                        intensityLevel = levelIfSelected, // 0..5
                        onTap = {
                            if (levelIfSelected == null) selected[emo.key] = 0 // empieza en 0
                            editingKey = emo.key
                        },
                        onLongPress = { if (levelIfSelected != null) selected.remove(emo.key) }
                    )
                }
            }

            // Seleccionadas
            Column {
                Text("Seleccionadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = buttonWidth),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = buttonHeight + 12.dp, max = buttonHeight * 3 + 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                    verticalArrangement = Arrangement.spacedBy(espacioEntreBotones),
                    userScrollEnabled = true
                ) {
                    items(selected.keys.toList(), key = { it }) { key ->
                        val def = defaultEmotionPalette.firstOrNull { it.key == key }
                        val level = selected[key] ?: 0
                        EmotionBarButton(
                            label = def?.label ?: key,
                            baseColor = getEmotionColor(key),
                            width = buttonWidth,
                            height = buttonHeight,
                            intensityLevel = level, // 0..5
                            onTap = { editingKey = key },
                            onLongPress = { selected.remove(key) }
                        )
                    }
                }
            }

            // Patrones sugeridos (ligeros)
            if (suggested.isNotEmpty()) {
                OutlinedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Patrones sugeridos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggested.forEach { p ->
                                AssistChip(
                                    onClick = {
                                        selected.clear()
                                        p.items.forEach { selected[it.key] = it.intensity.coerceIn(0, 5) }
                                        generalIntensity = p.generalIntensity.coerceIn(1, 5)
                                        Toast.makeText(context, "Patrón aplicado: ${p.name}", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text(p.name) }
                                )
                            }
                        }
                        Text(
                            buildPatternsMessage(
                                runCatching { computePatterns(context, minCount = 4) }.getOrElse {
                                    PatternsReport(0, emptyList(), emptyList(), emptyList(), emptyList())
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Patrones guardados por el usuario
            OutlinedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Patrones (tuyos)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        patterns.forEach { p ->
                            FilterChip(
                                selected = selectedPatternName == p.name,
                                onClick = { selectedPatternName = if (selectedPatternName == p.name) null else p.name },
                                label = { Text(p.name) }
                            )
                        }
                    }
                    var newPatternNameLocal by rememberSaveable { mutableStateOf("") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val p = patterns.firstOrNull { it.name == selectedPatternName }
                                if (p == null) {
                                    Toast.makeText(context, "Elige un patrón.", Toast.LENGTH_SHORT).show()
                                } else {
                                    selected.clear()
                                    p.items.forEach { selected[it.key] = it.intensity.coerceIn(0, 5) }
                                    generalIntensity = p.generalIntensity.coerceIn(1, 5)
                                    Toast.makeText(context, "Patrón aplicado.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) { Text("Aplicar") }
                        Button(
                            onClick = {
                                val name = newPatternNameLocal.trim()
                                if (name.isEmpty()) {
                                    Toast.makeText(context, "Pon un nombre.", Toast.LENGTH_SHORT).show(); return@Button
                                }
                                val items = selected.map { (k, lvl) ->
                                    val def = defaultEmotionPalette.firstOrNull { it.key == k }
                                    EmotionItem(k, def?.label ?: k, lvl.coerceIn(0, 5))
                                }
                                if (items.isEmpty()) {
                                    Toast.makeText(context, "Selecciona alguna emoción.", Toast.LENGTH_SHORT).show(); return@Button
                                }
                                val list = patterns.toMutableList().apply {
                                    removeAll { it.name.equals(name, true) }
                                    add(0, EmotionPattern(name, generalIntensity, items))
                                }
                                patterns = list
                                savePatterns(context, list)
                                newPatternNameLocal = ""
                                Toast.makeText(context, "Patrón guardado.", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("Guardar patrón actual") }
                    }
                    OutlinedTextField(
                        value = newPatternNameLocal,
                        onValueChange = { newPatternNameLocal = it },
                        label = { Text("Nombre del patrón") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Intensidad general (1..5)
            Text("Intensidad general", style = MaterialTheme.typography.titleMedium)
            NumberPickerRow(selected = generalIntensity, onSelect = { generalIntensity = it.coerceIn(1, 5) })

            // Tema / contexto + sugerencias (máx 6)
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Tema / contexto (ej. trabajo, familia, proyecto)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            run {
                val typed = topic.text.trim()
                val visible = topicsSugg.filter { it.isNotBlank() && (typed.isEmpty() || it.contains(typed, ignoreCase = true)) }.take(6)
                if (visible.isNotEmpty()) {
                    Text("Sugerencias de temas", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        visible.forEach { s -> AssistChip(onClick = { topic = TextFieldValue(s) }, label = { Text(s) }) }
                    }
                }
            }

            // Lugar + sugerencias (máx 6)
            OutlinedTextField(place, { place = it }, label = { Text("Lugar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            run {
                val typed = place.text.trim()
                val visible = placeSugg.filter { it.isNotBlank() && (typed.isEmpty() || it.contains(typed, ignoreCase = true)) }.take(6)
                if (visible.isNotEmpty()) {
                    Text("Sugerencias de lugares", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        visible.forEach { s -> AssistChip(onClick = { place = TextFieldValue(s) }, label = { Text(s) }) }
                    }
                }
            }

            // Personas + sugerencias (máx 6, autocompleta último término)
            OutlinedTextField(people, { people = it }, label = { Text("Personas (separadas por comas)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            run {
                val tokens = people.text.split(",").map { it.trim() }
                val current = tokens.lastOrNull().orEmpty()
                val visible = peopleSugg
                    .filter { it.isNotBlank() }
                    .filter { current.isEmpty() || it.contains(current, ignoreCase = true) }
                    .take(6)
                if (visible.isNotEmpty()) {
                    Text("Sugerencias de personas", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        visible.forEach { s ->
                            AssistChip(
                                onClick = {
                                    val prefix = tokens.dropLast(1).filter { it.isNotBlank() }
                                    val newText = (prefix + s).joinToString(", ")
                                    people = TextFieldValue(newText)
                                },
                                label = { Text(s) }
                            )
                        }
                    }
                }
            }

            // Sensaciones corporales + sugerencias (máx 6, autocompleta último término)
            OutlinedTextField(notes, { notes = it }, label = { Text("Sensaciones corporales (separadas por comas)") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            run {
                val tokens = notes.text.split(",").map { it.trim() }
                val current = tokens.lastOrNull().orEmpty()
                val visible = sensationsSugg
                    .filter { it.isNotBlank() }
                    .filter { current.isEmpty() || it.contains(current, ignoreCase = true) }
                    .take(6)
                if (visible.isNotEmpty()) {
                    Text("Sugerencias de sensaciones", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        visible.forEach { s ->
                            AssistChip(
                                onClick = {
                                    val prefix = tokens.dropLast(1).filter { it.isNotBlank() }
                                    val newText = (prefix + s).joinToString(", ")
                                    notes = TextFieldValue(newText)
                                },
                                label = { Text(s) }
                            )
                        }
                    }
                }
            }

            // Campos descriptivos
            OutlinedTextField(situationFacts, { situationFacts = it }, label = { Text("Situación y hechos") }, modifier = Modifier.fillMaxWidth(), maxLines = 6)
            OutlinedTextField(thoughts, { thoughts = it }, label = { Text("Pensamientos") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            OutlinedTextField(actions, { actions = it }, label = { Text("Qué hiciste") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)

            TrainingStrip(modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    val items: List<EmotionItem> = selected.map { (key, level) ->
                        val def = defaultEmotionPalette.firstOrNull { it.key == key }
                        EmotionItem(key, def?.label ?: key, level.coerceIn(0, 5))
                    }
                    val entry = EmotionEntry(
                        emotions = items,
                        generalIntensity = generalIntensity.coerceIn(1, 5),
                        place = place.text,
                        people = people.text,
                        thoughts = thoughts.text,
                        actions = actions.text,
                        notes = notes.text,
                        situationFacts = situationFacts.text,
                        topic = topic.text
                    )
                    runCatching {
                        saveEmotionEntryFileWithMoment(context, entry, momentType = "reflexion", captureMode = "completa")
                    }.onSuccess {
                        val p = place.text.trim()
                        if (p.isNotEmpty()) addPlaceSuggestion(context, p)
                        val ppl = parseCommaList(people.text)
                        if (ppl.isNotEmpty()) addPeopleSuggestions(context, ppl)
                        val sens = parseCommaList(notes.text)
                        if (sens.isNotEmpty()) addSensationsSuggestions(context, sens)
                        val t = topic.text.trim()
                        if (t.isNotEmpty()) addTopicSuggestion(context, t)

                        Toast.makeText(context, "Emoción guardada en gestor.", Toast.LENGTH_LONG).show()
                        selected.clear()
                        place = TextFieldValue(""); people = TextFieldValue("")
                        topic = TextFieldValue("")
                        thoughts = TextFieldValue(""); actions = TextFieldValue("")
                        notes = TextFieldValue(""); situationFacts = TextFieldValue("")
                        generalIntensity = 3
                    }.onFailure {
                        Toast.makeText(context, "Error al guardar: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar en gestor") }
        }

        // Overlay de chips con selección visible cuando se hace scroll
        val thresholdPx = with(density) { 320.dp.toPx() }
        val showFloating by remember { derivedStateOf { scroll.value > thresholdPx && selected.isNotEmpty() } }
        AnimatedVisibility(
            visible = showFloating,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .zIndex(1f)
        ) {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .widthIn(max = 520.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selected.keys.forEach { key ->
                            val def = defaultEmotionPalette.firstOrNull { it.key == key }
                            val level = selected[key] ?: 0
                            AssistChip(
                                onClick = { /* noop */ },
                                label = { Text("${def?.label ?: key} • $level") }
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== Diálogo de intensidad =====
    val key = editingKey
    if (key != null) {
        val def = defaultEmotionPalette.firstOrNull { it.key == key }
        var temp by remember(key) { mutableStateOf((selected[key] ?: 0).coerceIn(0, 5)) } // 0..5

        // Preferencia global persistente
        var showDefs by remember { mutableStateOf(getShowDefsOnSelect(context)) }

        // Textos de info (se renderizan solo si showDefs == true)
        val userDef = remember(key) { getUserEmotionDefinition(context, key) }
        val adaptDef = remember(key) { getAdaptativeDefinition(key) }
        val critDef  = remember(key) { getCriticalDefinition(key) }
        val keyPhrases = remember(key) { getKeyPhrases(key).take(3) }
        val sensBody = remember(key) { getBodySensations(context, key).take(3) }

        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text("Intensidad: ${def?.label ?: key}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Selector 0..5
                    NumberPickerRow(selected = temp, onSelect = { temp = it.coerceIn(0, 5) })

                    // Info con MISMO formato/tamaño
                    val style = MaterialTheme.typography.bodyMedium

                    ElevatedCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    val newValue = !showDefs
                                    showDefs = newValue
                                    setShowDefsOnSelect(context, newValue)
                                }) {
                                    Text(if (showDefs) "Ocultar info" else "Mostrar info")
                                }
                            }

                            if (showDefs) {
                                val mainText = userDef?.takeIf { !it.isNullOrBlank() } ?: adaptDef
                                Text(mainText, style = style)
                                Text(critDef, style = style)

                                if (keyPhrases.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        keyPhrases.forEach { phrase ->
                                            Text("• $phrase", style = style)
                                        }
                                    }
                                }

                                if (sensBody.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        sensBody.forEach { s -> Text("• $s", style = style) }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selected.remove(key); editingKey = null }) { Text("Deseleccionar") }
            },
            confirmButton = {
                TextButton(onClick = {
                    selected[key] = temp // 0..5
                    editingKey = null
                }) { Text("Aceptar") }
            }
        )
    }
}

/* -------------------- Utils -------------------- */
private fun parseCommaList(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

/* ---- UI auxiliares ---- */
@Composable
private fun EmotionBarButton(
    label: String,
    baseColor: Color,
    width: Dp,
    height: Dp,
    intensityLevel: Int?,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val level = intensityLevel?.coerceIn(0, 5) ?: 0 // 0..5
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
                Box(Modifier.weight(level.toFloat()).fillMaxHeight().background(baseColor))
                val rem = 5 - level
                if (rem > 0) Spacer(Modifier.weight(rem.toFloat()).fillMaxHeight())
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
                textSize = (textSize.value - 1).sp
            }
        }
    )
}

@Composable
private fun NumberPickerRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (0..5).forEach { n -> // 0..5
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
