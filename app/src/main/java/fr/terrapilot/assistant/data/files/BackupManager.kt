package fr.terrapilot.assistant.data.files

import android.content.Context
import android.net.Uri
import fr.terrapilot.assistant.data.db.AppDatabase
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupResult(
    val success: Boolean,
    val message: String,
    val sha256: String? = null,
    val filesCount: Int = 0
)

interface BackupFileOps {
    fun createUniqueDir(dir: File): Boolean
    fun copyFile(src: File, dst: File)
    fun copyDir(src: File, dst: File)
    fun deleteFile(file: File): Boolean
    fun deleteDir(dir: File): Boolean
}

object DefaultBackupFileOps : BackupFileOps {
    override fun createUniqueDir(dir: File): Boolean {
        return try {
            dir.parentFile?.mkdirs()
            java.nio.file.Files.createDirectory(dir.toPath())
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun copyFile(src: File, dst: File) {
        src.copyTo(dst, overwrite = true)
    }

    override fun copyDir(src: File, dst: File) {
        if (!src.copyRecursively(dst, overwrite = true)) {
            throw java.io.IOException("Échec copie répertoire ${src.name}")
        }
    }

    override fun deleteFile(file: File): Boolean = !file.exists() || file.delete()

    override fun deleteDir(dir: File): Boolean = !dir.exists() || dir.deleteRecursively()
}

object BackupManager {
    const val MAX_ENTRY_BYTES = 50L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 250L * 1024 * 1024
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
            AppDatabase.runWithReadLock {
                val out = context.contentResolver.openOutputStream(destUri)
                    ?: return@runWithReadLock BackupResult(false, "Impossible d'ouvrir la destination.")
                out.use { exportFromFiles(dbFile, mediaDir, it) }
            }
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
            AppDatabase.runWithMaintenanceLock {
                val inp = context.contentResolver.openInputStream(srcUri)
                    ?: return@runWithMaintenanceLock BackupResult(false, "Impossible de lire l'archive.")
                inp.use {
                    restoreToFiles(dbFile, mediaDir, stagingDir, it)
                }
            }
        } catch (e: Exception) {
            BackupResult(false, "Erreur restauration : ${e.message}")
        }
    }

    internal fun restoreToFiles(
        dbFile: File,
        mediaDir: File,
        stagingDir: File,
        input: InputStream,
        maxEntryBytes: Long = MAX_ENTRY_BYTES,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
        maxEntries: Int = MAX_ENTRIES,
        beforeDbReplace: () -> Unit = {},
        fileOps: BackupFileOps = DefaultBackupFileOps
    ): BackupResult {
        stagingDir.mkdirs()
        val uniqueId = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val backupDir = File(dbFile.parentFile ?: stagingDir.parentFile, "terrapilot_restore_bak_$uniqueId")
        val bakDb = File(backupDir, "terrapilot.db.bak")
        val bakMedia = File(backupDir, "chantier_files.bak")

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

            try {
                beforeDbReplace()
            } catch (e: Exception) {
                return BackupResult(false, "Restauration annulée : échec fermeture base active (${e.message}).")
            }

            // Option A (stratégie conservatrice) : si un WAL résiduel non vide subsiste, ne jamais le supprimer.
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            if (walFile.exists() && walFile.length() > 0L) {
                return BackupResult(false, "WAL résiduel détecté, état SQLite non confirmé, restauration annulée.")
            }
            if (!fileOps.deleteFile(walFile) || !fileOps.deleteFile(shmFile)) {
                return BackupResult(false, "Restauration annulée : impossible de nettoyer les fichiers SQLite WAL/SHM.")
            }

            if (!fileOps.createUniqueDir(backupDir)) {
                return BackupResult(false, "Restauration annulée : impossible de créer un répertoire de sauvegarde unique (${backupDir.absolutePath}).")
            }

            val hadOldDb = dbFile.exists()
            val hadOldMedia = mediaDir.exists()
            try {
                if (hadOldDb) fileOps.copyFile(dbFile, bakDb)
                if (hadOldMedia) fileOps.copyDir(mediaDir, bakMedia)
            } catch (e: Exception) {
                return BackupResult(false, "Restauration annulée : échec création sauvegarde (${e.message}). Fichiers conservés dans : ${backupDir.absolutePath}")
            }

            try {
                dbFile.parentFile?.mkdirs()
                fileOps.copyFile(stagedDb, dbFile)

                val stagedMedia = File(stagingDir, "chantier_files")
                if (mediaDir.exists() && !fileOps.deleteDir(mediaDir)) {
                    throw java.io.IOException("Impossible de supprimer l'ancien dossier média.")
                }
                var restoredCount = 1
                if (stagedMedia.exists()) {
                    mediaDir.mkdirs()
                    stagedMedia.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = f.relativeTo(stagedMedia)
                        val dest = File(mediaDir, rel.path)
                        dest.parentFile?.mkdirs()
                        fileOps.copyFile(f, dest)
                        restoredCount++
                    }
                }
                val cleanupOk = fileOps.deleteDir(backupDir)
                val msg = if (cleanupOk) {
                    "Restauration sécurisée réussie ($restoredCount fichiers)."
                } else {
                    "Restauration réussie ($restoredCount fichiers), mais nettoyage incomplet : sauvegardes temporaires conservées dans ${backupDir.absolutePath}"
                }
                BackupResult(true, msg, filesCount = restoredCount)
            } catch (e: Exception) {
                var rollbackOk = true
                try {
                    if (hadOldDb) {
                        fileOps.copyFile(bakDb, dbFile)
                    } else if (!fileOps.deleteFile(dbFile)) {
                        rollbackOk = false
                    }
                    if (hadOldMedia) {
                        if (!fileOps.deleteDir(mediaDir)) rollbackOk = false
                        else fileOps.copyDir(bakMedia, mediaDir)
                    } else if (!fileOps.deleteDir(mediaDir)) {
                        rollbackOk = false
                    }
                } catch (_: Exception) {
                    rollbackOk = false
                }

                if (rollbackOk) {
                    val cleanupOk = fileOps.deleteDir(backupDir)
                    val msg = if (cleanupOk) {
                        "Échec restauration, état précédent intégralement restauré : ${e.message}"
                    } else {
                        "Échec restauration, état précédent restauré, mais sauvegardes temporaires conservées dans : ${backupDir.absolutePath}"
                    }
                    BackupResult(false, msg)
                } else {
                    BackupResult(
                        false,
                        "Échec restauration ET rollback incomplet. Sauvegardes conservées dans : ${backupDir.absolutePath}"
                    )
                }
            }
        } catch (e: Exception) {
            BackupResult(false, "Erreur restauration : ${e.message}")
        } finally {
            stagingDir.deleteRecursively()
        }
    }
}
