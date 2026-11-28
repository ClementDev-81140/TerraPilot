package fr.terrapilot.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChantierEntity::class,
        NiveauEntity::class,
        PenteEntity::class,
        VolumeEntity::class,
        ImplantationEntity::class,
        RelevePointEntity::class,
        PlanEntity::class,
        ReseauEntity::class,
        PhotoEntity::class,
        HistoriqueEntity::class,
        MapFeatureEntity::class,
        OfflineAreaEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chantierDao(): ChantierDao
    abstract fun niveauDao(): NiveauDao
    abstract fun penteDao(): PenteDao
    abstract fun volumeDao(): VolumeDao
    abstract fun implantationDao(): ImplantationDao
    abstract fun releveDao(): ReleveDao
    abstract fun planDao(): PlanDao
    abstract fun reseauDao(): ReseauDao
    abstract fun photoDao(): PhotoDao
    abstract fun historiqueDao(): HistoriqueDao
    abstract fun mapFeatureDao(): MapFeatureDao
    abstract fun offlineAreaDao(): OfflineAreaDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `map_features` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `chantierId` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `targetAltitudeM` REAL,
                        `geometryJson` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_map_features_chantierId` ON `map_features` (`chantierId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_areas` (
                        `mapLibreRegionId` INTEGER NOT NULL,
                        `chantierId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `west` REAL NOT NULL,
                        `south` REAL NOT NULL,
                        `east` REAL NOT NULL,
                        `north` REAL NOT NULL,
                        `minZoom` REAL NOT NULL,
                        `maxZoom` REAL NOT NULL,
                        `completedResources` INTEGER NOT NULL,
                        `requiredResources` INTEGER NOT NULL,
                        `downloadedBytes` INTEGER NOT NULL,
                        `isComplete` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`mapLibreRegionId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_areas_chantierId` ON `offline_areas` (`chantierId`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal fun get(context: Context): AppDatabase {
            if (DatabaseAccessGate.isMaintenanceActive()) {
                throw IllegalStateException("Accès à AppDatabase refusé : maintenance en cours.")
            }
            return INSTANCE ?: synchronized(this) {
                if (DatabaseAccessGate.isMaintenanceActive()) {
                    throw IllegalStateException("Accès à AppDatabase refusé : maintenance en cours.")
                }
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "terrapilot.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        internal fun closeAndReset() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } finally {
                    INSTANCE = null
                }
            }
        }
    }
}
