package com.example.emotionapp.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
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

private fun todayYmd(): String {
    val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return sdf.format(Date())
}

private fun sanitizeSegment(raw: String): String =
    raw.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9_-]"), "")

private fun firstWordPlace(place: String?): String {
    val fw = place.orEmpty().trim().split(Regex("\\s+")).firstOrNull()?.lowercase(Locale.getDefault()) ?: "sinlugar"
    return sanitizeSegment(fw)
}

/* ================== MODELOS (canon en data) ================== */

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
    val topic: String = ""            // NUEVO (opcional en JSON antiguo)
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
    momentType: String,            // "instantanea" | "reflexion"
    captureMode: String = "completa" // "rapida" | "completa" | "texto"
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
// NUEVO: guarda la emoción fijando el sello temporal del nombre (YYYYMMDD_HHMM)
fun saveEmotionEntryFileWithMomentAt(
    context: Context,
    entry: EmotionEntry,
    momentType: String,             // "instantanea" | "reflexion"
    captureMode: String = "completa",
    atStamp: String                 // "YYYYMMDD_HHMM" (del audio origen)
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

/** Formato clásico: a_<lugar>_<YYYYMMDD>_<HHMM>.(m4a|json) */
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

/** Copia binario del audio “pegado” a una emoción e_<...>.json => e_<...>.m4a */
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
// ===== Borrado de entrada (JSON) + audio emparejado (si existe) =====
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

/** Acepta audios antiguos (a_*.m4a) y nuevos emparejados con emoción (e_*.m4a). */
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
    val out = mutableListOf<String>();
    val seen = mutableSetOf<String>();
    for (it in list.map(::normalize).filter { it.isNotEmpty() }) {
        val k = it.lowercase(Locale.getDefault());
        if (seen.add(k)) out.add(it);
        if (out.size >= MAX_SUGGESTIONS) break;
    }
    return out;
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

/* ================== Parseo de nombres (ambos formatos) ================== */

data class ParsedName(
    val type: String,
    val place: String,
    val dateStamp: String, // YYYYMMDD_HHMM
    val year: Int?, val month: Int?
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

fun listPlacesFromFiles(context: Context): List<String> {
    val set = linkedSetOf<String>()
    (listEmotionFiles(context) + listAudioFiles(context)).forEach {
        val p = parseBaseName(it.baseName).place
        if (p.isNotBlank()) set.add(p)
    }
    return set.toList()
}

/* ================== Exportar / Importar ZIP ================== */

/** Exporta a un ZIP usando el selector del sistema (SAF). */
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

/** Exporta a un ZIP local dentro de /Documents de la app y devuelve el File. */
@Throws(IOException::class)
fun exportEntriesToLocalZip(context: Context): File {
    val baseDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "Exportaciones_emolog")
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

/** NUEVO: Exporta un subconjunto de bases a un ZIP con nombre controlado. */
@Throws(IOException::class)
fun exportEntriesToLocalZipSubset(context: Context, baseNames: Set<String>?, outName: String): File {
    val baseDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "Exportaciones_emolog")
    if (!baseDir.exists()) baseDir.mkdirs()
    val safe = outName.trim().lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9._-]"), "_")
    val finalName = if (safe.endsWith(".zip")) safe else "$safe.zip"
    val outFile = File(baseDir, finalName)

    val root = entriesDir(context)
    val allFiles = root.listFiles()?.filter { it.isFile } ?: emptyList()
    val allowedBases = baseNames?.toSet()

    ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
        for (f in allFiles) {
            val base = f.nameWithoutExtension
            val include = if (allowedBases == null) true else allowedBases.contains(base)
            if (!include) continue
            val rel = root.toPath().relativize(f.toPath()).toString().replace("\\", "/")
            zos.putNextEntry(ZipEntry(rel))
            FileInputStream(f).use { it.copyTo(zos, 8 * 1024) }
            zos.closeEntry()
        }
    }
    return outFile
}

/** Importa desde un ZIP seleccionado con SAF. */
@Throws(IOException::class)
fun importEntriesFromZip(context: Context, srcUri: Uri) {
    val root = entriesDir(context)
    context.contentResolver.openInputStream(srcUri)?.use { ins ->
        ZipInputStream(BufferedInputStream(ins)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(root, entry.name.substringAfterLast("/"))
                if (!entry.isDirectory) {
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos, 8 * 1024) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    } ?: throw IOException("No se pudo abrir el ZIP de origen.")
}

/** NUEVO: Importa desde un ZIP local (File). Sobrescribe si existe. */
@Throws(IOException::class)
fun importEntriesFromLocalZip(context: Context, zipFile: File) {
    val root = entriesDir(context)
    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val outFile = File(root, entry.name.substringAfterLast("/"))
            if (!entry.isDirectory) {
                FileOutputStream(outFile).use { fos -> zis.copyTo(fos, 8 * 1024) }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
}

/** NUEVO: Lista de backups .zip locales en la carpeta Exportaciones_emolog */
fun listLocalBackups(context: Context): List<File> {
    val baseDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "Exportaciones_emolog")
    if (!baseDir.exists()) return emptyList()
    return baseDir.listFiles()?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }?.sortedByDescending { it.lastModified() } ?: emptyList()
}

/* ================== Resumen para IA ================== */

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

/** NUEVO: resumen filtrado por baseNames (null = todo) */
fun buildEntriesSummaryFor(context: Context, baseFilter: Set<String>?): String {
    val gson = Gson()
    val filter = baseFilter?.toSet()

    val emos = listEmotionFiles(context).filter { filter == null || filter.contains(it.baseName) }.mapNotNull { ef ->
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

    val auds = listAudioFiles(context).filter { filter == null || filter.contains(it.baseName) }.mapNotNull { af ->
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

/** NUEVO: copia resumen filtrado al portapapeles */
fun copySummaryToClipboardFor(context: Context, baseFilter: Set<String>?): String {
    val text = buildEntriesSummaryFor(context, baseFilter)
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Resumen Emolog", text))
    return text
}

/* ================== Motor de patrones (Reflexión) ================== */

data class PatternItem(val name: String, val count: Int, val avgIntensity: Double)
data class PatternsReport(
    val totalEntries: Int,
    val places: List<PatternItem>,
    val people: List<PatternItem>,
    val sensations: List<PatternItem>,
    val emotions: List<PatternItem>
)

private const val PREFS_ANALYTICS = "analytics_prefs"
private const val KEY_PATTERNS_SHOWN_AT9 = "patterns_shown_once_after_8"

fun shouldShowPatternsNow(context: Context): Boolean {
    val total = listEmotionFiles(context).size
    val shown = context.getSharedPreferences(PREFS_ANALYTICS, Context.MODE_PRIVATE)
        .getBoolean(KEY_PATTERNS_SHOWN_AT9, false)
    return total >= 9 && !shown
}
fun markPatternsShown(context: Context) {
    context.getSharedPreferences(PREFS_ANALYTICS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_PATTERNS_SHOWN_AT9, true).apply()
}

fun computePatterns(context: Context, minCount: Int = 8): PatternsReport {
    val files = listEmotionFiles(context)
    val total = files.size
    val gson = Gson()

    val placeCount = mutableMapOf<String, MutableList<Int>>()
    val peopleCount = mutableMapOf<String, MutableList<Int>>()
    val sensCount = mutableMapOf<String, MutableList<Int>>()
    val emoCount = mutableMapOf<String, MutableList<Int>>()

    for (ef in files) {
        val text = ef.file.readText()
        val obj: JsonObject = try { JsonParser.parseString(text).asJsonObject } catch (_: Exception) { continue }
        val entry = try { gson.fromJson(text, EmotionEntry::class.java) } catch (_: Exception) { continue }
        val intensity = entry.generalIntensity.coerceIn(1, 5)

        val lugar = entry.place.trim().ifEmpty { "sin lugar" }
        placeCount.getOrPut(lugar) { mutableListOf() }.add(intensity)

        entry.people.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { p ->
            peopleCount.getOrPut(p) { mutableListOf() }.add(intensity)
        }

        val sensations: List<String> = try {
            if (obj.has("sensations") && obj.get("sensations").isJsonArray)
                gson.fromJson(obj.get("sensations"), object : TypeToken<List<String>>() {}.type)
            else splitNotesToSensations(obj.get("notes")?.asString)
        } catch (_: Exception) {
            splitNotesToSensations(entry.notes)
        }
        sensations.forEach { s -> sensCount.getOrPut(s) { mutableListOf() }.add(intensity) }

        entry.emotions.forEach { e -> emoCount.getOrPut(e.label) { mutableListOf() }.add(e.intensity.coerceIn(1,5)) }
    }

    fun mapToItems(m: Map<String, List<Int>>): List<PatternItem> =
        m.entries
            .map { (k, v) -> PatternItem(k, v.size, ((v.average() * 10.0).roundToInt() / 10.0)) }
            .filter { it.count >= minCount }
            .sortedWith(compareByDescending<PatternItem> { it.count }.thenByDescending { it.avgIntensity })

    return PatternsReport(
        totalEntries = total,
        places = mapToItems(placeCount),
        people = mapToItems(peopleCount),
        sensations = mapToItems(sensCount),
        emotions = mapToItems(emoCount)
    )
}

fun buildPatternsMessage(report: PatternsReport, topPerGroup: Int = 3): String {
    if (report.totalEntries < 8) return "Aún no hay suficientes entradas para proponer patrones (necesitamos al menos 8)."
    val sb = StringBuilder()
    fun addBlock(title: String, list: List<PatternItem>) {
        val top = list.take(topPerGroup)
        if (top.isNotEmpty()) {
            sb.appendLine("• $title")
            for (it in top) sb.appendLine("   - ${it.name}: ${it.count} apariciones (intensidad media ${"%.1f".format(Locale.getDefault(), it.avgIntensity)})")
        }
    }
    addBlock("Lugares frecuentes", report.places)
    addBlock("Personas asociadas", report.people)
    addBlock("Sensaciones corporales repetidas", report.sensations)
    addBlock("Emociones predominantes", report.emotions)
    return if (sb.isEmpty()) "Hay suficientes entradas, pero aún no se detectan patrones claros con ≥8 observaciones." else sb.toString()
}
/* … (todo tu archivo tal como estaba) … */

/* ================== PREFILL / NAV BRIDGE ================== */

private const val PREFS_PREFILL = "prefill_bus"
private const val KEY_PREFILL_PLACE = "prefill_place"
private const val KEY_PREFILL_NOTES = "prefill_notes"
private const val KEY_PREFILL_INT = "prefill_intensity"
private const val KEY_PREFILL_FROM_AUDIO = "prefill_from_audio"
private const val KEY_PREFILL_PENDING_OPEN = "prefill_pending_open"

data class EmotionPrefill(
    val place: String,
    val notes: String,
    val generalIntensity: Int,
    val fromAudioBase: String?
)

/** Guardar un pre-relleno en preferencias a partir de una entrada de audio existente.
 *  Además marca la intención de abrir la pestaña Emociones.
 */
fun setEmotionPrefillFromAudio(context: Context, baseName: String): Boolean {
    val meta = loadAudioMeta(context, baseName) ?: return false
    val (placeFromBase, _) = extractPlaceAndDate(baseName)
    val place = if (meta.place.isNotBlank()) meta.place else placeFromBase
    val prefs = context.getSharedPreferences(PREFS_PREFILL, Context.MODE_PRIVATE)
    prefs.edit()
        .putString(KEY_PREFILL_PLACE, place)
        .putString(KEY_PREFILL_NOTES, meta.description)
        .putInt(KEY_PREFILL_INT, meta.generalIntensity.coerceIn(1,5))
        .putString(KEY_PREFILL_FROM_AUDIO, baseName)
        .putBoolean(KEY_PREFILL_PENDING_OPEN, true)
        .apply()
    return true
}

/** Consumir y limpiar el pre-relleno (si existe). */
fun consumeEmotionPrefill(context: Context): EmotionPrefill? {
    val prefs = context.getSharedPreferences(PREFS_PREFILL, Context.MODE_PRIVATE)
    val from = prefs.getString(KEY_PREFILL_FROM_AUDIO, null)
    val hasAny = prefs.contains(KEY_PREFILL_PLACE) || prefs.contains(KEY_PREFILL_NOTES) || from != null
    if (!hasAny) return null
    val place = prefs.getString(KEY_PREFILL_PLACE, "") ?: ""
    val notes = prefs.getString(KEY_PREFILL_NOTES, "") ?: ""
    val gi = prefs.getInt(KEY_PREFILL_INT, 3)
    // limpiamos todo pero conservamos pending_open si estuviera activo
    val pending = prefs.getBoolean(KEY_PREFILL_PENDING_OPEN, false)
    prefs.edit().clear().apply()
    if (pending) context.getSharedPreferences(PREFS_PREFILL, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_PREFILL_PENDING_OPEN, true).apply()
    return EmotionPrefill(place, notes, gi, from)
}

/** Consumir la marca de 'abrir Emociones' (true una sola vez) */
fun consumePendingOpenEmotion(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_PREFILL, Context.MODE_PRIVATE)
    val pending = prefs.getBoolean(KEY_PREFILL_PENDING_OPEN, false)
    if (pending) prefs.edit().putBoolean(KEY_PREFILL_PENDING_OPEN, false).apply()
    return pending
}

/** Guardar una emoción usando el mismo 'baseName' del audio y borrar el origen. */
fun saveEmotionEntryUsingAudioBase(context: Context, entry: EmotionEntry, audioBaseName: String): File {
    // Derivamos base e_<...> a partir de a_<...>
    val base = (if (audioBaseName.startsWith("a_")) "e_" + audioBaseName.substring(2) else "e_$audioBaseName")
        .removeSuffix(".m4a").removeSuffix(".json")
    val out = File(entriesDir(context), "$base.json")

    val gson = Gson()
    val tree = JsonParser.parseString(gson.toJson(entry)).asJsonObject
    val sensations = splitNotesToSensations(tree.get("notes")?.asString)
    tree.add("sensations", gson.toJsonTree(sensations))
    out.writeText(gson.toJson(tree))

    // Borrar archivos de audio origen (si existen)
    val audioRoot = audioBaseName.removeSuffix(".m4a").removeSuffix(".json")
    File(entriesDir(context), "$audioRoot.m4a").delete()
    File(entriesDir(context), "$audioRoot.json").delete()

    return out
}
