package com.example.emotionapp.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File

/* ========= Carpeta “entries” (local) ========= */
private fun entriesRoot(context: Context): File {
    val dir = File(context.filesDir, "entries")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

/* ========= Utils ========= */
private fun splitNotesToSensations(notes: String?): List<String> =
    notes.orEmpty()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

/* ========= PREFILL / NAV BRIDGE ========= */

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

/**
 * Carga los metadatos del audio (place/description/intensity) y los deja
 * listos para abrir Emociones con esos campos precargados.
 */
fun setEmotionPrefillFromAudio(context: Context, baseName: String): Boolean {
    val meta = loadAudioMeta(context, baseName) ?: return false
    // Si el nombre del archivo lleva lugar en el propio baseName, úsalo de respaldo
    val placeFromBase = runCatching {
        parseBaseName(baseName.removeSuffix(".m4a").removeSuffix(".json")).place
    }.getOrDefault("")

    val place = if (meta.place.isNotBlank()) meta.place else placeFromBase
    val sp = context.getSharedPreferences(PREFS_PREFILL, Context.MODE_PRIVATE)
    sp.edit()
        .putString(KEY_PREFILL_PLACE, place)
        .putString(KEY_PREFILL_NOTES, meta.description)
        .putInt(KEY_PREFILL_INT, meta.generalIntensity.coerceIn(1, 5))
        .putString(KEY_PREFILL_FROM_AUDIO, baseName)
        .putBoolean(KEY_PREFILL_PENDING_OPEN, true) // <- para que MainActivity lo detecte
        .apply()
    return true
}

/** Consume el prefill (si existe) para precargar la pantalla Emociones. */
fun consumeEmotionPrefill(context: Context): EmotionPrefill? {
    val sp = context.getSharedPreferences(PREFS_PREFILL, Context.MODE_PRIVATE)
    val from = sp.getString(KEY_PREFILL_FROM_AUDIO, null)
    val hasAny = sp.contains(KEY_PREFILL_PLACE) || sp.contains(KEY_PREFILL_NOTES) || from != null
    if (!hasAny) return null

    val place = sp.getString(KEY_PREFILL_PLACE, "") ?: ""
    val notes = sp.getString(KEY_PREFILL_NOTES, "") ?: ""
    val gi = sp.getInt(KEY_PREFILL_INT, 3)
    val audio = from

    // limpiamos todo salvo el flag de “pendiente” que lo gestiona consumePendingOpenEmotion
    sp.edit()
        .remove(KEY_PREFILL_PLACE)
        .remove(KEY_PREFILL_NOTES)
        .remove(KEY_PREFILL_INT)
        .remove(KEY_PREFILL_FROM_AUDIO)
        .apply()

    return EmotionPrefill(place, notes, gi, audio)
}

/**
 * Devuelve true una vez cuando hay que abrir Emociones automáticamente
 * (por ejemplo, tras elegir “Completar desde audio”).
 */
fun consumePendingOpenEmotion(context: Context): Boolean {
    val sp = context.getSharedPreferences(PREFS_PREFILL, Context.MODE_PRIVATE)
    val pending = sp.getBoolean(KEY_PREFILL_PENDING_OPEN, false)
    if (pending) {
        sp.edit().putBoolean(KEY_PREFILL_PENDING_OPEN, false).apply()
    }
    return pending
}

/**
 * Guarda una emoción usando el mismo “base” que el audio (a_.. → e_..),
 * añade el array "sensations" derivado de notes, y elimina los ficheros
 * del audio original (m4a + json) si existen.
 */
fun saveEmotionEntryUsingAudioBase(context: Context, entry: EmotionEntry, audioBaseName: String): File {
    val base = (if (audioBaseName.startsWith("a_")) "e_" + audioBaseName.substring(2) else "e_$audioBaseName")
        .removeSuffix(".m4a").removeSuffix(".json")
    val out = File(entriesRoot(context), "$base.json")

    val gson = Gson()
    val tree = JsonParser.parseString(gson.toJson(entry)).asJsonObject
    val sensations = splitNotesToSensations(tree.get("notes")?.asString)
    tree.add("sensations", gson.toJsonTree(sensations))
    out.writeText(gson.toJson(tree))

    // borrar el audio y sus metadatos con el base original
    val audioRoot = audioBaseName.removeSuffix(".m4a").removeSuffix(".json")
    File(entriesRoot(context), "$audioRoot.m4a").delete()
    File(entriesRoot(context), "$audioRoot.json").delete()

    return out
}
