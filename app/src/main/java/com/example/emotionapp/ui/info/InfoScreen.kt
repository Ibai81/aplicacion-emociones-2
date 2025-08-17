package com.example.emotionapp.ui.info

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.defaultEmotionPalette
import com.example.emotionapp.data.*
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoScreen() {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Información emocional", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        defaultEmotionPalette.forEach { def: EmotionDef ->
            EmotionInfoCard(
                key = def.key,
                title = def.label,
                onResetAll = {
                    setUserEmotionDefinition(context, def.key, null)
                    setUserEmotionSensations(context, def.key, null)
                    Toast.makeText(context, "Restablecido: ${def.label}", Toast.LENGTH_SHORT).show()
                },
                onEditSaved = { newText, newSensList ->
                    setUserEmotionDefinition(context, def.key, newText)
                    setUserEmotionSensations(context, def.key, newSensList)
                    Toast.makeText(context, "Actualizado: ${def.label}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun EmotionInfoCard(
    key: String,
    title: String,
    onResetAll: () -> Unit,
    onEditSaved: (String, List<String>?) -> Unit
) {
    val context = LocalContext.current

    var userDef by remember(key) { mutableStateOf(getUserEmotionDefinition(context, key).orEmpty()) }
    val baseDef = remember(key) { getAdaptativeDefinition(key) }
    val critDef = remember(key) { getCriticalDefinition(key) }
    var userSens by remember(key) { mutableStateOf(getUserEmotionSensations(context, key) ?: emptyList()) }
    val defaultSens = remember(key) { getDefaultBodySensations(key) }
    val keyPhrases = remember(key) { getKeyPhrases(key).take(3) }

    var showEditor by remember(key) { mutableStateOf(false) }
    var editorText by remember(key) { mutableStateOf(userDef.ifBlank { baseDef }) }
    var sensEditorText by remember(key) { mutableStateOf((if (userSens.isNotEmpty()) userSens else defaultSens).joinToString(", ")) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            // Misma tipografía/tamaño para los 4 apartados
            val style = MaterialTheme.typography.bodyMedium

            if (showEditor) {
                OutlinedTextField(
                    value = editorText,
                    onValueChange = { editorText = it },
                    label = { Text("Editar definición") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sensEditorText,
                    onValueChange = { sensEditorText = it },
                    label = { Text("Sensaciones (separadas por comas)") },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(userDef.ifBlank { baseDef }, style = style)
                Text(critDef, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (keyPhrases.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        keyPhrases.forEach { phrase -> Text("• $phrase", style = style) }
                    }
                }
                // 4º apartado: Sensaciones corporales (3)
                val sens = if (userSens.isNotEmpty()) userSens else defaultSens
                if (sens.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        sens.forEach { s -> Text("• $s", style = style) }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showEditor) {
                    TextButton(onClick = {
                        val t = editorText.trim()
                        val parsedSens = sensEditorText.split(",").map { it.trim() }
                            .filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(3)
                        onEditSaved(if (t.isBlank()) "" else t, if (parsedSens.isEmpty()) null else parsedSens)
                        userDef = if (t.isBlank()) "" else t
                        userSens = parsedSens
                        showEditor = false
                    }) { Text("Guardar") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { showEditor = false }) { Text("Cancelar") }
                } else {
                    TextButton(onClick = {
                        editorText = (userDef.ifBlank { baseDef })
                        sensEditorText = (if (userSens.isNotEmpty()) userSens else defaultSens).joinToString(", ")
                        showEditor = true
                    }) { Text("Editar") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        onResetAll()
                        userDef = ""
                        userSens = emptyList()
                        showEditor = false
                    }) { Text("Restablecer") }
                }
            }
        }
    }
}
