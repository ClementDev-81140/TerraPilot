package fr.terrapilot.assistant

import fr.terrapilot.assistant.data.files.BackupManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun createSampleZip(): ByteArray {
        val srcDb = tmp.newFile("src.db").apply { writeText("DB_CONTENT_V2") }
        val srcMedia = tmp.newFolder("src_media")
        File(srcMedia, "photo1.jpg").writeText("PHOTO_BYTES_1")
        val baos = ByteArrayOutputStream()
        val res = BackupManager.exportFromFiles(srcDb, srcMedia, baos)
        assertTrue(res.success)
        assertEquals(2, res.filesCount)
        return baos.toByteArray()
    }

    @Test
    fun archive_valide_avec_base_et_medias() {
        val zipBytes = createSampleZip()
        val destDb = File(tmp.root, "dest.db").apply { writeText("OLD_DB") }
        val destMedia = File(tmp.root, "dest_media").apply {
            mkdirs()
            File(this, "old.jpg").writeText("OLD_PHOTO")
        }
        val staging = File(tmp.root, "staging1")
        val res = BackupManager.restoreToFiles(destDb, destMedia, staging, ByteArrayInputStream(zipBytes))
        assertTrue(res.success)
        assertEquals("DB_CONTENT_V2", destDb.readText())
        assertEquals("PHOTO_BYTES_1", File(destMedia, "photo1.jpg").readText())
        assertFalse(File(destMedia, "old.jpg").exists())
    }

    @Test
    fun manifeste_absent_refuse_et_preserve_anciennes_donnees() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("terrapilot.db"))
            zos.write("FAKE_DB".toByteArray())
            zos.closeEntry()
        }
        val destDb = File(tmp.root, "dest_no_man.db").apply { writeText("KEEP_ME") }
        val destMedia = File(tmp.root, "dest_no_man_media").apply { mkdirs() }
        val res = BackupManager.restoreToFiles(destDb, destMedia, File(tmp.root, "st2"), ByteArrayInputStream(baos.toByteArray()))
        assertFalse(res.success)
        assertEquals("KEEP_ME", destDb.readText())
    }

    @Test
    fun manifeste_sans_hash_base_refuse() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("terrapilot.db"))
            zos.write("FAKE_DB".toByteArray())
            zos.closeEntry()
            val man = JSONObject().put("files", JSONObject())
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(man.toString().toByteArray())
            zos.closeEntry()
        }
        val destDb = File(tmp.root, "dest_no_db_hash.db").apply { writeText("KEEP_ME") }
        val res = BackupManager.restoreToFiles(destDb, File(tmp.root, "m3"), File(tmp.root, "st3"), ByteArrayInputStream(baos.toByteArray()))
        assertFalse(res.success)
        assertEquals("KEEP_ME", destDb.readText())
    }

    @Test
    fun fichier_modifie_apres_manifeste_refuse() {
        val validZip = createSampleZip()
        val tampered = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(validZip)).use { zis ->
            ZipOutputStream(tampered).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    zos.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "chantier_files/photo1.jpg") {
                        zos.write("TAMPERED_PHOTO".toByteArray())
                    } else {
                        zos.write(zis.readBytes())
                    }
                    zos.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        val destDb = File(tmp.root, "dest_tamper.db").apply { writeText("KEEP_ME") }
        val res = BackupManager.restoreToFiles(destDb, File(tmp.root, "m4"), File(tmp.root, "st4"), ByteArrayInputStream(tampered.toByteArray()))
        assertFalse(res.success)
        assertEquals("KEEP_ME", destDb.readText())
    }

    @Test
    fun entree_non_autorisee_refusee() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("../evil.txt"))
            zos.write("EVIL".toByteArray())
            zos.closeEntry()
        }
        val destDb = File(tmp.root, "dest_evil.db").apply { writeText("KEEP_ME") }
        val res = BackupManager.restoreToFiles(destDb, File(tmp.root, "m5"), File(tmp.root, "st5"), ByteArrayInputStream(baos.toByteArray()))
        assertFalse(res.success)
        assertEquals("KEEP_ME", destDb.readText())
    }

    @Test
    fun depassement_limites_refuse() {
        val zipBytes = createSampleZip()
        val destDb = File(tmp.root, "dest_lim.db").apply { writeText("KEEP_ME") }
        val res = BackupManager.restoreToFiles(
            destDb,
            File(tmp.root, "m6"),
            File(tmp.root, "st6"),
            ByteArrayInputStream(zipBytes),
            maxEntryBytes = 5L
        )
        assertFalse(res.success)
        assertEquals("KEEP_ME", destDb.readText())
    }
}
