package fr.terrapilot.assistant

import fr.terrapilot.assistant.data.db.DatabaseAccessGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DatabaseAccessGateTest {

    @Test
    fun maintenance_attend_fin_operation_en_cours_et_refuse_nouvelle_operation() = runBlocking {
        val opStarted = CompletableDeferred<Unit>()
        val allowOpFinish = CompletableDeferred<Unit>()
        var dbClosed = false

        val opJob = launch {
            DatabaseAccessGate.withOperation {
                opStarted.complete(Unit)
                allowOpFinish.await()
            }
        }
        opStarted.await()

        val maintJob = launch {
            DatabaseAccessGate.runMaintenance(closeDb = { dbClosed = true }) {
                var refused = false
                try {
                    DatabaseAccessGate.withOperation { }
                } catch (_: IllegalStateException) {
                    refused = true
                }
                assertTrue(refused)
            }
        }

        // Tant que l'opération n'est pas terminée, closeDb ne doit pas avoir été appelé
        kotlinx.coroutines.delay(50)
        assertFalse(dbClosed)

        allowOpFinish.complete(Unit)
        opJob.join()
        maintJob.join()
        assertTrue(dbClosed)
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
            kotlinx.coroutines.flow.flow {
                DatabaseAccessGate.withOperation {
                    collecting.complete(Unit)
                    emit(1)
                    kotlinx.coroutines.delay(5000)
                }
            }.collect { }
        }
        collecting.await()

        val maintJob = launch {
            DatabaseAccessGate.runMaintenance(closeDb = { dbClosed = true }) { }
        }
        kotlinx.coroutines.delay(50)
        assertFalse(dbClosed)

        flowJob.cancel()
        flowJob.join()
        maintJob.join()
        assertTrue(dbClosed)
    }
}
