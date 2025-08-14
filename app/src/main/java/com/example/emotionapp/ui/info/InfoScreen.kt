package com.example.emotionapp.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.RowScope // <-- IMPORTANTE

data class InfoRow(
    val emocion: String,
    val descripcion: String,
    val sensaciones: String
)

private val infoRows = listOf(
    InfoRow(
        "Alegría",
        "Bienestar y expansión al conseguir o compartir algo valioso.",
        "Ligereza en el pecho, sonrisa espontánea, energía, calor agradable."
    ),
    InfoRow(
        "Tristeza",
        "Respuesta a pérdida o desilusión; invita a recogerse y pedir apoyo.",
        "Opresión en pecho/garganta, hombros caídos, cansancio, lágrimas."
    ),
    InfoRow(
        "Miedo",
        "Alarma ante amenaza; prepara para huir o protegerse.",
        "Corazón acelerado, respiración rápida, nudo en el estómago, sudor frío."
    ),
    InfoRow(
        "Ira",
        "Impulso a poner límites cuando algo parece injusto o invasivo.",
        "Calor corporal, tensión mandibular, puños cerrados, enrojecimiento."
    ),
    InfoRow(
        "Asco",
        "Rechazo ante lo que percibimos contaminante o inapropiado.",
        "Náusea, ceño fruncido, retirada del cuerpo, contracción abdominal."
    ),
    InfoRow(
        "Sorpresa",
        "Respuesta breve ante lo inesperado; orienta la atención.",
        "Sobresalto, ojos muy abiertos, respiración súbita, salto leve."
    ),
    InfoRow(
        "Ansiedad",
        "Preocupación anticipatoria; exceso de alerta sin peligro claro.",
        "Inquietud, mariposas en el estómago, sudoración, tensión en hombros."
    ),
    InfoRow(
        "Calma",
        "Sensación de seguridad y suficiencia; facilita la recuperación.",
        "Respiración profunda, ritmo estable, músculos sueltos, calor neutro."
    ),
    InfoRow(
        "Vergüenza",
        "Temor a juicio negativo; invita a proteger la imagen propia.",
        "Calor en la cara, mirar hacia abajo, encogimiento corporal."
    ),
    InfoRow(
        "Culpa",
        "Malestar por dañar normas o valores; impulsa a reparar.",
        "Nudo en el estómago, peso en el pecho, inquietud leve."
    ),
    InfoRow(
        "Amor/Afecto",
        "Vínculo y cuidado; deseo de proximidad y cooperación.",
        "Calidez en el pecho, relajación facial, ganas de acercarse."
    ),
    InfoRow(
        "Envidia",
        "Comparación desfavorable; motiva a mejorar o a evitar.",
        "Opresión torácica, inquietud, mirada repetida al objeto/persona."
    ),
    InfoRow(
        "Orgullo",
        "Satisfacción por logro propio o del grupo; afirma identidad.",
        "Pecho erguido, expansión corporal, energía, sonrisa contenida."
    ),
    InfoRow(
        "Alivio",
        "Descenso de tensión tras un riesgo que no se cumple.",
        "Suspiro profundo, relajación muscular, calor que se dispersa."
    )
)

@Composable
fun InfoScreen() {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Información sobre emociones y sensaciones",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Descripción breve de cada emoción y sus sensaciones corporales más frecuentes. Úsala como referencia.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                // Cabecera
                TableRow(
                    e = "Emoción",
                    d = "Descripción",
                    s = "Sensaciones corporales",
                    header = true
                )
                Spacer(Modifier.height(4.dp))
                // Filas
                infoRows.forEach { r ->
                    TableRow(r.emocion, r.descripcion, r.sensaciones)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TableRow(
    e: String,
    d: String,
    s: String,
    header: Boolean = false
) {
    val shape = RoundedCornerShape(8.dp)
    val bg = if (header) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(
                width = if (header) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Cell(e, weight = 0.22f, bold = header)
        Spacer(Modifier.width(8.dp))
        Cell(d, weight = 0.38f, bold = header)
        Spacer(Modifier.width(8.dp))
        Cell(s, weight = 0.40f, bold = header)
    }
}

// ⚠️ IMPORTANTE: RowScope receiver para que Modifier.weight(...) funcione
@Composable
private fun RowScope.Cell(text: String, weight: Float, bold: Boolean) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontSize = if (bold) 15.sp else 14.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = if (bold) 1 else Int.MAX_VALUE,
        overflow = if (bold) TextOverflow.Ellipsis else TextOverflow.Clip,
        lineHeight = 20.sp
    )
}
