package fr.terrapilot.assistant

import fr.terrapilot.assistant.data.db.DatabaseAccessGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class DatabaseAccessGateTest {

    @Test
    fun maintenance_attend_operations_actives_et_met_en_attente_les_nouvelles() = runBlocking {
        val opStarted = CompletableDeferred<Unit>()
        val allowOpFinish = CompletableDeferred<Unit>()
        val maintenanceEntered = CompletableDeferred<Unit>()
        val allowMaintenanceFinish = CompletableDeferred<Unit>()
        val queuedOperationEntered = CompletableDeferred<Unit>()
        var dbClosed = false

        val activeJob = launch {
            DatabaseAccessGate.withOperation {
                opStarted.complete(Unit)
                allowOpFinish.await()
            }
        }
        opStarted.await()

        val maintenanceJob = launch {
            DatabaseAccessGate.runMaintenance(closeDb = { dbClosed = true }) {
                maintenanceEntered.complete(Unit)
                allowMaintenanceFinish.await()
            }
        }

        withTimeout(2_000) { DatabaseAccessGate.maintenanceState.first { it } }
        assertFalse("Room ne doit pas être fermé tant que l'opération active tourne", dbClosed)

        val queuedJob = launch {
            DatabaseAccessGate.withOperation {
                queuedOperationEntered.complete(Unit)
            }
        }

        // L'opération ponctuelle est en attente, elle ne doit pas entrer pendant la maintenance.
        assertFalse(queuedOperationEntered.isCompleted)

        allowOpFinish.complete(Unit)
        withTimeout(2_000) { maintenanceEntered.await() }
        assertTrue("La fermeture doit intervenir avant le bloc de maintenance", dbClosed)
        assertFalse("Une nouvelle opération ne doit pas entrer pendant la maintenance", queuedOperationEntered.isCompleted)

        allowMaintenanceFinish.complete(Unit)
        withTimeout(2_000) { maintenanceJob.join() }
        withTimeout(2_000) { queuedJob.join() }
        activeJob.join()

        assertTrue(queuedOperationEntered.isCompleted)
        assertFalse(DatabaseAccessGate.maintenanceState.value)
    }

    @Test
    fun exception_pendant_maintenance_remet_gate_en_etat_et_incremente_epoch() = runBlocking {
        val beforeEpoch = DatabaseAccessGate.epoch.value
        try {
            DatabaseAccessGate.runMaintenance<Unit> {
                throw RuntimeException("Panne simulée")
            }
            fail("Exception attendue")
        } catch (_: RuntimeException) {
        }
        assertEquals(beforeEpoch + 1, DatabaseAccessGate.epoch.value)
        val ok = DatabaseAccessGate.withOperation { true }
        assertTrue(ok)
    }

    @Test
    fun collecte_flow_active_comptabilisee_et_annulation_libere_gate() = runBlocking {
        val collecting = CompletableDeferred<Unit>()
        var dbClosed = false

        val flowJob = launch {
            DatabaseAccessGate.observeFlowWithProvider {
                kotlinx.coroutines.flow.flow {
                    collecting.complete(Unit)
                    emit(1)
                    kotlinx.coroutines.delay(5_000)
                }
            }.collect { }
        }
        collecting.await()

        val maintenanceJob = launch {
            DatabaseAccessGate.runMaintenance(closeDb = { dbClosed = true }) {
                assertTrue(DatabaseAccessGate.maintenanceState.value)
            }
        }
        kotlinx.coroutines.delay(50)
        assertFalse(dbClosed)

        flowJob.cancel()
        flowJob.join()
        maintenanceJob.join()
        assertTrue(dbClosed)
        assertFalse(DatabaseAccessGate.maintenanceState.value)
    }

    @Test
    fun flow_permanent_annule_automatiquement_par_maintenance_et_repart() = runBlocking {
        val firstCollect = CompletableDeferred<Unit>()
        val secondCollect = CompletableDeferred<Unit>()
        var collectCount = 0
        var dbClosed = false

        val flowJob = launch {
            DatabaseAccessGate.observeFlowWithProvider {
                kotlinx.coroutines.flow.flow {
                    collectCount++
                    if (collectCount == 1) firstCollect.complete(Unit)
                    if (collectCount == 2) secondCollect.complete(Unit)
                    emit(collectCount)
                    kotlinx.coroutines.delay(5_000)
                }
            }.collect { }
        }
        firstCollect.await()

        // Le collecteur n'est pas annulé manuellement : le gate doit le suspendre puis le relancer.
        DatabaseAccessGate.runMaintenance(closeDb = { dbClosed = true }) {
            assertTrue(dbClosed)
        }
        withTimeout(2_000) { secondCollect.await() }

        flowJob.cancel()
        flowJob.join()
    }
}
