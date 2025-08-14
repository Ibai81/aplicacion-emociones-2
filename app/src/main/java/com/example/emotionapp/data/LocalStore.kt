package com.example.emotionapp.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.example.emotionapp.ui.emociones.EmotionEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Carpeta base de entradas locales */
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

/** Guarda una entrada de EMOCIONES como JSON con nombre: e_YYYYMMDD_HHMM_<lugar>.json */
fun saveEmotionEntryFile(context: Context, entry: EmotionEntry): File {
    val name = "e_${nowStamp()}_${firstWordPlace(entry.place)}.json"
    val file = File(entriesDir(context), name)
    file.writeText(Gson().toJson(entry))
    return file
}

/** Metadatos para audio (lado JSON) */
data class AudioMeta(
    val description: String,
    val generalIntensity: Int,
    val place: String
)

/**
 * Guarda una entrada de AUDIO:
 * - Copia el audio a: a_YYYYMMDD_HHMM_<lugar>.m4a
 * - Crea metadatos JSON: a_YYYYMMDD_HHMM_<lugar>.json
 * Devuelve (audioFile, metaFile)
 */
fun saveAudioEntryFiles(
    context: Context,
    source: Uri,
    description: String,
    generalIntensity: Int,
    place: String
): Pair<File, File> {
    val base = "a_${nowStamp()}_${firstWordPlace(place)}"
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
        requireNotNull(input) { "No se pudo abrir el audio." }
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

/** Listados para el gestor */
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

/** Cargar detalles */
fun loadEmotionEntry(context: Context, jsonFile: File): EmotionEntry {
    val text = jsonFile.readText()
    return Gson().fromJson(text, EmotionEntry::class.java)
}

fun loadAudioMeta(context: Context, baseName: String): AudioMeta? {
    val metaFile = File(entriesDir(context), "$baseName.json")
    if (!metaFile.exists()) return null
    return Gson().fromJson(metaFile.readText(), AudioMeta::class.java)
}

/** Uri para File local */
fun fileUri(file: File): Uri = file.toUri()

/* ===================== SUGERENCIAS (LUGARES / PERSONAS) ===================== */

private const val PREFS_SUGG = "suggest_prefs"
private const val KEY_PLACES = "places_json"
private const val KEY_PEOPLE = "people_json"
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

/* --- API pública de sugerencias --- */

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

/** Reemplaza la lista completa (para el editor en Configuración) */
fun replacePlaceSuggestions(context: Context, items: List<String>) {
    val clean = sanitizeListKeepOrder(items)
    writeStringList(context, KEY_PLACES, clean)
}

fun replacePeopleSuggestions(context: Context, items: List<String>) {
    val clean = sanitizeListKeepOrder(items)
    writeStringList(context, KEY_PEOPLE, clean)
}

fun clearPlaceSuggestions(context: Context) = writeStringList(context, KEY_PLACES, emptyList())
fun clearPeopleSuggestions(context: Context) = writeStringList(context, KEY_PEOPLE, emptyList())
