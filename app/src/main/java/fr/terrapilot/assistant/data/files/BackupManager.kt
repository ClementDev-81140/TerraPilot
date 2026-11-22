package fr.terrapilot.assistant.data.files

import android.content.Context
import android.net.Uri
import fr.terrapilot.assistant.data.db.AppDatabase
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupResult(
    val success: Boolean,
    val message: String,
    val sha256: String? = null,
    val filesCount: Int = 0
)

object BackupManager {
    private const val MAX_ENTRY_BYTES = 50L * 1024 * 1024 // 50 Mo max par entrée
    private const val MAX_TOTAL_BYTES = 250L * 1024 * 1024 // 250 Mo max total
    private const val MAX_ENTRIES = 5000

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun isAllowedEntry(name: String): Boolean {
        if (name == "terrapilot.db" || name == "manifest.json") return true
        return name.startsWith("chantier_files/") && !name.contains("..") && !name.startsWith("/")
    }

    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
        maxEntryBytes: Long,
        remainingTotalBytes: Long
    ): Long {
        val buf = ByteArray(8192)
        var entryRead = 0L
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            entryRead += n
            if (entryRead > maxEntryBytes) {
                throw SecurityException("Entrée ZIP trop volumineuse (> $maxEntryBytes octets)")
            }
            if (entryRead > remainingTotalBytes) {
                throw SecurityException("Taille totale décompressée dépassée")
            }
            output.write(buf, 0, n)
        }
        return entryRead
    }

    fun exportDatabaseToZip(context: Context, destUri: Uri): BackupResult {
        return try {
            val dbFile = context.getDatabasePath("terrapilot.db")
            if (!dbFile.exists()) {
                return BackupResult(false, "Base de données introuvable.")
            }
            val manifest = JSONObject()
            val filesJson = JSONObject()
            var count = 0

            context.contentResolver.openOutputStream(destUri)?.use { out ->
                ZipOutputStream(out).use { zos ->
                    val dbBytes = dbFile.readBytes()
                    filesJson.put("terrapilot.db", sha256Hex(dbBytes))
                    zos.putNextEntry(ZipEntry("terrapilot.db"))
                    zos.write(dbBytes)
                    zos.closeEntry()
                    count++

                    val mediaDir = File(context.filesDir, "chantier_files")
                    if (mediaDir.exists()) {
                        mediaDir.walkTopDown().filter { it.isFile }.forEach { f ->
                            val rel = "chantier_files/" + f.relativeTo(mediaDir).path.replace("\\", "/")
                            val b = f.readBytes()
                            filesJson.put(rel, sha256Hex(b))
                            zos.putNextEntry(ZipEntry(rel))
                            zos.write(b)
                            zos.closeEntry()
                            count++
                        }
                    }

                    manifest.put("version", 2)
                    manifest.put("createdAt", System.currentTimeMillis())
                    manifest.put("files", filesJson)
                    val manifestBytes = manifest.toString(2).toByteArray(Charsets.UTF_8)
                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(manifestBytes)
                    zos.closeEntry()
                }
            } ?: return BackupResult(false, "Impossible d'ouvrir la destination.")

            BackupResult(true, "Sauvegarde complète exportée ($count fichiers).", filesJson.optString("terrapilot.db"), count)
        } catch (e: Exception) {
            BackupResult(false, "Erreur export : ${e.message}")
        }
    }

    fun restoreDatabaseFromZip(context: Context, srcUri: Uri): BackupResult {
        val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}").apply { mkdirs() }
        val dbFile = context.getDatabasePath("terrapilot.db")
        val bakFile = File(dbFile.parentFile, "terrapilot.db.bak")

        return try {
            var manifestObj: JSONObject? = null
            var totalRead = 0L
            var entriesCount = 0

            context.contentResolver.openInputStream(srcUri)?.use { inp ->
                ZipInputStream(inp).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        entriesCount++
                        if (entriesCount > MAX_ENTRIES) {
                            return BackupResult(false, "Archive rejetée : trop d'entrées (> $MAX_ENTRIES).")
                        }
                        if (!entry.isDirectory) {
                            if (!isAllowedEntry(entry.name)) {
                                return BackupResult(false, "Archive rejetée : entrée non autorisée (${entry.name}).")
                            }
                            val target = File(stagingDir, entry.name)
                            if (!target.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)) {
                                return BackupResult(false, "Archive rejetée : chemin non sécurisé (${entry.name}).")
                            }
                            target.parentFile?.mkdirs()
                            val written = target.outputStream().use { out ->
                                copyLimited(zis, out, MAX_ENTRY_BYTES, MAX_TOTAL_BYTES - totalRead)
                            }
                            totalRead += written
                            if (entry.name == "manifest.json") {
                                manifestObj = JSONObject(target.readText(Charsets.UTF_8))
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            } ?: return BackupResult(false, "Impossible de lire l'archive.")

            val stagedDb = File(stagingDir, "terrapilot.db")
            if (!stagedDb.exists()) {
                return BackupResult(false, "Archive invalide : terrapilot.db absent.")
            }

            // Vérification stricte du manifeste SHA-256
            val filesManifest = manifestObj?.optJSONObject("files")
                ?: return BackupResult(false, "Archive rejetée : manifeste SHA-256 absent ou invalide.")

            val keys = filesManifest.keys()
            while (keys.hasNext()) {
                val rel = keys.next()
                val expectedHash = filesManifest.getString(rel)
                val f = File(stagingDir, rel)
                if (!f.exists() || sha256Hex(f.readBytes()) != expectedHash) {
                    return BackupResult(false, "Échec vérification intégrité SHA-256 pour $rel.")
                }
            }

            // Fermeture propre de Room et nettoyage WAL/SHM avant remplacement
            try {
                AppDatabase.get(context).close()
            } catch (_: Exception) {}

            if (dbFile.exists()) {
                dbFile.copyTo(bakFile, overwrite = true)
            }
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()

            try {
                stagedDb.copyTo(dbFile, overwrite = true)
                val stagedMedia = File(stagingDir, "chantier_files")
                var restoredCount = 1
                if (stagedMedia.exists()) {
                    val destMedia = File(context.filesDir, "chantier_files").apply { mkdirs() }
                    stagedMedia.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = f.relativeTo(stagedMedia)
                        val dest = File(destMedia, rel.path)
                        dest.parentFile?.mkdirs()
                        f.copyTo(dest, overwrite = true)
                        restoredCount++
                    }
                }
                BackupResult(true, "Restauration sécurisée réussie ($restoredCount fichiers). Redémarrez l'application.", filesCount = restoredCount)
            } catch (e: Exception) {
                // Rollback atomique
                if (bakFile.exists()) {
                    bakFile.copyTo(dbFile, overwrite = true)
                }
                BackupResult(false, "Échec restauration, base précédente restaurée : ${e.message}")
            }
        } catch (e: Exception) {
            BackupResult(false, "Erreur restauration : ${e.message}")
        } finally {
            stagingDir.deleteRecursively()
        }
    }
}
