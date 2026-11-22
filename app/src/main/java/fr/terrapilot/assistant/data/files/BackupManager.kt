package fr.terrapilot.assistant.data.files

import android.content.Context
import android.net.Uri
import fr.terrapilot.assistant.data.db.AppDatabase
import org.json.JSONObject
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
    const val MAX_ENTRY_BYTES = 50L * 1024 * 1024 // 50 Mo max par entrée
    const val MAX_TOTAL_BYTES = 250L * 1024 * 1024 // 250 Mo max total
    const val MAX_ENTRIES = 5000

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(8192)
        file.inputStream().use { inp ->
            while (true) {
                val n = inp.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyAndDigest(input: InputStream, output: OutputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            digest.update(buf, 0, n)
            output.write(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isAllowedEntry(name: String): Boolean {
        if (name == "terrapilot.db" || name == "manifest.json") return true
        return name.startsWith("chantier_files/") && !name.contains("..") && !name.startsWith("/") && !name.contains("\\")
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
        val dbFile = context.getDatabasePath("terrapilot.db")
        val mediaDir = File(context.filesDir, "chantier_files")
        return try {
            val out = context.contentResolver.openOutputStream(destUri)
                ?: return BackupResult(false, "Impossible d'ouvrir la destination.")
            out.use { exportFromFiles(dbFile, mediaDir, it) }
        } catch (e: Exception) {
            BackupResult(false, "Erreur export : ${e.message}")
        }
    }

    fun exportFromFiles(dbFile: File, mediaDir: File, output: OutputStream): BackupResult {
        return try {
            if (!dbFile.exists()) {
                return BackupResult(false, "Base de données introuvable.")
            }
            val manifest = JSONObject()
            val filesJson = JSONObject()
            var count = 0

            ZipOutputStream(output).use { zos ->
                zos.putNextEntry(ZipEntry("terrapilot.db"))
                val dbHash = dbFile.inputStream().use { copyAndDigest(it, zos) }
                zos.closeEntry()
                filesJson.put("terrapilot.db", dbHash)
                count++

                if (mediaDir.exists()) {
                    mediaDir.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = "chantier_files/" + f.relativeTo(mediaDir).path.replace("\\", "/")
                        zos.putNextEntry(ZipEntry(rel))
                        val h = f.inputStream().use { copyAndDigest(it, zos) }
                        zos.closeEntry()
                        filesJson.put(rel, h)
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

            BackupResult(true, "Sauvegarde complète exportée ($count fichiers).", filesJson.optString("terrapilot.db"), count)
        } catch (e: Exception) {
            BackupResult(false, "Erreur export : ${e.message}")
        }
    }

    fun restoreDatabaseFromZip(context: Context, srcUri: Uri): BackupResult {
        val dbFile = context.getDatabasePath("terrapilot.db")
        val mediaDir = File(context.filesDir, "chantier_files")
        val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}")
        return try {
            val inp = context.contentResolver.openInputStream(srcUri)
                ?: return BackupResult(false, "Impossible de lire l'archive.")
            inp.use {
                restoreToFiles(dbFile, mediaDir, stagingDir, it) {
                    AppDatabase.closeAndReset()
                }
            }
        } catch (e: Exception) {
            BackupResult(false, "Erreur restauration : ${e.message}")
        }
    }

    fun restoreToFiles(
        dbFile: File,
        mediaDir: File,
        stagingDir: File,
        input: InputStream,
        maxEntryBytes: Long = MAX_ENTRY_BYTES,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
        maxEntries: Int = MAX_ENTRIES,
        beforeDbReplace: () -> Unit = {}
    ): BackupResult {
        stagingDir.mkdirs()
        val bakDb = File(dbFile.parentFile, "terrapilot.db.bak")
        val bakMedia = File(mediaDir.parentFile, "chantier_files.bak_${System.currentTimeMillis()}")

        return try {
            var manifestObj: JSONObject? = null
            var totalRead = 0L
            var entriesCount = 0
            val seenEntries = mutableSetOf<String>()
            val extractedDataFiles = mutableSetOf<String>()

            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entriesCount++
                    if (entriesCount > maxEntries) {
                        return BackupResult(false, "Archive rejetée : trop d'entrées (> $maxEntries).")
                    }
                    if (!seenEntries.add(entry.name)) {
                        return BackupResult(false, "Archive rejetée : entrée dupliquée (${entry.name}).")
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
                            copyLimited(zis, out, maxEntryBytes, maxTotalBytes - totalRead)
                        }
                        totalRead += written
                        if (entry.name == "manifest.json") {
                            manifestObj = JSONObject(target.readText(Charsets.UTF_8))
                        } else {
                            extractedDataFiles.add(entry.name)
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            val stagedDb = File(stagingDir, "terrapilot.db")
            if (!stagedDb.exists()) {
                return BackupResult(false, "Archive invalide : terrapilot.db absent.")
            }

            val filesManifest = manifestObj?.optJSONObject("files")
                ?: return BackupResult(false, "Archive rejetée : manifeste SHA-256 absent ou invalide.")

            if (!filesManifest.has("terrapilot.db")) {
                return BackupResult(false, "Archive rejetée : hash SHA-256 de terrapilot.db absent du manifeste.")
            }

            val manifestKeys = mutableSetOf<String>()
            val keys = filesManifest.keys()
            while (keys.hasNext()) {
                val rel = keys.next()
                if (!isAllowedEntry(rel) || rel == "manifest.json") {
                    return BackupResult(false, "Manifeste invalide : entrée non autorisée ($rel).")
                }
                manifestKeys.add(rel)
                val expectedHash = filesManifest.getString(rel)
                val f = File(stagingDir, rel)
                if (!f.exists()) {
                    return BackupResult(false, "Manifeste incohérent : fichier $rel absent de l'archive.")
                }
                if (sha256Hex(f) != expectedHash) {
                    return BackupResult(false, "Échec vérification intégrité SHA-256 pour $rel.")
                }
            }

            if (extractedDataFiles != manifestKeys) {
                val missingInManifest = extractedDataFiles - manifestKeys
                return BackupResult(false, "Archive rejetée : fichiers sans hash dans le manifeste ($missingInManifest).")
            }

            // Phase transactionnelle : fermeture Room + sauvegarde de l'état actif
            try {
                beforeDbReplace()
            } catch (_: Exception) {}

            if (dbFile.exists()) {
                dbFile.copyTo(bakDb, overwrite = true)
            }
            if (mediaDir.exists()) {
                mediaDir.copyRecursively(bakMedia, overwrite = true)
            }

            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()

            try {
                dbFile.parentFile?.mkdirs()
                stagedDb.copyTo(dbFile, overwrite = true)

                val stagedMedia = File(stagingDir, "chantier_files")
                if (mediaDir.exists()) {
                    mediaDir.deleteRecursively()
                }
                var restoredCount = 1
                if (stagedMedia.exists()) {
                    mediaDir.mkdirs()
                    stagedMedia.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = f.relativeTo(stagedMedia)
                        val dest = File(mediaDir, rel.path)
                        dest.parentFile?.mkdirs()
                        f.copyTo(dest, overwrite = true)
                        restoredCount++
                    }
                }
                bakDb.delete()
                bakMedia.deleteRecursively()
                BackupResult(true, "Restauration sécurisée réussie ($restoredCount fichiers).", filesCount = restoredCount)
            } catch (e: Exception) {
                // Rollback complet base + médias
                if (bakDb.exists()) {
                    bakDb.copyTo(dbFile, overwrite = true)
                }
                if (bakMedia.exists()) {
                    mediaDir.deleteRecursively()
                    bakMedia.copyRecursively(mediaDir, overwrite = true)
                }
                BackupResult(false, "Échec restauration, état précédent intégralement restauré : ${e.message}")
            }
        } catch (e: Exception) {
            BackupResult(false, "Erreur restauration : ${e.message}")
        } finally {
            stagingDir.deleteRecursively()
            bakMedia.deleteRecursively()
        }
    }
}
