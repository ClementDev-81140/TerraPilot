package fr.terrapilot.assistant.data.files

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupResult(val success: Boolean, val message: String, val sha256: String? = null)

object BackupManager {
    fun exportDatabaseToZip(context: Context, destUri: Uri): BackupResult {
        return try {
            val dbFile = context.getDatabasePath("terrapilot.db")
            if (!dbFile.exists()) {
                return BackupResult(false, "Base de données introuvable.")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                ZipOutputStream(out).use { zos ->
                    val bytes = dbFile.readBytes()
                    digest.update(bytes)
                    zos.putNextEntry(ZipEntry("terrapilot.db"))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            } ?: return BackupResult(false, "Impossible d'ouvrir la destination.")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            BackupResult(true, "Sauvegarde exportée avec succès.", hash)
        } catch (e: Exception) {
            BackupResult(false, "Erreur export : ${e.message}")
        }
    }

    fun restoreDatabaseFromZip(context: Context, srcUri: Uri): BackupResult {
        return try {
            val dbFile = context.getDatabasePath("terrapilot.db")
            val temp = File(context.cacheDir, "restore_tmp.db")
            var found = false
            context.contentResolver.openInputStream(srcUri)?.use { inp ->
                ZipInputStream(inp).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "terrapilot.db") {
                            temp.writeBytes(zis.readBytes())
                            found = true
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            if (!found) return BackupResult(false, "Archive invalide : terrapilot.db absent.")
            temp.copyTo(dbFile, overwrite = true)
            temp.delete()
            BackupResult(true, "Restauration effectuée. Redémarrez l'application pour recharger les données.")
        } catch (e: Exception) {
            BackupResult(false, "Erreur restauration : ${e.message}")
        }
    }
}
