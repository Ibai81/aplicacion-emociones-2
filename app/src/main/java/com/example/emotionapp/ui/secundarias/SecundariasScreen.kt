package com.example.emotionapp.ui.secundarias

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.data.getDefaultBodySensations
import com.example.emotionapp.data.getKeyPhrases
import com.example.emotionapp.data.getUnifiedDefinition
import com.example.emotionapp.data.relatedPrimariesForSecondary

@Composable
fun SecundariasScreen() {
    val scroll = rememberScrollState()

    // ===== Grupos de secundarias =====
    val autoconscientes = listOf("vergüenza", "culpa", "orgullo")
    val sociales       = listOf("amor", "gratitud", "envidia", "desprecio")
    val anticipatorias = listOf("ansiedad")
    val alivioGroup    = listOf("alivio")
    val bloqueoMetas   = listOf("frustración")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Secundarias", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        GroupSection("Autoconscientes", autoconscientes)
        GroupSection("Sociales / relacionales", sociales)
        GroupSection("Anticipatorias (futuro incierto)", anticipatorias)
        GroupSection("Alivio / recuperación", alivioGroup)
        GroupSection("Bloqueo de metas", bloqueoMetas)
    }
}

@Composable
private fun GroupSection(title: String, keys: List<String>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            keys.forEach { key ->
                SecondaryInfoCard(
                    key = key,
                    title = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                )
            }
        }
    }
}

@Composable
private fun SecondaryInfoCard(key: String, title: String) {
    val unifiedDef = remember(key) { getUnifiedDefinition(key) }
    val keyPhrases = remember(key) { getKeyPhrases(key).take(3) }
    val defaultSens = remember(key) { getDefaultBodySensations(key) }
    val rels = remember(title) { relatedPrimariesForSecondary(title) }

    // Descripciones específicas mejoradas para las solicitadas
    val improved = remember {
        mapOf(
            "orgullo" to "Satisfacción por cumplir estándares o valores importantes ante ti y/o los demás. Suele aparecer tras un logro personal y aporta energía para sostener hábitos. Puede ser sano (reconocer el esfuerzo) o inflado (compararse para sentirse por encima).",
            "ansiedad" to "Activación anticipatoria cuando hay incertidumbre o riesgo percibido. Se mezcla con miedo y, a veces, con sorpresa por cambios imprevistos o con vergüenza si temes que se note.",
            "frustración" to "Tensión al ver bloqueados tus objetivos. Combina ira (obstáculo externo) y tristeza (pérdida de lo esperado). A menudo viene de imprevistos que rompen el plan.",
            "alivio" to "Descarga tras disiparse una amenaza o una espera angustiosa. El cuerpo suelta la tensión y puede aparecer una alegría suave por haber pasado el bache.",
            "gratitud" to "Apreciación por un beneficio recibido (material o emocional). Suele tener alegría y, si fue inesperado, un toque de sorpresa. Fortalece los vínculos.",
            "amor" to "Afecto que impulsa el cuidado y la cercanía. Sentirse a gusto con el vínculo, con alegría calma y apertura corporal. Facilita cooperación y protección mutua.",
            "envidia" to "Malestar por la comparación desfavorable con otra persona. Puede mezclar tristeza por carencia e ira ante una posible injusticia; si se cronifica, erosiona vínculos."
        )
    }[key.lowercase()]

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            // 1) Descripción mejorada (o unificada si existe y no hay mejorada)
            when {
                !improved.isNullOrBlank() -> Text(improved, style = MaterialTheme.typography.bodyMedium)
                !unifiedDef.isNullOrBlank() -> Text(unifiedDef, style = MaterialTheme.typography.bodyMedium)
            }

            // 2) Frases clave
            if (keyPhrases.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    keyPhrases.forEach { p -> Text("• $p", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            // 3) Sensaciones corporales típicas
            if (defaultSens.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    defaultSens.forEach { s -> Text("• $s", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            // 4) Raíces primarias con ejemplos (ya tiramos de EmotionRelations)
            if (rels.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Deriva de primarias", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rels.forEach { r ->
                        Text("• ${r.to}", style = MaterialTheme.typography.bodyMedium)
                        r.examples.take(2).forEach { ex ->
                            Text("   – Pensamiento: ${ex.thought}", style = MaterialTheme.typography.bodySmall)
                            Text("     Cuerpo: ${ex.body} · Contexto: ${ex.context}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
