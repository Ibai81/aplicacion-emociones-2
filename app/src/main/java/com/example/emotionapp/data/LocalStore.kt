package com.example.emotionapp.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import com.example.emotionapp.ui.emociones.EmotionEntry
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Carpeta base de entradas locales (privada de la app) */
private fun entriesDir(context: Context): File {
    val dir = File(context.filesDir, "entries")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun nowStamp(): String {
    val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
    return sdf.format(Date())
}

private fun firstWordPlace(place: String?): String {
    val fw = place.orEmpty()
        .trim()
        .split(Regex("\\s+"))
        .firstOrNull()
        ?.lowercase(Locale.getDefault())
        ?: "sinlugar"
    return fw.replace(Regex("[^a-z0-9_-]"), "")
}

/* ================== GUARDADO ================== */

/** Util: partir notas en sensaciones (lista limpia) */
private fun splitNotesToSensations(notes: String?): List<String> =
    notes.orEmpty()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.getDefault()) }

/** Guarda EMOCIÓN como JSON con nombre: e_<lugar>_<fecha>.json
 *  Además añade al JSON un campo `sensations` (lista) derivado de `notes` separadas por comas. */
fun saveEmotionEntryFile(context: Context, entry: EmotionEntry): File {
    val base = "e_${firstWordPlace(entry.place)}_${nowStamp()}"
    val file = File(entriesDir(context), "$base.json")
    val gson = Gson()

    val tree = JsonParser.parseString(gson.toJson(entry)).asJsonObject
    val sensations = splitNotesToSensations(tree.get("notes")?.asString)
    tree.add("sensations", gson.toJsonTree(sensations))

    file.writeText(gson.toJson(tree))
    return file
}

/** Metadatos de AUDIO (lado JSON) */
data class AudioMeta(
    val description: String,
    val generalIntensity: Int,
    val place: String
)

/** Guarda AUDIO: a_<lugar>_<fecha>.m4a y a_<lugar>_<fecha>.json (metadatos). */
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

    val meta = AudioMeta(description, generalIntensity, place)
    val metaFile = File(entriesDir(context), "$base.json")
    metaFile.writeText(Gson().toJson(meta))
    return audioFile to metaFile
}

/** Util: copiar de un Uri a un File */
private fun copyUriToFile(resolver: ContentResolver, uri: Uri, dest: File) {
    resolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "No se pudo abrir la fuente." }
        FileOutputStream(dest).use { output ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                output.write(buf, 0, n)
            }
            output.flush()
        }
    }
}

/* ================== LISTADOS / CARGA ================== */

data class EntryFile(
    val file: File,
    val isEmotion: Boolean,
    val baseName: String
)

fun listEmotionFiles(context: Context): List<EntryFile> =
    entriesDir(context).listFiles()
        ?.filter { it.isFile && it.name.startsWith("e_") && it.name.endsWith(".json") }
        ?.map { EntryFile(it, true, it.nameWithoutExtension) }
        ?.sortedByDescending { it.file.lastModified() }
        ?: emptyList()

fun listAudioFiles(context: Context): List<EntryFile> {
    val dir = entriesDir(context)
    val audios = dir.listFiles()
        ?.filter { it.isFile && it.name.startsWith("a_") && it.name.endsWith(".m4a") }
        ?.map { EntryFile(it, false, it.nameWithoutExtension) }
        ?: emptyList()
    return audios.sortedByDescending { it.file.lastModified() }
}

fun loadEmotionEntry(context: Context, jsonFile: File): EmotionEntry {
    val text = jsonFile.readText()
    return Gson().fromJson(text, EmotionEntry::class.java)
}

/** Carga metadatos de audio a partir del baseName (a_..._YYYYMMDD_HHMM) */
fun loadAudioMeta(context: Context, baseName: String): AudioMeta? {
    val metaFile = File(entriesDir(context), "$baseName.json")
    if (!metaFile.exists()) return null
    return Gson().fromJson(metaFile.readText(), AudioMeta::class.java)
}

/** Uri para File local */
fun fileUri(file: File): Uri = file.toUri()

/* ================== SUGERENCIAS (LUGARES / PERSONAS / SENSACIONES) ================== */

private const val PREFS_SUGG = "suggest_prefs"
private const val KEY_PLACES = "places_json"
private const val KEY_PEOPLE = "people_json"
private const val KEY_SENSATIONS = "sensations_json"
private const val MAX_SUGGESTIONS = 30

private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS_SUGG, Context.MODE_PRIVATE)

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
    val seen = mutableSetOf<String>()
    base.forEach { seen.add(it.lowercase(Locale.getDefault())) }
    for (item in incoming.map(::normalize).filter { it.isNotEmpty() }) {
        val key = item.lowercase(Locale.getDefault())
        if (seen.add(key)) base.add(0, item) // al principio = más reciente
    }
    // Dedup conservando el primero (más reciente)
    val finalSeen = mutableSetOf<String>()
    val finalList = mutableListOf<String>()
    for (it in base) {
        val k = it.lowercase(Locale.getDefault())
        if (finalSeen.add(k)) finalList.add(it)
        if (finalList.size >= MAX_SUGGESTIONS) break
    }
    base.clear()
    base.addAll(finalList)
}

/* API pública sugerencias: lugares / personas */
fun loadPlaceSuggestions(context: Context): List<String> =
    readStringList(context, KEY_PLACES)

fun loadPeopleSuggestions(context: Context): List<String> =
    readStringList(context, KEY_PEOPLE)

fun addPlaceSuggestion(context: Context, place: String) {
    val list = readStringList(context, KEY_PLACES)
    mergeUniqueCaseInsensitive(list, listOf(place))
    writeStringList(context, KEY_PLACES, list)
}

fun addPeopleSuggestions(context: Context, people: List<String>) {
    val list = readStringList(context, KEY_PEOPLE)
    mergeUniqueCaseInsensitive(list, people)
    writeStringList(context, KEY_PEOPLE, list)
}

fun replacePlaceSuggestions(context: Context, items: List<String>) {
    writeStringList(context, KEY_PLACES, sanitizeListKeepOrder(items))
}

fun replacePeopleSuggestions(context: Context, items: List<String>) {
    writeStringList(context, KEY_PEOPLE, sanitizeListKeepOrder(items))
}

/* API pública sugerencias: SENSACIONES CORPORALES */
fun loadSensationsSuggestions(context: Context): List<String> =
    readStringList(context, KEY_SENSATIONS)

fun addSensationsSuggestions(context: Context, sensations: List<String>) {
    val list = readStringList(context, KEY_SENSATIONS)
    mergeUniqueCaseInsensitive(list, sensations)
    writeStringList(context, KEY_SENSATIONS, list)
}

fun replaceSensationsSuggestions(context: Context, items: List<String>) {
    writeStringList(context, KEY_SENSATIONS, sanitizeListKeepOrder(items))
}

fun clearSensationsSuggestions(context: Context) =
    writeStringList(context, KEY_SENSATIONS, emptyList())

/* ================== Parseo nombre (ambos formatos) ================== */
data class ParsedName(
    val type: String,      // "e" o "a"
    val place: String,
    val dateStamp: String, // YYYYMMDD_HHMM
    val year: Int?,
    val month: Int?
)

private val dateRegex = Regex("""\d{8}_\d{4}""")

fun parseBaseName(base: String): ParsedName {
    val parts = base.split("_")
    if (parts.isEmpty()) return ParsedName(base, "sinlugar", "", null, null)
    val t = parts[0]
    // nuevo: e_<lugar>_<YYYYMMDD>_<HHMM>
    if (parts.size >= 3 && dateRegex.matches(parts.takeLast(2).joinToString("_"))) {
        val date = parts.takeLast(2).joinToString("_")
        val place = parts.drop(1).dropLast(2).joinToString("_").ifBlank { "sinlugar" }
        val y = date.substring(0, 4).toIntOrNull()
        val m = date.substring(4, 6).toIntOrNull()
        return ParsedName(t, place, date, y, m)
    }
    // antiguo: e_<YYYYMMDD>_<HHMM>_<lugar>
    if (parts.size >= 3 && dateRegex.matches(parts[1] + "_" + parts[2])) {
        val date = parts[1] + "_" + parts[2]
        val place = parts.drop(3).joinToString("_").ifBlank { "sinlugar" }
        val y = date.substring(0, 4).toIntOrNull()
        val m = date.substring(4, 6).toIntOrNull()
        return ParsedName(t, place, date, y, m)
    }
    return ParsedName(t, "sinlugar", "", null, null)
}

/** Lugares detectados por nombres */
fun listPlacesFromFiles(context: Context): List<String> {
    val set = linkedSetOf<String>()
    (listEmotionFiles(context) + listAudioFiles(context)).forEach {
        val p = parseBaseName(it.baseName).place
        if (p.isNotBlank()) set.add(p)
    }
    return set.toList()
}

/* ================== Exportar / Importar ZIP ================== */
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
                val outFile = File(root, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos, 8 * 1024) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    } ?: throw IOException("No se pudo abrir el ZIP de origen.")
}

/** ZIP local en …/Documents/Exportaciones_emolog/ */
@Throws(IOException::class)
fun exportEntriesToLocalZip(context: Context): File {
    val baseDir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
        "Exportaciones_emolog"
    )
    if (!baseDir.exists()) baseDir.mkdirs()
    val outFile = File(baseDir, "backup_emolog_${nowStamp()}.zip")

    ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
        val root = entriesDir(context)
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = root.toPath().relativize(file.toPath()).toString().replace("\\", "/")
            zos.putNextEntry(ZipEntry(rel))
            FileInputStream(file).use { it.copyTo(zos, 8 * 1024) }
            zos.closeEntry()
        }
    }
    return outFile
}

/* ================== RESUMEN PARA IA (PORTAPAPELES) ================== */

data class SummaryEmotion(
    val file: String,
    val lugar: String,
    val fecha: String,
    val generalIntensity: Int,
    val sensations: List<String>, // lista
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
    } else {
        "sinlugar" to ""
    }
}

/** Construye JSON con emociones + audios (sin binarios) */
fun buildEntriesSummary(context: Context): String {
    val gson = Gson()

    val emos = listEmotionFiles(context).mapNotNull { ef ->
        runCatching {
            val entry = loadEmotionEntry(context, ef.file)
            val (lugar, fecha) = extractPlaceAndDate(ef.baseName)

            // Sensaciones: usa "sensations" si existe; si no, parte "notes"
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

/** Copia el resumen al portapapeles (para pegar en ChatGPT/otra IA) */
fun copySummaryToClipboard(context: Context): String {
    val text = buildEntriesSummary(context)
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Resumen Emolog", text))
    return text
}
