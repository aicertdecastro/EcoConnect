package ph.edu.batstateu.ecoconnect.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ph.edu.batstateu.ecoconnect.bluetooth.CollectorValidator.Decision
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus

class ValidationTest {

    private val now = 1_795_300_000_000L
    private val hello = BtMessage.Hello(REQUEST_ID, RESIDENT, now - 50)

    private fun validate(
        request: CollectionRequestEntity? = testRequest(),
        hello: BtMessage.Hello = this.hello,
        receipt: CollectionReceiptEntity? = null,
        expected: String = REQUEST_ID,
    ) = CollectorValidator.validate(hello, expected, request, COLLECTOR, receipt, now)

    // ---------- Collector: accept ----------

    @Test
    fun acceptedAndRescheduledAreConfirmedNow() {
        assertEquals(Decision.Ok(now), validate(testRequest(SyncStatus.ACCEPTED)))
        assertEquals(Decision.Ok(now), validate(testRequest(SyncStatus.RESCHEDULED)))
    }

    // ---------- Collector: each REJECT reason ----------

    @Test
    fun requestNotFoundLocally() {
        assertEquals(Decision.Reject(CollectorValidator.NOT_FOUND), validate(request = null))
    }

    @Test
    fun notAssignedToThisCollector() {
        assertEquals(Decision.Reject(CollectorValidator.OTHER_COLLECTOR), validate(testRequest(collectorId = "collector-2")))
        assertEquals(Decision.Reject(CollectorValidator.OTHER_COLLECTOR), validate(testRequest(collectorId = null)))
    }

    @Test
    fun userIdMismatch() {
        assertEquals(Decision.Reject(CollectorValidator.OTHER_RESIDENT), validate(hello = hello.copy(userId = "someone-else")))
    }

    @Test
    fun statusNotReady() {
        listOf(SyncStatus.PENDING_SUBMISSION, SyncStatus.SENT, SyncStatus.DECLINED, SyncStatus.COLLECTED).forEach { status ->
            assertEquals(
                Decision.Reject(CollectorValidator.notReady(status)),
                validate(testRequest(status)),
            )
        }
    }

    @Test
    fun helloForAnotherRequestThanTheOneOnScreen() {
        assertEquals(Decision.Reject(CollectorValidator.WRONG_REQUEST), validate(expected = "another-request"))
    }

    // ---------- Collector: retries ----------

    @Test
    fun collectedWithReceiptReconfirmsWithStoredTime() {
        val receipt = CollectionReceiptEntity("r", REQUEST_ID, RESIDENT, COLLECTOR, confirmedAt = 42L)
        assertEquals(Decision.Ok(42L), validate(testRequest(SyncStatus.COLLECTED), receipt = receipt))
    }

    @Test
    fun residentsEarlierConfirmedAtIsReusedWhenCollectorHasNoReceipt() {
        assertEquals(Decision.Ok(777L), validate(hello = hello.copy(previousConfirmedAt = 777L)))
        // A time in the future is not trusted.
        assertEquals(Decision.Ok(now), validate(hello = hello.copy(previousConfirmedAt = now + 60_000)))
    }

    // ---------- Resident: verify CONFIRM ----------

    @Test
    fun userAcceptsMatchingConfirm() {
        assertNull(UserVerifier.problem(BtMessage.Confirm(REQUEST_ID, COLLECTOR, now), testRequest()))
    }

    @Test
    fun userRejectsConfirmForAnotherRequest() {
        assertEquals(UserVerifier.WRONG_REQUEST, UserVerifier.problem(BtMessage.Confirm("other", COLLECTOR, now), testRequest()))
    }

    @Test
    fun userRejectsConfirmFromAnotherCollector() {
        assertEquals(UserVerifier.WRONG_COLLECTOR, UserVerifier.problem(BtMessage.Confirm(REQUEST_ID, "collector-2", now), testRequest()))
    }
}
