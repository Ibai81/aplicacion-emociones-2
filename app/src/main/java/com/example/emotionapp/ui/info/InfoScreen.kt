package com.example.emotionapp.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.defaultEmotionPalette

@Composable
fun InfoScreen() {
    val scroll = rememberScrollState()

    // Tabla 1: Emoción + Sensaciones corporales comunes
    val bodyByEmotion = mapOf(
        "Miedo" to listOf("Tensión corporal", "Aceleración cardíaca", "Sudoración", "Nudo en el estómago"),
        "Ira" to listOf("Calor en pecho/cara", "Mandíbula tensa", "Impulso a empujar/hablar fuerte"),
        "Vergüenza" to listOf("Rubor", "Bajar la mirada", "Encogimiento postural"),
        "Desprecio" to listOf("Ceja/comisura levantada", "Cuerpo se aparta"),
        "Asco" to listOf("Náusea", "Retirada de cabeza", "Arrugar la nariz"),
        "Culpa" to listOf("Opresión en pecho", "Nudo de estómago"),
        "Sufrimiento" to listOf("Baja energía", "Opresión/lagrimeo"),
        "Interés" to listOf("Relajación tónica", "Mirada enfocada"),
        "Sorpresa" to listOf("Sobresalto", "Aumento del tono", "Inspiración corta"),
        "Alegría" to listOf("Ligereza", "Expansión torácica", "Sonrisa")
    )

    // Tabla 2: Emoción + Pensamientos comunes
    val thoughtsByEmotion = mapOf(
        "Miedo" to listOf("“Puede pasar algo malo”", "“No estoy a salvo”"),
        "Ira" to listOf("“Esto no es justo”", "“Tengo que poner límites”"),
        "Vergüenza" to listOf("“Van a juzgarme”", "“Quiero desaparecer”"),
        "Desprecio" to listOf("“Eso está por debajo de lo aceptable”"),
        "Asco" to listOf("“Qué repugnante”"),
        "Culpa" to listOf("“No debí hacerlo”", "“Necesito reparar”"),
        "Sufrimiento" to listOf("“Nada mejora”", "“Estoy agotado/a”"),
        "Interés" to listOf("“Quiero saber más”"),
        "Sorpresa" to listOf("“¡No me lo esperaba!”"),
        "Alegría" to listOf("“Qué bien”", "“Gracias”")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Guía rápida", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        EmotionBlock(
            title = "Sensaciones corporales por emoción",
            data = bodyByEmotion
        )

        EmotionBlock(
            title = "Pensamientos frecuentes por emoción",
            data = thoughtsByEmotion
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Recuerda: las etiquetas ayudan a reconocer patrones, no a encasillarte. Ajusta la app a tu lenguaje.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmotionBlock(
    title: String,
    data: Map<String, List<String>>
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            // Ordenamos según tu paleta para que coincidan los nombres
            val order = defaultEmotionPalette.map { it.label }
            data
                .toList()
                .sortedBy { pair -> order.indexOf(pair.first).let { if (it == -1) Int.MAX_VALUE else it } }
                .forEach { (emotion, items) ->
                    Text("• $emotion", fontWeight = FontWeight.SemiBold)
                    items.forEach { it2 ->
                        Text("   - $it2", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
        }
    }
}
