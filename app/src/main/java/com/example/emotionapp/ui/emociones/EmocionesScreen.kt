package com.example.emotionapp.ui.emociones

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.defaultEmotionPalette
import com.example.emotionapp.data.EmotionEntry
import com.example.emotionapp.data.EmotionItem
import com.example.emotionapp.data.getBodySensations
import com.example.emotionapp.data.getKeyPhrases
import com.example.emotionapp.data.getShowDefsOnSelect
import com.example.emotionapp.data.getUnifiedDefinition
import com.example.emotionapp.data.getUserEmotionDefinition
import com.example.emotionapp.data.relatedSecondariesForPrimary
import com.example.emotionapp.data.saveEmotionEntryFileWithMoment
import com.example.emotionapp.data.setShowDefsOnSelect
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.example.emotionapp.ui.common.HintTextField

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EmotionScreen(getEmotionColor: (String) -> Color) {
    val context = LocalContext.current

    // PRIMARIAS seleccionadas (clave -> 0..5)
    val selected: SnapshotStateMap<String, Int> = remember { mutableStateMapOf<String, Int>() }

    // SECUNDARIAS seleccionadas (solo desde “Sugeridas”)
    var selectedSecondaries by remember { mutableStateOf(listOf<String>()) }
    fun toggleSecondary(key: String) {
        selectedSecondaries = if (key in selectedSecondaries) selectedSecondaries - key else selectedSecondaries + key
    }

    // Campos
    var place by remember { mutableStateOf("") }
    var people by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var thoughts by remember { mutableStateOf("") }
    var actions by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var situationFacts by remember { mutableStateOf("") }
    var generalIntensity by remember { mutableStateOf(3) }

    // Diálogo de intensidad
    var editingKey by remember { mutableStateOf<String?>(null) }

    // Lista de primarias (sin culpa/vergüenza)
    val primaryList = remember {
        defaultEmotionPalette.filterNot {
            it.key.equals("culpa", true) || it.key.equals("vergüenza", true) || it.key.equals("verguenza", true)
        }
    }

    // Sugeridas a partir de PRIMARIAS elegidas (EmotionRelations)
    val recommendedSecondaries = remember(selected.toMap()) {
        selected.keys
            .map { k -> primaryList.firstOrNull { it.key == k }?.label ?: k }
            .flatMap { label -> relatedSecondariesForPrimary(label).map { it.to } }
            .distinct()
    }

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Registro completo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        // ---------- PRIMARIAS ----------
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 420.dp)
        ) {
            items(primaryList, key = { it.key }) { emo ->
                val level = selected[emo.key]
                EmotionTileButton(
                    label = emo.label,
                    baseColor = getEmotionColor(emo.key),
                    width = 120.dp,
                    height = 48.dp,
                    intensityLevel = level,
                    onTap = {
                        if (level == null) selected[emo.key] = 0
                        editingKey = emo.key
                    },
                    onLongPress = { if (level != null) selected.remove(emo.key) }
                )
            }
        }

        // Seleccionadas (resumen)
        if (selected.isNotEmpty()) {
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleccionadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selected.forEach { (k, v) ->
                            AssistChip(onClick = { editingKey = k }, label = { Text("${labelForKey(primaryList, k)} · $v/5") })
                        }
                    }
                }
            }
        }

        // ---------- SECUNDARIAS SUGERIDAS (únicas que mostramos) ----------
        if (recommendedSecondaries.isNotEmpty()) {
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sugeridas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recommendedSecondaries.forEach { sec ->
                            val picked = selectedSecondaries.any { it.equals(sec, ignoreCase = true) }
                            FilterChip(
                                selected = picked,
                                onClick = { toggleSecondary(sec) },
                                label = { Text(cap(sec)) }
                            )
                        }
                    }
                }
            }
        }

        // ---------- Detalle/relaciones de la primaria en edición ----------
        val currentDef = remember(editingKey) { primaryList.firstOrNull { it.key == editingKey } }
        if (editingKey != null && currentDef != null) {
            RelationBoxForPrimary(def = currentDef)
        }

        // ---------- Campos (con HintTextField) ----------
        HintTextField(
            value = place, onValueChange = { place = it },
            label = "Lugar", hint = "Ej.: bar, casa, trabajo…",
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        HintTextField(
            value = people, onValueChange = { people = it },
            label = "Personas (coma separadas)", hint = "Nombres separados por comas",
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        HintTextField(
            value = topic, onValueChange = { topic = it },
            label = "Tema", hint = "Ej.: trabajo, familia, proyecto…",
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        HintTextField(
            value = thoughts, onValueChange = { thoughts = it },
            label = "Pensamientos", hint = "Frases que te dijiste o te pasaron por la cabeza",
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )

        HintTextField(
            value = actions, onValueChange = { actions = it },
            label = "Acciones/Conducta", hint = "Qué hiciste o evitaste",
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )

        HintTextField(
            value = notes, onValueChange = { notes = it },
            label = "Sensaciones corporales / Notas (coma separadas)", hint = "Ej.: nudo en el estómago, manos frías…",
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )

        HintTextField(
            value = situationFacts, onValueChange = { situationFacts = it },
            label = "Situación y hechos", hint = "Qué pasó, sin interpretaciones",
            maxLines = 6, modifier = Modifier.fillMaxWidth()
        )

        // Intensidad general
        Text("Intensidad general", style = MaterialTheme.typography.titleMedium)
        NumberPickerRow(selected = generalIntensity) { generalIntensity = it }

        // ---------- Guardar ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (selected.isEmpty()) {
                        Toast.makeText(context, "Añade al menos una emoción primaria.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val items = selected.mapNotNull { (k, v) ->
                        val def = primaryList.firstOrNull { it.key == k } ?: return@mapNotNull null
                        EmotionItem(def.key, def.label, v.coerceIn(0, 5))
                    }
                    val entry = EmotionEntry(
                        emotions = items,
                        generalIntensity = generalIntensity.coerceIn(1, 5),
                        place = place,
                        people = people,
                        thoughts = thoughts,
                        actions = actions,
                        notes = notes,
                        situationFacts = situationFacts,
                        topic = topic
                    )
                    val file = saveEmotionEntryFileWithMoment(
                        context = context,
                        entry = entry,
                        momentType = "instantanea",
                        captureMode = "completa"
                    )
                    // Añadir primarias/secundarias al JSON
                    runCatching {
                        val json = JsonParser.parseString(file.readText()).asJsonObject
                        json.add("primaryEmotions", Gson().toJsonTree(entry.emotions))
                        json.add("secondaryEmotions", Gson().toJsonTree(selectedSecondaries))
                        file.writeText(Gson().toJson(json))
                    }
                    Toast.makeText(context, "Entrada guardada", Toast.LENGTH_SHORT).show()
                    // Reset
                    selected.clear()
                    selectedSecondaries = emptyList()
                    place = ""; people = ""; topic = ""
                    thoughts = ""; actions = ""; notes = ""; situationFacts = ""
                    generalIntensity = 3
                },
                modifier = Modifier.weight(1f)
            ) { Text("Guardar") }

            OutlinedButton(
                onClick = {
                    selected.clear()
                    selectedSecondaries = emptyList()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Limpiar") }
        }
    }

    // Diálogo de intensidad (con SCROLL)
    val def = remember(editingKey) { primaryList.firstOrNull { it.key == editingKey } }
    if (editingKey != null && def != null) {
        IntensityDialog(
            key = def.key,
            defLabel = def.label,
            current = selected[def.key] ?: 0,
            onDismiss = { editingKey = null },
            onConfirm = { newValue ->
                selected[def.key] = newValue.coerceIn(0, 5)
                editingKey = null
            }
        )
    }
}

/* --------------------- Diálogo de intensidad (SCROLL) --------------------- */
@Composable
private fun IntensityDialog(
    key: String,
    defLabel: String,
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val context = LocalContext.current

    var temp by remember(key) { mutableStateOf(current.coerceIn(0, 5)) }
    var showDefs by remember { mutableStateOf(getShowDefsOnSelect(context)) }

    val userDef = remember(key) { getUserEmotionDefinition(context, key) }
    val unifiedDef = remember(key) { getUnifiedDefinition(key) }
    val keyPhrases = remember(key) { getKeyPhrases(key).take(3) }
    val sensBody = remember(key) { getBodySensations(context, key).take(3) }

    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Intensidad: $defLabel") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(scroll)
            ) {
                NumberPickerRow(selected = temp, onSelect = { temp = it.coerceIn(0, 5) })

                ElevatedCard {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                val newValue = !showDefs
                                showDefs = newValue
                                setShowDefsOnSelect(context, newValue)
                            }) { Text(if (showDefs) "Ocultar info" else "Mostrar info") }
                        }
                        AnimatedVisibility(visible = showDefs) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val main = userDef?.takeIf { it.isNotBlank() } ?: unifiedDef
                                if (!main.isNullOrBlank()) Text(main, style = MaterialTheme.typography.bodyMedium)
                                if (keyPhrases.isNotEmpty()) {
                                    Column { keyPhrases.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) } }
                                }
                                if (sensBody.isNotEmpty()) {
                                    Column { sensBody.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) } }
                                }
                                val rels = relatedSecondariesForPrimary(defLabel)
                                if (rels.isNotEmpty()) {
                                    Spacer(Modifier.size(4.dp))
                                    Text("Relaciones típicas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Column {
                                        rels.forEach { r ->
                                            Text("• $defLabel → ${r.to}", style = MaterialTheme.typography.bodyMedium)
                                            r.examples.take(2).forEach { ex ->
                                                Text("   – Pensamiento: ${ex.thought}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("     Cuerpo: ${ex.body} · Contexto: ${ex.context}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(temp) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/* --------------------- Botón primaria --------------------- */
@Composable
private fun EmotionTileButton(
    label: String,
    baseColor: Color,
    width: Dp,
    height: Dp,
    intensityLevel: Int?,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val bg = if (intensityLevel == null) baseColor.copy(alpha = 0.25f)
    else baseColor.copy(alpha = 0.25f + 0.15f * intensityLevel.coerceIn(0, 5))
    val fg = if ((0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue) > 0.5f) Color.Black else Color.White

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .size(width = width, height = height)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() }, onTap = { onTap() })
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = fg, fontWeight = FontWeight.Medium)
            if (intensityLevel != null) Text("${intensityLevel}/5", color = fg)
        }
    }
}

/* --------------------- Picker 1..5 --------------------- */
@Composable
private fun NumberPickerRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { n ->
            val sel = selected == n
            val bg = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$n",
                    color = if (sel) fg else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onSelect(n) }) }
                )
            }
        }
    }
}

/* --------------------- Relaciones (primaria → secundarias) --------------------- */
@Composable
private fun RelationBoxForPrimary(def: EmotionDef) {
    val rels = remember(def.label) { relatedSecondariesForPrimary(def.label) }
    if (rels.isEmpty()) return
    ElevatedCard {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Relaciones típicas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            rels.forEach { r ->
                Text("• ${def.label} → ${r.to}", style = MaterialTheme.typography.bodyMedium)
                r.examples.take(2).forEach { ex ->
                    Text("   – Pensamiento: ${ex.thought}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("     Cuerpo: ${ex.body} · Contexto: ${ex.context}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/* --------------------- Utils --------------------- */
private fun labelForKey(list: List<EmotionDef>, key: String): String =
    list.firstOrNull { it.key == key }?.label ?: key

private fun cap(s: String): String =
    if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)
