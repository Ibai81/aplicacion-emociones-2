package com.example.emotionapp.ui.configuracion

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.EmotionDef
import com.example.emotionapp.defaultEmotionPalette

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
    // Estado para abrir diálogo de color por emoción
    var editingEmotion by remember { mutableStateOf<EmotionDef?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Configuración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))

        // ----- Sección: color general de la aplicación -----
        Text("Color de la aplicación", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowColorGrid(
            colors = palette,
            selected = primaryColor,
            onPick = onPickPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Se usa como color primario (botones, resaltados…).",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // ----- Sección: colores por emoción -----
        Text("Color de las emociones", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(defaultPalette) { emo ->
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
                                        onPickForEmotion(emo.key, emo.color) // restaurar esta emoción
                                        Toast.makeText(context, "Restaurado: ${emo.label}", Toast.LENGTH_SHORT).show()
                                    },
                                    onTap = { editingEmotion = emo } // abrir diálogo
                                )
                            }
                    )
                    Text(emo.label, modifier = Modifier.weight(1f))
                    Text(
                        text = "#%08X".format(current.toArgb()),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    TextButton(onClick = { editingEmotion = emo }) {
                        Text("Cambiar")
                    }
                }
            }

            // Botón Restaurar todos
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onResetAll,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) { Text("Restaurar todos") }
                }
            }
        }
    }

    // Diálogo de paleta (estado controlado)
    val emo = editingEmotion
    if (emo != null) {
        AlertDialog(
            onDismissRequest = { editingEmotion = null },
            title = { Text("Color para: ${emo.label}") },
            text = {
                FlowColorGrid(
                    colors = palette,
                    selected = emotionColors[emo.key] ?: emo.color,
                    onPick = { c ->
                        onPickForEmotion(emo.key, c)
                        editingEmotion = null
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { editingEmotion = null }) { Text("Cerrar") }
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
        items(colors) { c ->
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
