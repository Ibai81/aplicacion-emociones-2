package com.example.emotionapp.data

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Normaliza clave/etiqueta para buscar en tablas. */
private fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase()

/** Ejemplo breve para ayudar a identificar una relación entre emociones. */
data class RelationExample(
    val thought: String,        // “pensamiento típico”
    val body: String,           // “sensación corporal”
    val context: String         // “situación frecuente”
)

/** Relación: A (origen) → B (secundaria/derivada) con ejemplos. */
data class Relation(
    val from: String,           // clave/label origen (primaria o secundaria)
    val to: String,             // clave/label destino (primaria o secundaria)
    val type: String,           // "deriva_en" | "posible_raiz" | "mezcla"
    val examples: List<RelationExample>
)

/* ======================== TABLA DE CONEXIONES ======================== */
private val RELS: List<Relation> = listOf(
    // ====== PRIMARIAS → SECUNDARIAS ======
    // MIEDO →
    Relation("Miedo", "Ansiedad", "deriva_en", listOf(
        RelationExample("¿Y si sale mal?", "Nudo en el estómago", "Antes de hablar en público"),
        RelationExample("Podría perder el control", "Respiración rápida", "Entrar en un sitio nuevo")
    )),
    Relation("Miedo", "Preocupación", "deriva_en", listOf(
        RelationExample("Le doy vueltas una y otra vez", "Tensión en cuello", "Noche anterior a un examen")
    )),
    Relation("Miedo", "Vergüenza", "deriva_en", listOf(
        RelationExample("Van a notar que tengo miedo", "Rubor facial", "Situación social")
    )),

    // TRISTEZA →
    Relation("Tristeza", "Resignación", "deriva_en", listOf(
        RelationExample("Para qué intentarlo", "Pesadez corporal", "Tras varios intentos fallidos")
    )),
    Relation("Tristeza", "Apatía", "deriva_en", listOf(
        RelationExample("No me apetece nada", "Baja energía", "Días sin reforzadores")
    )),
    Relation("Tristeza", "Desesperanza", "deriva_en", listOf(
        RelationExample("Nada va a cambiar", "Opresión en pecho", "Racha larga de estrés")
    )),

    // IRA →
    Relation("Ira", "Frustración", "deriva_en", listOf(
        RelationExample("Siempre me bloquean", "Mandíbula tensa", "Trámites, burocracia")
    )),
    Relation("Ira", "Culpa", "deriva_en", listOf(
        RelationExample("No debí hablar así", "Calor que baja", "Después de una discusión")
    )),
    Relation("Ira", "Rencor", "deriva_en", listOf(
        RelationExample("No olvido lo que hizo", "Tensión sostenida", "Conflicto antiguo")
    )),

    // ASCO →
    Relation("Asco", "Desprecio", "deriva_en", listOf(
        RelationExample("Esto es inaceptable", "Retraimiento corporal", "Norma moral vulnerada")
    )),

    // ALEGRÍA →
    Relation("Alegría", "Orgullo", "deriva_en", listOf(
        RelationExample("Lo he conseguido", "Ligereza", "Después de un logro propio")
    )),
    Relation("Alegría", "Gratitud", "deriva_en", listOf(
        RelationExample("Qué suerte tenerte", "Calidez", "Apoyo recibido")
    )),
    Relation("Alegría", "Amor", "deriva_en", listOf(
        RelationExample("Quiero cuidar este vínculo", "Apertura en el pecho", "Momentos de conexión")
    )),

    // SORPRESA →
    Relation("Sorpresa", "Ansiedad", "deriva_en", listOf(
        RelationExample("¿Y ahora qué hago?", "Sobresalto y tensión", "Cambios bruscos e inciertos")
    )),

    // ====== SECUNDARIAS → POSIBLES RAÍCES (PRIMARIAS) ======
    Relation("Ansiedad", "Miedo", "posible_raiz", listOf(
        RelationExample("Algo puede ir mal", "Respiración rápida", "Antes de exposición"),
        RelationExample("Perder el control", "Tensión en pecho", "Espacios concurridos")
    )),
    Relation("Ansiedad", "Sorpresa", "posible_raiz", listOf(
        RelationExample("Demasiados imprevistos", "Hiperalerta", "Cambios inesperados")
    )),
    Relation("Ansiedad", "Vergüenza", "posible_raiz", listOf(
        RelationExample("Me verán temblar", "Rubor", "Situación social evaluativa")
    )),

    Relation("Frustración", "Ira", "posible_raiz", listOf(
        RelationExample("Me lo impiden", "Mandíbula apretada", "Bloqueos repetidos")
    )),
    Relation("Frustración", "Tristeza", "posible_raiz", listOf(
        RelationExample("Pierdo lo que esperaba", "Pesadez", "Resultados peores de lo esperado")
    )),
    Relation("Frustración", "Sorpresa", "posible_raiz", listOf(
        RelationExample("No contaba con esto", "Tensión repentina", "Imprevistos laborales")
    )),

    Relation("Orgullo", "Alegría", "posible_raiz", listOf(
        RelationExample("He cumplido con mis valores", "Expansión torácica", "Después de un logro significativo"),
        RelationExample("Me reconozco el esfuerzo", "Sonrisa sostenida", "Meta alcanzada")
    )),

    Relation("Gratitud", "Alegría", "posible_raiz", listOf(
        RelationExample("Valoro lo recibido", "Calor en el pecho", "Apoyo genuino")
    )),
    Relation("Gratitud", "Sorpresa", "posible_raiz", listOf(
        RelationExample("No me lo esperaba", "Alivio y apertura", "Ayuda inesperada")
    )),

    Relation("Amor", "Alegría", "posible_raiz", listOf(
        RelationExample("Disfruto el vínculo", "Apertura corporal", "Conexión y cuidado mutuo")
    )),

    Relation("Envidia", "Tristeza", "posible_raiz", listOf(
        RelationExample("Yo no lo tengo", "Nudo en garganta", "Comparación social")
    )),
    Relation("Envidia", "Ira", "posible_raiz", listOf(
        RelationExample("No es justo", "Tono muscular alto", "Percepción de injusticia")
    )),

    Relation("Alivio", "Miedo", "posible_raiz", listOf(
        RelationExample("Ya pasó el peligro", "Exhalación larga", "Recibes una buena noticia tras la espera")
    )),
    Relation("Alivio", "Ansiedad", "posible_raiz", listOf(
        RelationExample("Se resolvió lo incierto", "Descarga de tensión", "Resultado confirmado")
    )),
    Relation("Alivio", "Tristeza", "posible_raiz", listOf(
        RelationExample("Terminó la racha", "Relajación progresiva", "Etapa difícil que concluye")
    )),

    // Otras ya presentes
    Relation("Resignación", "Tristeza", "posible_raiz", listOf(
        RelationExample("Ya no espero nada", "Baja energía", "Resultados negativos mantenidos")
    )),
    Relation("Apatía", "Tristeza", "posible_raiz", listOf(
        RelationExample("Nada me atrae", "Pesadez", "Pérdida de reforzadores")
    )),
    Relation("Vergüenza", "Miedo", "posible_raiz", listOf(
        RelationExample("Temo ser juzgado/a", "Mirada baja", "Exposición social")
    )),
    Relation("Culpa", "Ira", "posible_raiz", listOf(
        RelationExample("Me pasé de vueltas", "Fatiga posterior", "Conflicto previo")
    )),
    Relation("Desesperanza", "Tristeza", "posible_raiz", listOf(
        RelationExample("Nada cambia", "Opresión", "Racha larga")
    )),
    Relation("Rencor", "Ira", "posible_raiz", listOf(
        RelationExample("No olvido la ofensa", "Tensión crónica", "Conflicto antiguo")
    )),
)

/* ======================== API principal ======================== */

/** Dado una PRIMARIA, sugiere SECUNDARIAS con ejemplos. */
fun relatedSecondariesForPrimary(labelOrKey: String): List<Relation> {
    val k = norm(labelOrKey)
    return RELS.filter { norm(it.from) == k && it.type == "deriva_en" }
}

/** Dada una SECUNDARIA, sugiere PRIMARIAS raíz con ejemplos. */
fun relatedPrimariesForSecondary(labelOrKey: String): List<Relation> {
    val k = norm(labelOrKey)
    return RELS.filter { norm(it.from) == k && it.type == "posible_raiz" }
}

/* ======================== UI helpers opcionales ======================== */

@Composable
fun RelationHintsFromPrimary(primaryLabel: String) {
    val list = relatedSecondariesForPrimary(primaryLabel)
    if (list.isEmpty()) return
    Column {
        Text("Relaciones típicas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        list.forEach { r ->
            Text("• $primaryLabel → ${r.to}", style = MaterialTheme.typography.bodyMedium)
            r.examples.take(2).forEach { ex ->
                Text("   – Pensamiento: ${ex.thought}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("     Cuerpo: ${ex.body} · Contexto: ${ex.context}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun RelationHintsFromSecondary(secondaryLabel: String) {
    val list = relatedPrimariesForSecondary(secondaryLabel)
    if (list.isEmpty()) return
    Column {
        Text("Posibles raíces", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        list.forEach { r ->
            Text("• $secondaryLabel ↤ ${r.to}", style = MaterialTheme.typography.bodyMedium)
            r.examples.take(2).forEach { ex ->
                Text("   – Pensamiento: ${ex.thought}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("     Cuerpo: ${ex.body} · Contexto: ${ex.context}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
