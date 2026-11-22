package fr.terrapilot.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.locks.ReentrantReadWriteLock

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
        private val rwLock = ReentrantReadWriteLock()
        @Volatile
        private var maintenanceInProgress = false
        @Volatile
        private var maintenanceThread: Thread? = null

        private val _epoch = MutableStateFlow(0L)
        val epoch: StateFlow<Long> = _epoch

        fun get(context: Context): AppDatabase {
            if (maintenanceInProgress && Thread.currentThread() == maintenanceThread) {
                throw IllegalStateException("Accès à AppDatabase refusé : maintenance en cours sur ce thread.")
            }
            rwLock.readLock().lock()
            try {
                if (maintenanceInProgress) {
                    throw IllegalStateException("Accès à AppDatabase refusé : maintenance active.")
                }
                return INSTANCE ?: synchronized(this) {
                    INSTANCE ?: Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "terrapilot.db"
                    ).build().also { INSTANCE = it }
                }
            } finally {
                rwLock.readLock().unlock()
            }
        }

        private fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun <T> runWithMaintenanceLock(block: () -> T): T {
            if (maintenanceInProgress && Thread.currentThread() == maintenanceThread) {
                throw IllegalStateException("Maintenance imbriquée interdite sur ce thread.")
            }
            rwLock.writeLock().lock()
            maintenanceInProgress = true
            maintenanceThread = Thread.currentThread()
            try {
                closeAndReset()
                return block()
            } finally {
                maintenanceThread = null
                maintenanceInProgress = false
                _epoch.value = _epoch.value + 1
                rwLock.writeLock().unlock()
            }
        }

        fun <T> runWithReadLock(block: () -> T): T {
            if (maintenanceInProgress && Thread.currentThread() == maintenanceThread) {
                throw IllegalStateException("Lecture/export interdit pendant une maintenance sur ce thread.")
            }
            rwLock.readLock().lock()
            try {
                if (maintenanceInProgress) {
                    throw IllegalStateException("Lecture/export refusé : maintenance active.")
                }
                return block()
            } finally {
                rwLock.readLock().unlock()
            }
        }
    }
}
