package fr.terrapilot.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        HistoriqueEntity::class
    ],
    version = 1,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "terrapilot.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
