package com.example.emotionapp.ui.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emotionapp.defaultEmotionPalette

/* ====== Banco “rápido” (1 emoción) ====== */
private data class Q(val label: String, val clue: String)

private val quickClues = listOf(
    Q("Miedo", "Alarma/amenaza; aceleración, tensión, sudor."),
    Q("Ira", "Poner límites; calor y tensión en el cuerpo."),
    Q("Tristeza", "Respuesta a pérdida; pesadez, opresión."),
    Q("Alegría", "Bienestar y expansión; ligereza, energía."),
    Q("Asco", "Rechazo; náusea, retirada."),
    Q("Sorpresa", "Orientación rápida; sobresalto repentino."),
    Q("Culpa", "Autojuicio por dañar a alguien o una norma."),
    Q("Vergüenza", "Miedo a juicio ajeno; ganas de ocultarse."),
    Q("Interés", "Curiosidad/atracción por algo novedoso o útil."),
    Q("Sufrimiento", "Dolor emocional sostenido; cansancio."),
    // extra
    Q("Ansiedad", "Alerta anticipatoria; inquietud, mariposas, sudor."),
    Q("Calma", "Seguridad y suficiencia; respiración profunda, relajación.")
)

/* ====== Banco “ambiguo” (0–3 emociones correctas, con sensaciones) ====== */
private data class AmbQ(
    val expresion: String,
    val sensaciones: List<String>,
    val correctas: Set<String>
)

private val EXTRA_EMOS = listOf(
    "Ansiedad", "Calma", "Frustración", "Envidia", "Orgullo",
    "Ternura", "Alivio", "Aburrimiento", "Esperanza", "Confusión"
)

private val ambiguous = listOf(
    AmbQ(
        expresion = "“Me han escrito y no sé si contestar ahora o más tarde.”",
        sensaciones = listOf("nudo en el estómago", "inquietud", "respiración superficial"),
        correctas = setOf("Ansiedad", "Miedo")
    ),
    AmbQ(
        expresion = "“Me han interrumpido tres veces seguidas en la reunión.”",
        sensaciones = listOf("calor en la cara", "mandíbula tensa", "impulso a hablar alto"),
        correctas = setOf("Ira", "Frustración")
    ),
    AmbQ(
        expresion = "“Estoy mirando mensajes viejos sin ganas de responder.”",
        sensaciones = listOf("pesadez corporal", "baja energía"),
        correctas = setOf("Aburrimiento", "Tristeza")
    ),
    AmbQ(
        expresion = "“Me he cruzado con un perro grande en un portal estrecho.”",
        sensaciones = listOf("aceleración cardíaca", "sudoración", "tensión corporal"),
        correctas = setOf("Miedo", "Ansiedad", "Sorpresa")
    ),
    AmbQ(
        expresion = "“Se ha cancelado por fin la cita que me preocupaba.”",
        sensaciones = listOf("exhalación larga", "soltura en el cuerpo"),
        correctas = setOf("Alivio", "Alegría")
    ),
    AmbQ(
        expresion = "“Estoy sentado en el banco del parque, respiración amplia, sin prisa.”",
        sensaciones = listOf("relajación tónica", "mirada suave"),
        correctas = setOf("Calma", "Interés")
    ),
    AmbQ(
        expresion = "“He visto una acción injusta hacia un compañero.”",
        sensaciones = listOf("calor en el pecho", "impulso a intervenir"),
        correctas = setOf("Ira")
    ),
    AmbQ(
        expresion = "“Tengo dolor de cabeza por hambre, nada más.”",
        sensaciones = listOf("molestia física", "sin carga emocional sostenida"),
        correctas = emptySet()
    ),
    AmbQ(
        expresion = "“He recibido una buena noticia inesperada.”",
        sensaciones = listOf("sobresalto", "inspiración corta", "energía repentina"),
        correctas = setOf("Sorpresa", "Alegría")
    ),
    AmbQ(
        expresion = "“Veía a alguien con lo que yo quiero desde hace tiempo.”",
        sensaciones = listOf("rumiación", "contracción suave", "mirada fija"),
        correctas = setOf("Envidia", "Tristeza")
    )
)

/* ====== Componente público ====== */
@Composable
fun TrainingStrip(modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(0) } // 0: Rápido (1), 1: Ambiguo (0–3)

    OutlinedCard(modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Entrenamiento de identificación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Rápido (1)") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Ambiguo (0–3)") })
            }

            when (tab) {
                0 -> QuickRound()
                else -> AmbiguousRound()
            }
        }
    }
}

/* ====== Modo Rápido (1 emoción) ====== */
@Composable
private fun QuickRound() {
    var q by remember { mutableStateOf(quickClues.random()) }
    var picked by remember { mutableStateOf<String?>(null) }

    val pool = remember(q) {
        val base = defaultEmotionPalette.map { it.label }.toMutableList()
        EXTRA_EMOS.forEach { if (!base.contains(it)) base.add(it) }
        val distractors = base.filter { it != q.label }.shuffled().take(2)
        (listOf(q.label) + distractors).shuffled()
    }

    val correct = picked != null && picked == q.label

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(q.clue, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pool.forEach { opt ->
                FilterChip(
                    selected = picked == opt,
                    onClick = { picked = opt },
                    label = { Text(opt) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (picked != null) {
                val txt = if (correct) "✅ ¡Bien! Era ${q.label}." else "❌ Era ${q.label}."
                Text(txt, color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { q = quickClues.random(); picked = null }) { Text("Siguiente") }
        }
    }
}

/* ====== Modo Ambiguo (0–3 emociones correctas o ninguna) ====== */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmbiguousRound() {
    var q by remember { mutableStateOf(ambiguous.random()) }
    var seleccion by remember { mutableStateOf(mutableSetOf<String>()) }
    var marcNinguna by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    // Opciones = paleta base + “extras”
    val opciones = remember {
        val base = defaultEmotionPalette.map { it.label }.toMutableList()
        EXTRA_EMOS.forEach { if (!base.contains(it)) base.add(it) }
        base.sorted()
    }

    fun comprobar() {
        val sel = if (marcNinguna) emptySet<String>() else seleccion.toSet()
        val ok = sel == q.correctas
        feedback = if (ok) {
            "✅ Correcto."
        } else {
            val esperado = if (q.correctas.isEmpty()) "Ninguna" else q.correctas.joinToString(", ")
            val elegido = if (marcNinguna) "Ninguna" else sel.joinToString(", ").ifBlank { "—" }
            "❌ Esperado: $esperado · Elegido: $elegido"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Expresión ambigua:", fontWeight = FontWeight.SemiBold)
        Text("“${q.expresion}”")
        if (q.sensaciones.isNotEmpty()) {
            Text("Sensaciones corporales: " + q.sensaciones.joinToString(", "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Multiselección con FlowRow (requiere opt-in)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            opciones.forEach { emo ->
                val selected = (!marcNinguna) && seleccion.contains(emo)
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (marcNinguna) marcNinguna = false
                        val set = seleccion.toMutableSet()
                        if (selected) set.remove(emo) else if (set.size < 3) set.add(emo) // límite 3
                        seleccion = set
                    },
                    label = { Text(emo) }
                )
            }
            // “Ninguna”
            AssistChip(
                onClick = {
                    marcNinguna = !marcNinguna
                    if (marcNinguna) seleccion = mutableSetOf()
                },
                label = { Text(if (marcNinguna) "Ninguna (✔)" else "Ninguna") }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { comprobar() }) { Text("Comprobar") }
            OutlinedButton(onClick = {
                q = ambiguous.random()
                seleccion = mutableSetOf()
                marcNinguna = false
                feedback = null
            }) { Text("Siguiente") }
            Spacer(Modifier.weight(1f))
        }

        if (feedback != null) {
            Text(feedback!!, color = if (feedback!!.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (q.correctas.isNotEmpty()) {
                Text(
                    "Pista: decide por el contexto + sensaciones. Si hay amenaza → Miedo/Ansiedad; si hay bloqueo y empuje → Ira/Frustración; si hay expansión suave → Alegría/Orgullo/Ternura; si no hay carga emocional, puede ser “Ninguna”.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
