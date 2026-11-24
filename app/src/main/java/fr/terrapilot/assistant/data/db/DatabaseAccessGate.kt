package fr.terrapilot.assistant.data.db

import android.content.Context
import kotlinx.coroutines.AbstractCoroutineContextElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Porte d'accès centralisée compatible coroutines pour Room.
 *
 * Les collectes Room sont suspendues pendant une maintenance. Les opérations ponctuelles
 * déjà commencées sont attendues ; les nouvelles écritures/lectures attendent la fin de la
 * maintenance au lieu d'échouer dans les coroutines de l'interface.
 */
object DatabaseAccessGate {
    private val stateMutex = Mutex()
    private val maintenanceMutex = Mutex()

    private var activeOpsCount = 0
    @Volatile
    private var maintenanceInProgress = false
    private var idleSignal: CompletableDeferred<Unit>? = null
    private var maintenanceFinished: CompletableDeferred<Unit>? = null

    private val _epoch = MutableStateFlow(0L)
    val epoch: StateFlow<Long> = _epoch

    private val _maintenanceState = MutableStateFlow(false)
    val maintenanceState: StateFlow<Boolean> = _maintenanceState

    private class MaintenanceContext :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<MaintenanceContext>
    }

    private class OperationContext :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<OperationContext>
    }

    fun isMaintenanceActive(): Boolean = maintenanceInProgress

    suspend fun <T> withDatabase(context: Context, block: suspend (AppDatabase) -> T): T =
        withOperation {
            block(AppDatabase.get(context))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observeFlow(context: Context, block: (AppDatabase) -> Flow<T>): Flow<T> =
        maintenanceState.flatMapLatest { inMaintenance ->
            if (inMaintenance) {
                emptyFlow()
            } else {
                flow {
                    withOperation {
                        emitAll(block(AppDatabase.get(context)))
                    }
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observeFlowWithProvider(block: () -> Flow<T>): Flow<T> =
        maintenanceState.flatMapLatest { inMaintenance ->
            if (inMaintenance) {
                emptyFlow()
            } else {
                flow {
                    withOperation {
                        emitAll(block())
                    }
                }
            }
        }

    /**
     * Exécute une opération courte. Si une maintenance est en cours, l'appel attend sa fin.
     * Une opération lancée depuis le bloc de maintenance est interdite pour éviter un deadlock.
     */
    suspend fun <T> withOperation(block: suspend () -> T): T {
        check(currentCoroutineContext()[MaintenanceContext] == null) {
            "Une opération Room ne peut pas être lancée depuis un bloc de maintenance."
        }

        while (true) {
            val waitForMaintenance = stateMutex.withLock {
                if (maintenanceInProgress) {
                    checkNotNull(maintenanceFinished) {
                        "État de maintenance incohérent : signal de fin absent."
                    }
                } else {
                    activeOpsCount++
                    null
                }
            }

            if (waitForMaintenance == null) break
            waitForMaintenance.await()
        }

        try {
            return withContext(OperationContext()) { block() }
        } finally {
            stateMutex.withLock {
                activeOpsCount--
                check(activeOpsCount >= 0) { "Compteur d'opérations Room négatif." }
                if (activeOpsCount == 0) {
                    idleSignal?.complete(Unit)
                    idleSignal = null
                }
            }
        }
    }

    /**
     * Suspend les Flows, attend les opérations actives, ferme Room puis exécute la maintenance.
     * Les opérations ponctuelles arrivant pendant cette période sont mises en attente.
     */
    suspend fun <T> runMaintenance(
        closeDb: () -> Unit = {},
        block: suspend () -> T
    ): T {
        check(currentCoroutineContext()[MaintenanceContext] == null) {
            "Les maintenances imbriquées sont interdites."
        }
        check(currentCoroutineContext()[OperationContext] == null) {
            "Une maintenance ne peut pas être lancée depuis une opération Room active."
        }

        return maintenanceMutex.withLock {
            withContext(MaintenanceContext()) {
                val waitForIdle = stateMutex.withLock {
                    check(!maintenanceInProgress) { "Maintenance déjà active." }
                    maintenanceInProgress = true
                    maintenanceFinished = CompletableDeferred()
                    _maintenanceState.value = true
                    if (activeOpsCount > 0) {
                        CompletableDeferred<Unit>().also { idleSignal = it }
                    } else {
                        null
                    }
                }

                try {
                    waitForIdle?.await()
                    closeDb()
                    block()
                } finally {
                    stateMutex.withLock {
                        maintenanceInProgress = false
                        idleSignal = null
                        maintenanceFinished?.complete(Unit)
                        maintenanceFinished = null
                        _maintenanceState.value = false
                    }
                    _epoch.value = _epoch.value + 1
                }
            }
        }
    }
}
