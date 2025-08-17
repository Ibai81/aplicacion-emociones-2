package com.example.emotionapp.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/* ================== Carpeta y nombres ================== */

private fun entriesDir(context: Context): File {
    val dir = File(context.filesDir, "entries")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun nowStamp(): String {
    val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
    return sdf.format(Date())
}

private fun sanitizeSegment(raw: String): String =
    raw.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9_-]"), "")

private fun firstWordPlace(place: String?): String {
    val fw = place.orEmpty().trim().split(Regex("\\s+")).firstOrNull()?.lowercase(Locale.getDefault()) ?: "sinlugar"
    return sanitizeSegment(fw)
}

/* ================== MODELOS ================== */

data class EmotionItem(val key: String, val label: String, val intensity: Int)

data class EmotionEntry(
    val emotions: List<EmotionItem>,
    val generalIntensity: Int,
    val place: String,
    val people: String,
    val thoughts: String,
    val actions: String,
    val notes: String,
    val situationFacts: String = "",
    val topic: String = ""
)

/* ================== Guardado/carga de EMOCIONES ================== */

private fun splitNotesToSensations(notes: String?): List<String> =
    notes.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.getDefault()) }

fun saveEmotionEntryFile(context: Context, entry: EmotionEntry): File {
    val base = "e_${firstWordPlace(entry.place)}_${nowStamp()}"
    val file = File(entriesDir(context), "$base.json")
    val gson = Gson()
    val tree = JsonParser.parseString(gson.toJson(entry)).asJsonObject
    tree.add("sensations", gson.toJsonTree(splitNotesToSensations(entry.notes)))
    file.writeText(gson.toJson(tree))
    return file
}

fun saveEmotionEntryFileWithMoment(
    context: Context,
    entry: EmotionEntry,
    momentType: String,
    captureMode: String = "completa"
): File {
    val base = "e_${firstWordPlace(entry.place)}_${nowStamp()}"
    val file = File(entriesDir(context), "$base.json")
    val gson = Gson()
    val tree = JsonParser.parseString(gson.toJson(entry)).asJsonObject
    tree.add("sensations", gson.toJsonTree(splitNotesToSensations(entry.notes)))
    tree.addProperty("momentType", momentType)
    tree.addProperty("captureMode", captureMode)
    file.writeText(gson.toJson(tree))
    return file
}

fun saveEmotionEntryFileWithMomentAt(
    context: Context,
    entry: EmotionEntry,
    momentType: String,
    captureMode: String = "completa",
    atStamp: String
): File {
    val base = "e_${firstWordPlace(entry.place)}_${atStamp}"
    val file = File(entriesDir(context), "$base.json")
    val gson = Gson()
    val tree = JsonParser.parseString(gson.toJson(entry)).asJsonObject
    tree.add("sensations", gson.toJsonTree(splitNotesToSensations(entry.notes)))
    tree.addProperty("momentType", momentType)
    tree.addProperty("captureMode", captureMode)
    file.writeText(gson.toJson(tree))
    return file
}

fun loadEmotionEntry(context: Context, jsonFile: File): EmotionEntry =
    Gson().fromJson(jsonFile.readText(), EmotionEntry::class.java)

/* ================== AUDIO ================== */

data class AudioMeta(val description: String, val generalIntensity: Int, val place: String)

fun saveAudioEntryFiles(
    context: Context,
    source: Uri,
    description: String,
    generalIntensity: Int,
    place: String
): Pair<File, File> {
    val base = "a_${firstWordPlace(place)}_${nowStamp()}"
    val audioFile = File(entriesDir(context), "$base.m4a")
    copyUriToFile(context.contentResolver, source, audioFile)
    val metaFile = File(entriesDir(context), "$base.json")
    metaFile.writeText(Gson().toJson(AudioMeta(description, generalIntensity, place)))
    return audioFile to metaFile
}

fun loadAudioMeta(context: Context, baseName: String): AudioMeta? {
    val metaFile = File(entriesDir(context), "$baseName.json")
    if (!metaFile.exists()) return null
    return Gson().fromJson(metaFile.readText(), AudioMeta::class.java)
}

fun fileUri(file: File): Uri = file.toUri()

fun copyAudioAlongEmotionBase(context: Context, source: Uri, emotionJsonFile: File): File {
    val base = emotionJsonFile.nameWithoutExtension
    val audioFile = File(entriesDir(context), "$base.m4a")
    copyUriToFile(context.contentResolver, source, audioFile)
    return audioFile
}

private fun copyUriToFile(resolver: ContentResolver, uri: Uri, dest: File) {
    resolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "No se pudo abrir la fuente." }
        FileOutputStream(dest).use { output ->
            val buf = ByteArray(8 * 1024)
            var n: Int
            while (true) {
                n = input.read(buf)
                if (n <= 0) break
                output.write(buf, 0, n)
            }
            output.flush()
        }
    }
}

/* ================== Listados ================== */

data class EntryFile(val file: File, val isEmotion: Boolean, val baseName: String)

fun deleteEmotionAndMedia(context: Context, baseName: String): Boolean {
    val dir = entriesDir(context)
    var ok = true
    val json = File(dir, "$baseName.json")
    val audio = File(dir, "$baseName.m4a")
    if (json.exists()) ok = json.delete() && ok
    if (audio.exists()) ok = audio.delete() && ok
    return ok
}

fun listEmotionFiles(context: Context): List<EntryFile> =
    entriesDir(context).listFiles()
        ?.filter { it.isFile && it.name.startsWith("e_") && it.name.endsWith(".json") }
        ?.map { EntryFile(it, true, it.nameWithoutExtension) }
        ?.sortedByDescending { it.file.lastModified() }
        ?: emptyList()

fun listAudioFiles(context: Context): List<EntryFile> =
    entriesDir(context).listFiles()
        ?.filter { it.isFile && it.name.endsWith(".m4a") && (it.name.startsWith("a_") || it.name.startsWith("e_")) }
        ?.map { EntryFile(it, false, it.nameWithoutExtension) }
        ?.sortedByDescending { it.file.lastModified() }
        ?: emptyList()

/* ================== Sugerencias persistentes ================== */

private const val PREFS_SUGG = "suggest_prefs"
private const val KEY_PLACES = "places_json"
private const val KEY_PEOPLE = "people_json"
private const val KEY_SENSATIONS = "sensations_json"
private const val MAX_SUGGESTIONS = 30
private const val KEY_TOPICS = "topics_json"

fun loadTopicSuggestions(context: Context): List<String> =
    readStringList(context, KEY_TOPICS)

fun addTopicSuggestion(context: Context, topic: String) {
    val list = readStringList(context, KEY_TOPICS)
    mergeUniqueCaseInsensitive(list, listOf(topic))
    writeStringList(context, KEY_TOPICS, list)
}
fun replaceTopicSuggestions(context: Context, items: List<String>) =
    writeStringList(context, KEY_TOPICS, sanitizeListKeepOrder(items))

private fun prefs(context: Context) = context.getSharedPreferences(PREFS_SUGG, Context.MODE_PRIVATE)

private fun readStringList(context: Context, key: String): MutableList<String> {
    val json = prefs(context).getString(key, null) ?: return mutableListOf()
    val type = object : TypeToken<List<String>>() {}.type
    return (Gson().fromJson<List<String>>(json, type) ?: emptyList()).toMutableList()
}

private fun writeStringList(context: Context, key: String, list: List<String>) {
    prefs(context).edit().putString(key, Gson().toJson(list)).apply()
}

private fun normalize(s: String) = s.trim()

private fun sanitizeListKeepOrder(list: List<String>): List<String> {
    val out = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (it in list.map(::normalize).filter { it.isNotEmpty() }) {
        val k = it.lowercase(Locale.getDefault())
        if (seen.add(k)) out.add(it)
        if (out.size >= MAX_SUGGESTIONS) break
    }
    return out
}

private fun mergeUniqueCaseInsensitive(base: MutableList<String>, incoming: List<String>) {
    val seen = base.map { it.lowercase(Locale.getDefault()) }.toMutableSet()
    for (item in incoming.map(::normalize).filter { it.isNotEmpty() }) {
        val k = item.lowercase(Locale.getDefault())
        if (seen.add(k)) base.add(0, item)
    }
    val finalSeen = mutableSetOf<String>()
    val finalList = mutableListOf<String>()
    for (it in base) {
        val k = it.lowercase(Locale.getDefault())
        if (finalSeen.add(k)) finalList.add(it)
        if (finalList.size >= MAX_SUGGESTIONS) break
    }
    base.clear(); base.addAll(finalList)
}

fun loadPlaceSuggestions(context: Context): List<String> = readStringList(context, KEY_PLACES)
fun loadPeopleSuggestions(context: Context): List<String> = readStringList(context, KEY_PEOPLE)
fun loadSensationsSuggestions(context: Context): List<String> = readStringList(context, KEY_SENSATIONS)

fun addPlaceSuggestion(context: Context, place: String) {
    val list = readStringList(context, KEY_PLACES)
    mergeUniqueCaseInsensitive(list, listOf(place))
    writeStringList(context, KEY_PLACES, list)
}
fun replacePlaceSuggestions(context: Context, items: List<String>) =
    writeStringList(context, KEY_PLACES, sanitizeListKeepOrder(items))

fun addPeopleSuggestions(context: Context, people: List<String>) {
    val list = readStringList(context, KEY_PEOPLE)
    mergeUniqueCaseInsensitive(list, people)
    writeStringList(context, KEY_PEOPLE, list)
}
fun replacePeopleSuggestions(context: Context, items: List<String>) =
    writeStringList(context, KEY_PEOPLE, sanitizeListKeepOrder(items))

fun addSensationsSuggestions(context: Context, sensations: List<String>) {
    val list = readStringList(context, KEY_SENSATIONS)
    mergeUniqueCaseInsensitive(list, sensations)
    writeStringList(context, KEY_SENSATIONS, list)
}
fun replaceSensationsSuggestions(context: Context, items: List<String>) =
    writeStringList(context, KEY_SENSATIONS, sanitizeListKeepOrder(items))

/* ================== Parseo de nombres ================== */

data class ParsedName(
    val type: String,
    val place: String,
    val dateStamp: String,
    val year: Int?, val month: Int?
)

private val dateRegex = Regex("""\d{8}_\d{4}""")

fun parseBaseName(base: String): ParsedName {
    val parts = base.split("_")
    if (parts.isEmpty()) return ParsedName(base, "sinlugar", "", null, null)
    val t = parts[0]
    if (parts.size >= 3 && dateRegex.matches(parts.takeLast(2).joinToString("_"))) {
        val date = parts.takeLast(2).joinToString("_")
        val place = parts.drop(1).dropLast(2).joinToString("_").ifBlank { "sinlugar" }
        val y = date.substring(0, 4).toIntOrNull()
        val m = date.substring(4, 6).toIntOrNull()
        return ParsedName(t, place, date, y, m)
    }
    if (parts.size >= 3 && dateRegex.matches(parts[1] + "_" + parts[2])) {
        val date = parts[1] + "_" + parts[2]
        val place = parts.drop(3).joinToString("_").ifBlank { "sinlugar" }
        val y = date.substring(0, 4).toIntOrNull()
        val m = date.substring(4, 6).toIntOrNull()
        return ParsedName(t, place, date, y, m)
    }
    return ParsedName(t, "sinlugar", "", null, null)
}

fun listPlacesFromFiles(context: Context): List<String> {
    val set = linkedSetOf<String>()
    (listEmotionFiles(context) + listAudioFiles(context)).forEach {
        val p = parseBaseName(it.baseName).place
        if (p.isNotBlank()) set.add(p)
    }
    return set.toList()
}

/* ================== Exportar / Importar ZIP (locales y SAF) ================== */

@Throws(IOException::class)
fun exportEntriesToZip(context: Context, destUri: Uri) {
    val root = entriesDir(context)
    context.contentResolver.openOutputStream(destUri)?.use { os ->
        ZipOutputStream(BufferedOutputStream(os)).use { zos ->
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = root.toPath().relativize(file.toPath()).toString().replace("\\", "/")
                zos.putNextEntry(ZipEntry(rel))
                FileInputStream(file).use { it.copyTo(zos, 8 * 1024) }
                zos.closeEntry()
            }
        }
    } ?: throw IOException("No se pudo abrir destino para escribir.")
}

@Throws(IOException::class)
fun importEntriesFromZip(context: Context, srcUri: Uri) {
    val root = entriesDir(context)
    context.contentResolver.openInputStream(srcUri)?.use { ins ->
        ZipInputStream(BufferedInputStream(ins)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(root, entry.name.substringAfterLast("/"))
                if (!entry.isDirectory) FileOutputStream(outFile).use { fos -> zis.copyTo(fos, 8 * 1024) }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    } ?: throw IOException("No se pudo abrir el ZIP de origen.")
}

/* ================== Resúmenes ================== */

data class SummaryEmotion(
    val file: String,
    val lugar: String,
    val fecha: String,
    val generalIntensity: Int,
    val sensations: List<String>,
    val emotions: List<Map<String, Any?>>,
    val people: String,
    val thoughts: String,
    val actions: String,
    val situationFacts: String
)
data class SummaryAudio(
    val file: String,
    val lugar: String,
    val fecha: String,
    val generalIntensity: Int,
    val description: String
)

private val dateRegex2 = Regex("""\d{8}_\d{4}""")
private fun extractPlaceAndDate(baseName: String): Pair<String, String> {
    val parts = baseName.split("_")
    return if (parts.size >= 4 && dateRegex2.matches(parts[parts.size - 2] + "_" + parts[parts.size - 1])) {
        val lugar = parts.drop(1).dropLast(2).joinToString("_").ifBlank { "sinlugar" }
        val fecha = parts[parts.size - 2] + "_" + parts[parts.size - 1]
        lugar to fecha
    } else if (parts.size >= 3 && dateRegex2.matches(parts[1] + "_" + parts[2])) {
        val fecha = parts[1] + "_" + parts[2]
        val lugar = parts.drop(3).joinToString("_").ifBlank { "sinlugar" }
        lugar to fecha
    } else "sinlugar" to ""
}

fun buildEntriesSummary(context: Context): String {
    val gson = Gson()

    val emos = listEmotionFiles(context).mapNotNull { ef ->
        runCatching {
            val entry = loadEmotionEntry(context, ef.file)
            val (lugar, fecha) = extractPlaceAndDate(ef.baseName)

            val text = ef.file.readText()
            val sensations: List<String> = try {
                val obj = JsonParser.parseString(text).asJsonObject
                if (obj.has("sensations") && obj.get("sensations").isJsonArray)
                    gson.fromJson(obj.get("sensations"), object : TypeToken<List<String>>() {}.type)
                else splitNotesToSensations(obj.get("notes")?.asString)
            } catch (_: Exception) {
                splitNotesToSensations(entry.notes)
            }

            SummaryEmotion(
                file = ef.file.name,
                lugar = lugar,
                fecha = fecha,
                generalIntensity = entry.generalIntensity,
                sensations = sensations,
                emotions = entry.emotions.map { mapOf("key" to it.key, "label" to it.label, "intensity" to it.intensity) },
                people = entry.people,
                thoughts = entry.thoughts,
                actions = entry.actions,
                situationFacts = entry.situationFacts
            )
        }.getOrNull()
    }

    val auds = listAudioFiles(context).mapNotNull { af ->
        val meta = loadAudioMeta(context, af.baseName) ?: return@mapNotNull null
        val (lugar, fecha) = extractPlaceAndDate(af.baseName)
        SummaryAudio(
            file = af.file.name,
            lugar = lugar,
            fecha = fecha,
            generalIntensity = meta.generalIntensity,
            description = meta.description
        )
    }

    val payload = mapOf("emociones" to emos, "audios" to auds)
    return gson.toJson(payload)
}

fun copySummaryToClipboard(context: Context): String {
    val text = buildEntriesSummary(context)
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Resumen Emolog", text))
    return text
}

/* ==================== Info de emociones ==================== */

private const val PREFS_EMOINFO = "emotion_info_prefs"
private const val KEY_SHOW_DEFS = "cfg_show_def_on_select"
private const val PREFIX_SILENCED = "silenced_"
private const val PREFIX_USERDEF = "userdef_"
private const val PREFIX_USERSENS = "usersens_" // ← sensaciones personalizadas por emoción

fun getShowDefsOnSelect(context: Context): Boolean {
    val sp = context.getSharedPreferences(PREFS_EMOINFO, Context.MODE_PRIVATE)
    return sp.getBoolean(KEY_SHOW_DEFS, true)
}
fun setShowDefsOnSelect(context: Context, value: Boolean) {
    val sp = context.getSharedPreferences(PREFS_EMOINFO, Context.MODE_PRIVATE)
    val prev = sp.getBoolean(KEY_SHOW_DEFS, true)
    sp.edit().putBoolean(KEY_SHOW_DEFS, value).apply()
    if (value && !prev) clearAllEmotionSilences(context)
}

private fun sanitizeEmotionKey(key: String): String {
    val norm = java.text.Normalizer.normalize(key, java.text.Normalizer.Form.NFD)
    return norm.replace(Regex("\\p{Mn}+"), "").lowercase()
}

fun isEmotionSilenced(context: Context, key: String): Boolean {
    val sp = context.getSharedPreferences(PREFS_EMOINFO, Context.MODE_PRIVATE)
    return sp.getBoolean(PREFIX_SILENCED + sanitizeEmotionKey(key), false)
}
fun setEmotionSilenced(context: Context, key: String, value: Boolean) {
    val sp = context.getSharedPreferences(PREFS_EMOINFO, Context.MODE_PRIVATE)
    sp.edit().putBoolean(PREFIX_SILENCED + sanitizeEmotionKey(key), value).apply()
}
fun clearAllEmotionSilences(context: Context) {
    val sp = context.getSharedPreferences(PREFS_EMOINFO, Context.MODE_PRIVATE)
    val editor = sp.edit()
    for (k in sp.all.keys) if (k.startsWith(PREFIX_SILENCED)) editor.remove(k)
    editor.apply()
}

fun getUserEmotionDefinition(context: Context, key: String): String? {
    val sp = context.getSharedPreferences(PREFS_EMOINFO, Context.MODE_PRIVATE)
    return sp.getString(PREFIX_USERDEF + sanitizeEmotionKey(key), null)
}
fun setUserEmotionDefinition(context: Context, key: String, value: String?) {
    val sp = context.getSharedPreferences(PREFS_EMOINFO, Context.MODE_PRIVATE)
    val e = sp.edit()
    if (value.isNullOrBlank()) e.remove(PREFIX_USERDEF + sanitizeEmotionKey(key)) else e.putString(PREFIX_USERDEF + sanitizeEmotionKey(key), value)
    e.apply()
}

// Sensaciones personalizadas por emoción (CSV en prefs)
fun getUserEmotionSensations(context: Context, key: String): List<String>? {
    val sp = context.getSharedPreferences(PREFS_EMOINFO, Context.MODE_PRIVATE)
    val csv = sp.getString(PREFIX_USERSENS + sanitizeEmotionKey(key), null) ?: return null
    return csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(3)
}
fun setUserEmotionSensations(context: Context, key: String, value: List<String>?) {
    val sp = context.getSharedPreferences(PREFS_EMOINFO, Context.MODE_PRIVATE)
    val e = sp.edit()
    if (value == null || value.isEmpty()) {
        e.remove(PREFIX_USERSENS + sanitizeEmotionKey(key))
    } else {
        val csv = value.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(3).joinToString(", ")
        e.putString(PREFIX_USERSENS + sanitizeEmotionKey(key), csv)
    }
    e.apply()
}

// Definiciones adaptativas (breves)
fun getAdaptativeDefinition(key: String): String = when (sanitizeEmotionKey(key)) {
    "alegria" -> "Facilita el vínculo social y motiva a repetir conductas placenteras."
    "interes" -> "Dirige la atención, impulsa la curiosidad y el aprendizaje."
    "sorpresa" -> "Redirige la atención ante lo inesperado para responder rápido."
    "tristeza", "sufrimiento", "angustia", "distress" -> "Señala pérdida o necesidad de apoyo, fomenta empatía en otros."
    "ira" -> "Prepara para defender límites y responder ante la injusticia."
    "asco" -> "Protege evitando sustancias o situaciones potencialmente dañinas."
    "desprecio" -> "Señala rechazo social ante violaciones de normas o valores."
    "verguenza", "vergüenza" -> "Regula la pertenencia al grupo al inhibir conductas rechazadas socialmente."
    "culpa" -> "Promueve la reparación tras dañar a otros."
    "miedo" -> "Activa conductas de protección: huida, cautela, preparación ante amenaza."
    else -> "Emoción básica con función adaptativa."
}

// Crítica actual
fun getCriticalDefinition(key: String): String = when (sanitizeEmotionKey(key)) {
    "alegria" -> "Funcional; en exceso invisibiliza otras emociones necesarias."
    "interes" -> "Clave para crecer; en exceso dispersa y agota."
    "sorpresa" -> "Ayuda a reajustar; constante genera inestabilidad."
    "tristeza", "sufrimiento", "angustia", "distress" -> "Elabora pérdidas; cronificada aisla y deprime."
    "ira" -> "Protege límites; sostenida deteriora vínculos."
    "asco" -> "Protege; mal dirigida estigmatiza."
    "desprecio" -> "Marca frontera moral; erosiona relaciones."
    "verguenza", "vergüenza" -> "Cuida imagen; en exceso bloquea la expresión."
    "culpa" -> "Impulsa reparación; en exceso paraliza."
    "miedo" -> "Previene riesgos; cronificado limita la libertad."
    else -> "Útil con medida; cronicidad la vuelve desadaptativa."
}

/* ====== Frases clave (3 por emoción) ====== */
fun getKeyPhrases(key: String): List<String> = when (sanitizeEmotionKey(key)) {
    "miedo" -> listOf("¿Y si sale mal?", "No estoy seguro de poder", "Mejor evitarlo")
    "ira" -> listOf("¡Esto no es justo!", "No me respetan", "Hasta aquí")
    "vergüenza", "verguenza" -> listOf("Van a pensar mal de mí", "Me he quedado en ridículo", "Ojalá no me miren")
    "desprecio" -> listOf("No merece la pena", "Yo no soy como ellos", "Qué poco nivel")
    "asco" -> listOf("Qué repulsión", "Aléjalo de mí", "Esto contamina")
    "culpa" -> listOf("La he liado", "Tengo que repararlo", "No debí hacerlo")
    "sufrimiento", "tristeza", "angustia", "distress" -> listOf("Esto pesa demasiado", "Necesito apoyo", "No tengo fuerzas")
    "interes" -> listOf("¿Cómo funciona?", "Quiero entenderlo", "Voy a probar")
    "sorpresa" -> listOf("¡No me lo esperaba!", "¿Qué ha pasado?", "Toca reaccionar")
    "alegria" -> listOf("Qué bien se está", "Quiero compartirlo", "Ojalá dure")
    else -> listOf("Esto me señala algo", "Voy a observar", "¿Qué necesito ahora?")
}

/* ====== Sensaciones corporales por emoción (3) ====== */
fun getDefaultBodySensations(key: String): List<String> = when (sanitizeEmotionKey(key)) {
    "miedo" -> listOf("Nudo en el estómago", "Tensión en el pecho", "Respiración acelerada")
    "ira" -> listOf("Calor en la cara", "Mandíbula apretada", "Puños tensos")
    "vergüenza", "verguenza" -> listOf("Rubor facial", "Mirada hacia abajo", "Encogimiento corporal")
    "desprecio" -> listOf("Ceja levantada", "Cuerpo echado atrás", "Labio superior tenso")
    "asco" -> listOf("Náusea", "Gesto de retraimiento", "Repulsión en la boca")
    "culpa" -> listOf("Opresión en el pecho", "Baja energía", "Mirada esquiva")
    "sufrimiento", "tristeza", "angustia", "distress" -> listOf("Nudo en la garganta", "Pesadez corporal", "Lagrimeo")
    "interes" -> listOf("Ojos abiertos", "Inclinación hacia delante", "Energía suave")
    "sorpresa" -> listOf("Sobresalto", "Ojos muy abiertos", "Boca abierta")
    "alegria" -> listOf("Ligereza en el pecho", "Sonrisa espontánea", "Energía alta")
    else -> listOf("Cambio en la respiración", "Tono muscular distinto", "Postura alterada")
}

fun getBodySensations(context: Context, key: String): List<String> =
    getUserEmotionSensations(context, key) ?: getDefaultBodySensations(key)
