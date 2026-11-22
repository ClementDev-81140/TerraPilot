package fr.terrapilot.assistant.data.files

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
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
    private const val MAX_ENTRY_BYTES = 100L * 1024 * 1024 // 100 Mo max par fichier

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
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
                    // 1. Base de données
                    val dbBytes = dbFile.readBytes()
                    filesJson.put("terrapilot.db", sha256Hex(dbBytes))
                    zos.putNextEntry(ZipEntry("terrapilot.db"))
                    zos.write(dbBytes)
                    zos.closeEntry()
                    count++

                    // 2. Fichiers locaux (photos, plans PDF, images)
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

                    // 3. Manifeste SHA-256
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
        return try {
            var manifestObj: JSONObject? = null
            var restoredCount = 0

            context.contentResolver.openInputStream(srcUri)?.use { inp ->
                ZipInputStream(inp).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // Protection Zip Slip
                            val target = File(stagingDir, entry.name)
                            if (!target.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)) {
                                return BackupResult(false, "Archive rejetée : chemin non sécurisé (${entry.name}).")
                            }
                            target.parentFile?.mkdirs()
                            val bytes = zis.readBytes()
                            if (bytes.size > MAX_ENTRY_BYTES) {
                                return BackupResult(false, "Archive rejetée : fichier trop volumineux (${entry.name}).")
                            }
                            target.writeBytes(bytes)
                            if (entry.name == "manifest.json") {
                                manifestObj = JSONObject(bytes.toString(Charsets.UTF_8))
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

            // Vérification d'intégrité SHA-256 si manifeste présent
            val filesManifest = manifestObj?.optJSONObject("files")
            if (filesManifest != null) {
                val keys = filesManifest.keys()
                while (keys.hasNext()) {
                    val rel = keys.next()
                    val expectedHash = filesManifest.getString(rel)
                    val f = File(stagingDir, rel)
                    if (!f.exists() || sha256Hex(f.readBytes()) != expectedHash) {
                        return BackupResult(false, "Échec vérification intégrité SHA-256 pour $rel.")
                    }
                }
            }

            // Sauvegarde de sécurité de la base active avant remplacement atomique
            val dbFile = context.getDatabasePath("terrapilot.db")
            val bakFile = File(dbFile.parentFile, "terrapilot.db.bak")
            if (dbFile.exists()) {
                dbFile.copyTo(bakFile, overwrite = true)
            }
            stagedDb.copyTo(dbFile, overwrite = true)
            restoredCount++

            // Restauration des fichiers médias
            val stagedMedia = File(stagingDir, "chantier_files")
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
            BackupResult(false, "Erreur restauration : ${e.message}")
        } finally {
            stagingDir.deleteRecursively()
        }
    }
}
