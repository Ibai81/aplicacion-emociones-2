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
    val secondaryKeys = remember {
        listOf("vergüenza","culpa","orgullo","desprecio","ansiedad","frustración","alivio","gratitud","amor","envidia")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Secundarias", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        secondaryKeys.forEach { key ->
            SecondaryInfoCard(key = key, title = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
        }
    }
}

@Composable
private fun SecondaryInfoCard(key: String, title: String) {
    val unifiedDef = remember(key) { getUnifiedDefinition(key) }
    val keyPhrases = remember(key) { getKeyPhrases(key).take(3) }
    val defaultSens = remember(key) { getDefaultBodySensations(key) }
    val rels = remember(title) { relatedPrimariesForSecondary(title) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (!unifiedDef.isNullOrBlank()) {
                Text(unifiedDef, style = MaterialTheme.typography.bodyMedium)
            }

            if (keyPhrases.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    keyPhrases.forEach { p -> Text("• $p", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            if (defaultSens.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    defaultSens.forEach { s -> Text("• $s", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            if (rels.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Deriva de primarias", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rels.forEach { r ->
                        Text("• ${r.to}", style = MaterialTheme.typography.bodyMedium)
                        r.examples.take(2).forEach { ex ->
                            Text("   – Pensamiento: ${ex.thought}", style = MaterialTheme.typography.bodySmall)
                            Text("     Cuerpo: ${ex.body} · Contexto: ${ex.context}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
