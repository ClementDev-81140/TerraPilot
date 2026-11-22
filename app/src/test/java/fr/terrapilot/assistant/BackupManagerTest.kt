package fr.terrapilot.assistant

import fr.terrapilot.assistant.data.files.BackupFileOps
import fr.terrapilot.assistant.data.files.BackupManager
import fr.terrapilot.assistant.data.files.DefaultBackupFileOps
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

    @Test
    fun echec_fermeture_room_annule_restauration() {
        val zipBytes = createSampleZip()
        val destDb = File(tmp.root, "dest_room_fail.db").apply { writeText("KEEP_ME") }
        val res = BackupManager.restoreToFiles(
            destDb,
            File(tmp.root, "m7"),
            File(tmp.root, "st7"),
            ByteArrayInputStream(zipBytes),
            beforeDbReplace = { throw IllegalStateException("Room lock") }
        )
        assertFalse(res.success)
        assertTrue(res.message.contains("échec fermeture base active"))
        assertEquals("KEEP_ME", destDb.readText())
    }

    @Test
    fun echec_copie_media_rollback_complet_avec_ancien_dossier() {
        val zipBytes = createSampleZip()
        val destDb = File(tmp.root, "dest_rb1.db").apply { writeText("OLD_DB") }
        val destMedia = File(tmp.root, "dest_rb1_media").apply {
            mkdirs()
            File(this, "old.jpg").writeText("OLD_PHOTO")
        }
        val res = BackupManager.restoreToFiles(
            destDb,
            destMedia,
            File(tmp.root, "st8"),
            ByteArrayInputStream(zipBytes),
            fileOps = object : BackupFileOps by DefaultBackupFileOps {
                override fun copyFile(src: File, dst: File) {
                    if (src.name == "photo1.jpg") throw java.io.IOException("Disk full")
                    DefaultBackupFileOps.copyFile(src, dst)
                }
            }
        )
        assertFalse(res.success)
        assertTrue(res.message.contains("intégralement restauré"))
        assertEquals("OLD_DB", destDb.readText())
        assertEquals("OLD_PHOTO", File(destMedia, "old.jpg").readText())
        assertFalse(File(destMedia, "photo1.jpg").exists())
    }

    @Test
    fun echec_copie_media_rollback_sans_ancien_dossier() {
        val zipBytes = createSampleZip()
        val destDb = File(tmp.root, "dest_rb2.db").apply { writeText("OLD_DB") }
        val destMedia = File(tmp.root, "dest_rb2_media_absent")
        assertFalse(destMedia.exists())
        val res = BackupManager.restoreToFiles(
            destDb,
            destMedia,
            File(tmp.root, "st9"),
            ByteArrayInputStream(zipBytes),
            fileOps = object : BackupFileOps by DefaultBackupFileOps {
                override fun copyFile(src: File, dst: File) {
                    if (src.name == "photo1.jpg") throw java.io.IOException("Disk full")
                    DefaultBackupFileOps.copyFile(src, dst)
                }
            }
        )
        assertFalse(res.success)
        assertEquals("OLD_DB", destDb.readText())
        assertFalse(destMedia.exists())
    }

    @Test
    fun echec_rollback_conserve_sauvegardes_et_retourne_chemin() {
        val zipBytes = createSampleZip()
        val dbDir = tmp.newFolder("db_rb_fail")
        val destDb = File(dbDir, "terrapilot.db").apply { writeText("OLD_DB") }
        val destMedia = File(dbDir, "chantier_files").apply { mkdirs() }

        val res = BackupManager.restoreToFiles(
            destDb,
            destMedia,
            File(tmp.root, "st10"),
            ByteArrayInputStream(zipBytes),
            fileOps = object : BackupFileOps by DefaultBackupFileOps {
                override fun copyFile(src: File, dst: File) {
                    if (src.name == "photo1.jpg") throw java.io.IOException("Disk full")
                    if (src.name == "terrapilot.db.bak") throw java.io.IOException("Rollback DB fail")
                    DefaultBackupFileOps.copyFile(src, dst)
                }
            }
        )
        assertFalse(res.success)
        assertTrue(res.message.contains("rollback incomplet"))
        assertTrue(res.message.contains("terrapilot_restore_bak_"))
        val bakDirs = dbDir.listFiles { f -> f.isDirectory && f.name.startsWith("terrapilot_restore_bak_") }
        assertNotNull(bakDirs)
        assertEquals(1, bakDirs!!.size)
        assertEquals("OLD_DB", File(bakDirs[0], "terrapilot.db.bak").readText())
    }

    @Test
    fun ancienne_sauvegarde_secours_jamais_ecrasee() {
        val zipBytes = createSampleZip()
        val dbDir = tmp.newFolder("db_keep_old_bak")
        val existingBak = File(dbDir, "terrapilot.db.bak").apply { writeText("PRECIOUS_OLD_BAK") }
        val destDb = File(dbDir, "terrapilot.db").apply { writeText("OLD_DB") }
        val destMedia = File(dbDir, "chantier_files").apply { mkdirs() }

        val res = BackupManager.restoreToFiles(destDb, destMedia, File(tmp.root, "st11"), ByteArrayInputStream(zipBytes))
        assertTrue(res.success)
        assertTrue(existingBak.exists())
        assertEquals("PRECIOUS_OLD_BAK", existingBak.readText())
    }

    @Test
    fun wal_non_vide_annule_restauration() {
        val zipBytes = createSampleZip()
        val dbDir = tmp.newFolder("db_wal")
        val destDb = File(dbDir, "terrapilot.db").apply { writeText("OLD_DB") }
        File(dbDir, "terrapilot.db-wal").writeText("UNCHECKPOINTED_TX")
        val res = BackupManager.restoreToFiles(destDb, File(dbDir, "m"), File(tmp.root, "st12"), ByteArrayInputStream(zipBytes))
        assertFalse(res.success)
        assertTrue(res.message.contains("WAL résiduel détecté"))
        assertEquals("OLD_DB", destDb.readText())
    }

    @Test
    fun echec_suppression_shm_annule_restauration() {
        val zipBytes = createSampleZip()
        val dbDir = tmp.newFolder("db_shm")
        val destDb = File(dbDir, "terrapilot.db").apply { writeText("OLD_DB") }
        val res = BackupManager.restoreToFiles(
            destDb,
            File(dbDir, "m"),
            File(tmp.root, "st13"),
            ByteArrayInputStream(zipBytes),
            fileOps = object : BackupFileOps by DefaultBackupFileOps {
                override fun deleteFile(file: File): Boolean {
                    if (file.name.endsWith("-shm")) return false
                    return DefaultBackupFileOps.deleteFile(file)
                }
            }
        )
        assertFalse(res.success)
        assertTrue(res.message.contains("WAL/SHM"))
        assertEquals("OLD_DB", destDb.readText())
    }

    @Test
    fun echec_creation_backup_dir_annule_restauration() {
        val zipBytes = createSampleZip()
        val dbDir = tmp.newFolder("db_mkdir_fail")
        val destDb = File(dbDir, "terrapilot.db").apply { writeText("OLD_DB") }
        val res = BackupManager.restoreToFiles(
            destDb,
            File(dbDir, "m"),
            File(tmp.root, "st14"),
            ByteArrayInputStream(zipBytes),
            fileOps = object : BackupFileOps by DefaultBackupFileOps {
                override fun createUniqueDir(dir: File): Boolean = false
            }
        )
        assertFalse(res.success)
        assertTrue(res.message.contains("répertoire de sauvegarde unique"))
        assertEquals("OLD_DB", destDb.readText())
    }

    @Test
    fun echec_suppression_backup_dir_apres_succes_signale_chemin() {
        val zipBytes = createSampleZip()
        val dbDir = tmp.newFolder("db_del_bak_ok")
        val destDb = File(dbDir, "terrapilot.db").apply { writeText("OLD_DB") }
        val res = BackupManager.restoreToFiles(
            destDb,
            File(dbDir, "m"),
            File(tmp.root, "st15"),
            ByteArrayInputStream(zipBytes),
            fileOps = object : BackupFileOps by DefaultBackupFileOps {
                override fun deleteDir(dir: File): Boolean {
                    if (dir.name.startsWith("terrapilot_restore_bak_")) return false
                    return DefaultBackupFileOps.deleteDir(dir)
                }
            }
        )
        assertTrue(res.success)
        assertTrue(res.message.contains("nettoyage incomplet"))
        assertTrue(res.message.contains("terrapilot_restore_bak_"))
    }

    @Test
    fun echec_suppression_wal_annule_restauration() {
        val zipBytes = createSampleZip()
        val dbDir = tmp.newFolder("db_wal_del_fail")
        val destDb = File(dbDir, "terrapilot.db").apply { writeText("OLD_DB") }
        val res = BackupManager.restoreToFiles(
            destDb,
            File(dbDir, "m"),
            File(tmp.root, "st16"),
            ByteArrayInputStream(zipBytes),
            fileOps = object : BackupFileOps by DefaultBackupFileOps {
                override fun deleteFile(file: File): Boolean {
                    if (file.name.endsWith("-wal")) return false
                    return DefaultBackupFileOps.deleteFile(file)
                }
            }
        )
        assertFalse(res.success)
        assertTrue(res.message.contains("WAL/SHM"))
        assertEquals("OLD_DB", destDb.readText())
    }

    @Test
    fun echec_suppression_backup_dir_apres_rollback_signale_chemin() {
        val zipBytes = createSampleZip()
        val dbDir = tmp.newFolder("db_rb_del_bak_fail")
        val destDb = File(dbDir, "terrapilot.db").apply { writeText("OLD_DB") }
        val res = BackupManager.restoreToFiles(
            destDb,
            File(dbDir, "m"),
            File(tmp.root, "st17"),
            ByteArrayInputStream(zipBytes),
            fileOps = object : BackupFileOps by DefaultBackupFileOps {
                override fun copyFile(src: File, dst: File) {
                    if (src.name == "photo1.jpg") throw java.io.IOException("Disk full")
                    DefaultBackupFileOps.copyFile(src, dst)
                }
                override fun deleteDir(dir: File): Boolean {
                    if (dir.name.startsWith("terrapilot_restore_bak_")) return false
                    return DefaultBackupFileOps.deleteDir(dir)
                }
            }
        )
        assertFalse(res.success)
        assertTrue(res.message.contains("sauvegardes temporaires conservées"))
        assertTrue(res.message.contains("terrapilot_restore_bak_"))
        assertEquals("OLD_DB", destDb.readText())
    }

    @Test
    fun maintenance_lock_serialise_et_reinitialise_apres_exception() {
        try {
            fr.terrapilot.assistant.data.db.AppDatabase.runWithMaintenanceLock<Unit> {
                throw RuntimeException("Erreur simulée")
            }
            fail("Une exception était attendue")
        } catch (_: RuntimeException) {
        }
        val ok = fr.terrapilot.assistant.data.db.AppDatabase.runWithReadLock { true }
        assertTrue(ok)
    }

    @Test
    fun maintenance_imbriquee_refusee() {
        var nestedRefused = false
        fr.terrapilot.assistant.data.db.AppDatabase.runWithMaintenanceLock {
            try {
                fr.terrapilot.assistant.data.db.AppDatabase.runWithMaintenanceLock { }
            } catch (e: IllegalStateException) {
                nestedRefused = e.message?.contains("imbriquée") == true
            }
        }
        assertTrue(nestedRefused)
    }

    @Test
    fun export_depuis_thread_proprietaire_pendant_maintenance_refuse() {
        var readRefused = false
        fr.terrapilot.assistant.data.db.AppDatabase.runWithMaintenanceLock {
            try {
                fr.terrapilot.assistant.data.db.AppDatabase.runWithReadLock { }
            } catch (e: IllegalStateException) {
                readRefused = e.message?.contains("interdit") == true
            }
        }
        assertTrue(readRefused)
    }
}
