package com.example.emotionapp.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/* ========= Utilidades locales ========= */

private fun entriesDir(context: Context): File {
    val dir = File(context.filesDir, "entries")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun File.safeName(): String = this.name

/* ========= Tipos ligeros para Gestor ========= */

data class BackupInfo(
    val name: String,
    val uri: Uri? = null,
    val sizeBytes: Long = 0L,
    val modifiedMs: Long = 0L
) {
    // Alias para compatibilidad con GestorScreen (usa .lastModified)
    val lastModified: Long get() = modifiedMs
}
// Compat: algunos sitios lo llaman como función: backup.lastModified()
fun BackupInfo.lastModified(): Long = this.modifiedMs

/* ========= 1) Listar copias locales (backups) ========= */

fun listLocalBackups(context: Context): List<BackupInfo> {
    val out = mutableListOf<BackupInfo>()
    val candidates = listOf(context.filesDir, entriesDir(context))
    candidates.forEach { base ->
        base.listFiles { f -> f.isFile && f.name.endsWith(".zip", ignoreCase = true) }?.forEach { f ->
            out += BackupInfo(
                name = f.name,
                uri = Uri.fromFile(f),
                sizeBytes = f.length(),
                modifiedMs = f.lastModified()
            )
        }
    }
    return out.sortedByDescending { it.modifiedMs }
}

/* ========= 2) Importar desde un ZIP ========= */

fun importEntriesFromLocalZip(context: Context, zipUri: Uri): Int {
    val target = entriesDir(context)
    var count = 0

    fun unzipFromStream(buffered: BufferedInputStream) {
        ZipInputStream(buffered).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val accept = !entry.isDirectory &&
                        (name.endsWith(".json", true) || name.endsWith(".m4a", true))
                if (accept) {
                    val outFile = File(target, File(name).name)
                    outFile.outputStream().use { fos ->
                        val buf = ByteArray(8 * 1024)
                        var r: Int
                        while (zis.read(buf).also { r = it } > 0) fos.write(buf, 0, r)
                    }
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    runCatching {
        context.contentResolver.openInputStream(zipUri)?.use { ins ->
            unzipFromStream(BufferedInputStream(ins))
        } ?: run {
            val f = File(zipUri.path ?: "")
            if (f.exists()) {
                FileInputStream(f).use { fis ->
                    unzipFromStream(BufferedInputStream(fis))
                }
            }
        }
    }

    return count
}

/* Sobrecarga para llamadas que pasan BackupInfo en vez de Uri */
fun importEntriesFromLocalZip(context: Context, backup: BackupInfo): Int {
    val u = backup.uri ?: return 0
    return importEntriesFromLocalZip(context, u)
}

/* ========= 3) Exportar un subconjunto a ZIP =========
   Firma "SAF": (Context, Uri, Collection<String>)  -> escribe en el Uri
*/
fun exportEntriesToLocalZipSubset(
    context: Context,
    targetZipUri: Uri,
    names: Collection<String>
): Int {
    val dir = entriesDir(context)
    var added = 0
    val cr: ContentResolver = context.contentResolver

    cr.openOutputStream(targetZipUri)?.use { baseOut ->
        BufferedOutputStream(baseOut).use { bos ->
            ZipOutputStream(bos).use { zos ->
                names.forEach { n ->
                    val f = File(dir, n)
                    if (!f.exists() || !f.isFile) return@forEach
                    f.inputStream().use { fis ->
                        zos.putNextEntry(ZipEntry(f.safeName()))
                        val buf = ByteArray(8 * 1024)
                        var r: Int
                        while (fis.read(buf).also { r = it } > 0) zos.write(buf, 0, r)
                        zos.closeEntry()
                        added++
                    }
                    val maybeAudio = File(dir, f.nameWithoutExtension + ".m4a")
                    if (maybeAudio.exists()) {
                        maybeAudio.inputStream().use { fis ->
                            zos.putNextEntry(ZipEntry(maybeAudio.safeName()))
                            val buf = ByteArray(8 * 1024)
                            var r: Int
                            while (fis.read(buf).also { r = it } > 0) zos.write(buf, 0, r)
                            zos.closeEntry()
                            added++
                        }
                    }
                }
            }
        }
    }
    return added
}

/* Sobrecarga "conveniente" para el patrón que está usando tu GestorScreen:
   (Context, Collection<String>, String) -> crea un ZIP en /files con ese nombre.
   Devuelve el nº de elementos añadidos. */
fun exportEntriesToLocalZipSubset(
    context: Context,
    names: Collection<String>,
    targetDisplayName: String
): Int {
    val zipName = if (targetDisplayName.lowercase(Locale.getDefault()).endsWith(".zip"))
        targetDisplayName
    else
        "$targetDisplayName.zip"

    val outFile = File(context.filesDir, zipName)
    val uri = Uri.fromFile(outFile)
    // Reutilizamos la implementación SAF pasando el Uri de fichero
    return exportEntriesToLocalZipSubset(context, uri, names)
}

/* ========= 4) Copiar un resumen al portapapeles ========= */

fun copySummaryToClipboardFor(
    context: Context,
    selectedNames: Collection<String>
): String {
    val dir = entriesDir(context)
    val lines = selectedNames.mapNotNull { name ->
        val f = File(dir, name)
        if (!f.exists() || !f.isFile || !name.endsWith(".json", true)) return@mapNotNull null
        val text = runCatching { f.readText() }.getOrDefault("{}")
        val j = runCatching { JSONObject(text) }.getOrNull() ?: JSONObject()

        val place = j.optString("place", j.optString("lugar", ""))
        val people = j.optString("people", "")
        val gi = j.optInt("generalIntensity", j.optInt("general_intensity", -1))
        val emotions = j.optJSONArray("emotions")
        val emoStr = buildString {
            if (emotions != null) {
                for (i in 0 until emotions.length()) {
                    val e = emotions.optJSONObject(i) ?: continue
                    val label = e.optString("label", e.optString("key", ""))
                    val intensity = e.optInt("intensity", -1)
                    if (isNotEmpty()) append(", ")
                    append(if (intensity >= 0) "$label($intensity)" else label)
                }
            }
        }
        val base = f.nameWithoutExtension
        val stamp = base.takeLast(13).takeIf { it.matches(Regex("\\d{8}_\\d{4}")) } ?: ""
        val head = listOfNotNull(
            if (place.isNotBlank()) "Lugar: $place" else null,
            if (people.isNotBlank()) "Personas: $people" else null,
            if (gi >= 0) "Intensidad: $gi" else null,
            if (emoStr.isNotBlank()) "Emos: $emoStr" else null,
            if (stamp.isNotBlank()) "Cuando: $stamp" else null
        ).joinToString(" · ")

        "- ${f.name} → $head"
    }

    val result = if (lines.isEmpty()) "Sin elementos seleccionados." else lines.joinToString("\n")
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Resumen emociones", result))
    return result
}
