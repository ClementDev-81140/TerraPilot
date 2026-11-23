package fr.terrapilot.assistant.data.db

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Porte d'accès centralisée compatible coroutines pour Room.
 * - Permet plusieurs opérations concurrentes hors maintenance.
 * - Bloque toute nouvelle opération dès qu'une maintenance est demandée.
 * - Attend que activeOpsCount == 0 avant de fermer Room et remplacer les fichiers.
 * - Sérialise les maintenances simultanées.
 */
object DatabaseAccessGate {
    private val stateMutex = Mutex()
    private val maintenanceMutex = Mutex()

    private var activeOpsCount = 0
    @Volatile
    private var maintenanceInProgress = false
    private var idleSignal: CompletableDeferred<Unit>? = null

    private val _epoch = MutableStateFlow(0L)
    val epoch: StateFlow<Long> = _epoch

    fun isMaintenanceActive(): Boolean = maintenanceInProgress

    suspend fun <T> withDatabase(context: Context, block: suspend (AppDatabase) -> T): T {
        return withOperation {
            block(AppDatabase.get(context))
        }
    }

    suspend fun <T> withOperation(block: suspend () -> T): T {
        stateMutex.withLock {
            if (maintenanceInProgress) {
                throw IllegalStateException("Opération refusée : maintenance de la base en cours.")
            }
            activeOpsCount++
        }
        try {
            return block()
        } finally {
            stateMutex.withLock {
                activeOpsCount--
                if (activeOpsCount == 0) {
                    idleSignal?.complete(Unit)
                    idleSignal = null
                }
            }
        }
    }

    suspend fun <T> runMaintenance(
        closeDb: () -> Unit = {},
        block: suspend () -> T
    ): T {
        return maintenanceMutex.withLock {
            val waitForIdle: CompletableDeferred<Unit>? = stateMutex.withLock {
                if (maintenanceInProgress) {
                    throw IllegalStateException("Maintenance imbriquée interdite.")
                }
                maintenanceInProgress = true
                if (activeOpsCount > 0) {
                    CompletableDeferred<Unit>().also { idleSignal = it }
                } else {
                    null
                }
            }
            try {
                waitForIdle?.await()
                closeDb()
                return@withLock block()
            } finally {
                stateMutex.withLock {
                    maintenanceInProgress = false
                    _maintenanceState.value = false
                    idleSignal = null
                }
                _epoch.value = _epoch.value + 1
            }
        }
    }
}
