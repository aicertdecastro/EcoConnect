package ph.edu.batstateu.ecoconnect.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus

private const val TAG = "RemoteRequests"
private const val REQUESTS = "collection_requests"
private const val RECEIPTS = "collection_receipts"

/** Postgres unique_violation: the row is already on the server. */
private const val UNIQUE_VIOLATION = "23505"

/**
 * Supabase access for requests and receipts. Every call returns a [Result] and
 * never throws (except coroutine cancellation), so network errors can't crash the app.
 */
class RemoteRequestDataSource(private val client: SupabaseClient) {

    /** Idempotent: retrying with the same id never creates a duplicate. */
    suspend fun pushRequest(dto: CollectionRequestDto): Result<Unit> = safeCall("pushRequest ${dto.id}") {
        try {
            client.from(REQUESTS).upsert(dto) {
                onConflict = "id"
                ignoreDuplicates = true
            }
        } catch (e: PostgrestRestException) {
            if (e.code != UNIQUE_VIOLATION) throw e // "already exists" counts as success
        }
    }

    /** Requests owned by [userId] (RLS also restricts users to their own rows). */
    suspend fun fetchMyRequests(userId: String): Result<List<CollectionRequestDto>> = safeCall("fetchMyRequests") {
        client.from(REQUESTS).select {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<CollectionRequestDto>()
    }

    /** Collectors only: RLS returns every request. */
    suspend fun fetchAllRequests(): Result<List<CollectionRequestDto>> = safeCall("fetchAllRequests") {
        client.from(REQUESTS).select {
            order("created_at", Order.DESCENDING)
        }.decodeList<CollectionRequestDto>()
    }

    /**
     * Collectors only: accept, decline, reschedule or mark collected. Assigns the
     * request to [collectorId]; RLS rejects requests assigned to someone else.
     * [proposedDate] is "yyyy-MM-dd" (for RESCHEDULED).
     */
    suspend fun respondToRequest(
        id: String,
        collectorId: String,
        status: SyncStatus,
        response: String?,
        proposedDate: String?,
    ): Result<Unit> = safeCall("respondToRequest $id") {
        require(status != SyncStatus.PENDING_SUBMISSION && status != SyncStatus.SENT) { "Not a collector response: $status" }
        client.from(REQUESTS).update(
            buildJsonObject {
                put("status", status.name)
                put("collector_id", collectorId)
                put("collector_response", response)
                put("proposed_date", proposedDate)
                put("updated_at", formatServerTimestamp(System.currentTimeMillis()))
            },
        ) {
            filter { eq("id", id) }
        }
    }

    /** Idempotent on (request_id, uploaded_by). */
    suspend fun pushReceipt(dto: CollectionReceiptDto): Result<Unit> = safeCall("pushReceipt ${dto.receiptId}") {
        try {
            client.from(RECEIPTS).upsert(dto) {
                onConflict = "request_id,uploaded_by"
                ignoreDuplicates = true
            }
        } catch (e: PostgrestRestException) {
            if (e.code != UNIQUE_VIOLATION) throw e
        }
    }

    private suspend fun <T> safeCall(what: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$what failed: ${e.javaClass.simpleName}: ${e.message}")
        Result.failure(e)
    }
}
