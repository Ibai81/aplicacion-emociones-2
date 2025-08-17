package com.example.emotionapp.data

import android.content.Context
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

/* =========================================================
   TABLA DE CONEXIONES (curada, editable)
   - Nombres en castellano (coinciden con labels de tu paleta)
   - Puedes ampliar/ajustar sin tocar el resto del código
   ========================================================= */

private val RELS: List<Relation> = listOf(
    // MIEDO → …
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

    // TRISTEZA → …
    Relation("Tristeza", "Resignación", "deriva_en", listOf(
        RelationExample("Para qué intentarlo", "Pesadez corporal", "Tras varios intentos fallidos")
    )),
    Relation("Tristeza", "Apatía", "deriva_en", listOf(
        RelationExample("No me apetece nada", "Baja energía", "Días sin reforzadores")
    )),
    Relation("Tristeza", "Desesperanza", "deriva_en", listOf(
        RelationExample("Nada va a cambiar", "Opresión en pecho", "Racha larga de estrés")
    )),

    // IRA → …
    Relation("Ira", "Frustración", "deriva_en", listOf(
        RelationExample("Siempre me bloquean", "Mandíbula tensa", "Trámites, burocracia")
    )),
    Relation("Ira", "Culpa", "deriva_en", listOf(
        RelationExample("No debí hablar así", "Calor que baja", "Después de una discusión")
    )),
    Relation("Ira", "Rencor", "deriva_en", listOf(
        RelationExample("No olvido lo que me hizo", "Tensión prolongada", "Conflictos no resueltos")
    )),

    // VERGÜENZA → …
    Relation("Vergüenza", "Ansiedad", "deriva_en", listOf(
        RelationExample("Se van a fijar en mí", "Mirada hacia abajo", "Reuniones, exposiciones")
    )),
    Relation("Vergüenza", "Aislamiento", "deriva_en", listOf(
        RelationExample("Mejor no voy", "Cierre corporal", "Quedar con grupo nuevo")
    )),

    // CULPA → …
    Relation("Culpa", "Ansiedad", "deriva_en", listOf(
        RelationExample("No sé cómo repararlo", "Nudo en garganta", "Tras un error con impacto")
    )),
    Relation("Culpa", "Vergüenza", "deriva_en", listOf(
        RelationExample("Qué pensarán de mí", "Rubor", "Error visto por otros")
    )),

    // ASCO / DESPRECIO
    Relation("Asco", "Desprecio", "deriva_en", listOf(
        RelationExample("Esto es inaceptable", "Gesto de retraimiento", "Normas vulneradas")
    )),

    // INTERÉS y SORPRESA
    Relation("Sorpresa", "Miedo", "deriva_en", listOf(
        RelationExample("No lo esperaba y me asusta", "Sobresalto", "Cambio brusco de plan")
    )),
    Relation("Sorpresa", "Ansiedad", "deriva_en", listOf(
        RelationExample("¿Y ahora qué hago?", "Agitación", "Imprevistos laborales")
    )),
    Relation("Interés", "Frustración", "deriva_en", listOf(
        RelationExample("Quiero seguir pero no puedo", "Tensión leve", "Bloqueos/limitaciones externas")
    )),

    // ALEGRÍA → sociales
    Relation("Alegría", "Orgullo", "deriva_en", listOf(
        RelationExample("Lo he conseguido", "Ligereza", "Después de un logro")
    )),
    Relation("Alegría", "Gratitud", "deriva_en", listOf(
        RelationExample("Qué suerte tenerte", "Calidez", "Apoyo recibido")
    )),

    /* Inversas: cuando percibes una SECUNDARIA, revisar PRIMARIAS probables */
    Relation("Ansiedad", "Miedo", "posible_raiz", listOf(
        RelationExample("Algo puede ir mal", "Respiración rápida", "Antes de exposición"),
        RelationExample("Perder el control", "Tensión en pecho", "Espacios concurridos")
    )),
    Relation("Ansiedad", "Vergüenza", "posible_raiz", listOf(
        RelationExample("Me verán temblar", "Rubor", "Social")
    )),
    Relation("Frustración", "Ira", "posible_raiz", listOf(
        RelationExample("Me lo impiden", "Mandíbula apretada", "Bloqueos repetidos")
    )),
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
        RelationExample("Me pasé de vueltas", "Fadiga posterior", "Conflicto previo")
    )),
    Relation("Desesperanza", "Tristeza", "posible_raiz", listOf(
        RelationExample("Nada cambia", "Opresión", "Racha larga")
    )),
    Relation("Rencor", "Ira", "posible_raiz", listOf(
        RelationExample("No olvido la ofensa", "Tensión crónica", "Conflicto antiguo")
    ))
)

/* =========================================================
   API pública
   ========================================================= */

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

/* =========================================================
   UI helpers (opcionales) para incrustar en pantallas
   ========================================================= */

/** Bloque visual compacto: “Si sientes [primaria], suele derivar en…” */
@Composable
fun RelationHintsFromPrimary(primaryLabel: String) {
    val list = relatedSecondariesForPrimary(primaryLabel)
    if (list.isEmpty()) return
    Column {
        Text("Relaciones típicas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        list.forEach { r ->
            Text("• ${primaryLabel} → ${r.to}", style = MaterialTheme.typography.bodyMedium)
            // 1–2 ejemplos rápidos
            r.examples.take(2).forEach { ex ->
                Text("   – Pensamiento: ${ex.thought}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "     Cuerpo: ${ex.body} · Contexto: ${ex.context}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(6.dp))
        }
    }
}

/** Bloque visual compacto: “Si percibes [secundaria], revisa si hay…” */
@Composable
fun RelationHintsFromSecondary(secondaryLabel: String) {
    val list = relatedPrimariesForSecondary(secondaryLabel)
    if (list.isEmpty()) return
    Column {
        Text("Posibles raíces", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        list.forEach { r ->
            Text("• ${secondaryLabel} ↤ ${r.to}", style = MaterialTheme.typography.bodyMedium)
            r.examples.take(2).forEach { ex ->
                Text("   – Pensamiento: ${ex.thought}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("     Cuerpo: ${ex.body} · Contexto: ${ex.context}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
}