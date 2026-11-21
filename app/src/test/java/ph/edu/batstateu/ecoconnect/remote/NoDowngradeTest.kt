package ph.edu.batstateu.ecoconnect.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import ph.edu.batstateu.ecoconnect.bluetooth.testRequest
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.remote.MergeAction
import ph.edu.batstateu.ecoconnect.data.remote.keepLocalCollected
import ph.edu.batstateu.ecoconnect.data.remote.mergeServerRequest

/** refreshFromServer must never move a locally COLLECTED request back. */
class NoDowngradeTest {

    private val collected = testRequest(SyncStatus.COLLECTED)

    @Test
    fun serverAcceptedOrRescheduledDoesNotDowngradeCollected() {
        listOf(SyncStatus.ACCEPTED, SyncStatus.RESCHEDULED).forEach { serverStatus ->
            val action = mergeServerRequest(collected, testRequest(serverStatus))
            assertEquals("server $serverStatus", MergeAction.Skip, action)
        }
    }

    @Test
    fun otherServerChangesStillApplyButStatusStaysCollected() {
        val server = testRequest(SyncStatus.ACCEPTED).copy(collectorName = "Pedro", updatedAt = 5_000L)
        val action = mergeServerRequest(collected, server) as MergeAction.UpdateFromServer
        assertEquals(SyncStatus.COLLECTED, action.server.syncStatus)
        assertEquals("Pedro", action.server.collectorName)
    }

    @Test
    fun serverCollectedIsAppliedNormally() {
        val action = mergeServerRequest(testRequest(SyncStatus.ACCEPTED), testRequest(SyncStatus.COLLECTED))
        assertEquals(SyncStatus.COLLECTED, (action as MergeAction.UpdateFromServer).server.syncStatus)
    }

    @Test
    fun keepLocalCollectedOnlyTouchesCollectedRows() {
        val server = testRequest(SyncStatus.ACCEPTED)
        assertEquals(server, keepLocalCollected(null, server))
        assertEquals(server, keepLocalCollected(testRequest(SyncStatus.SENT), server))
        assertEquals(SyncStatus.COLLECTED, keepLocalCollected(collected, server).syncStatus)
    }
}
