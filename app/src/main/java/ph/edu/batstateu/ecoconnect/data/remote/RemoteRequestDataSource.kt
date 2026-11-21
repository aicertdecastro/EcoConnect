package ph.edu.batstateu.ecoconnect.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
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
class RemoteRequestDataSource(private val client: SupabaseClient) : CollectorRemote {

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

    /** Requests owned by [userId] (RLS also restricts users to their own rows), with names embedded. */
    suspend fun fetchMyRequests(userId: String): Result<List<CollectionRequestRowDto>> = safeCall("fetchMyRequests") {
        client.from(REQUESTS).select(Columns.raw(REQUEST_ROW_SELECT)) {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<CollectionRequestRowDto>()
    }

    /** Collectors: SENT and unassigned requests, plus every request assigned to [collectorId]. */
    override suspend fun fetchCollectorRequests(collectorId: String): Result<List<CollectionRequestRowDto>> =
        safeCall("fetchCollectorRequests") {
            client.from(REQUESTS).select(Columns.raw(REQUEST_ROW_SELECT)) {
                filter {
                    or {
                        and {
                            eq("status", SyncStatus.SENT.name)
                            exact("collector_id", null)
                        }
                        eq("collector_id", collectorId)
                    }
                }
                order("pickup_date", Order.ASCENDING)
            }.decodeList<CollectionRequestRowDto>()
        }

    /**
     * Collectors: accept, decline or reschedule a SENT request and assign it to [collectorId].
     * Only matches rows that are still SENT and unassigned (or already ours), and returns the
     * updated rows via `select()` — an EMPTY list means another collector took it first.
     * [proposedDate] is "yyyy-MM-dd" (for RESCHEDULED).
     */
    override suspend fun respondToRequest(
        id: String,
        collectorId: String,
        status: SyncStatus,
        response: String?,
        proposedDate: String?,
    ): Result<List<CollectionRequestRowDto>> = safeCall("respondToRequest $id") {
        require(status in COLLECTOR_RESPONSES) { "Not a collector response: $status" }
        client.from(REQUESTS).update(
            buildJsonObject {
                put("status", status.name)
                put("collector_id", collectorId)
                put("collector_response", response)
                put("proposed_date", proposedDate)
                put("updated_at", formatServerTimestamp(System.currentTimeMillis()))
            },
        ) {
            select(Columns.raw(REQUEST_ROW_SELECT))
            filter {
                eq("id", id)
                eq("status", SyncStatus.SENT.name)
                or {
                    exact("collector_id", null)
                    eq("collector_id", collectorId)
                }
            }
        }.decodeList<CollectionRequestRowDto>()
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

    /**
     * Collector's receipt reached the server → mark the request COLLECTED. Only matches a
     * request assigned to [collectorId] (RLS enforces the same). Idempotent.
     */
    suspend fun markCollected(requestId: String, collectorId: String): Result<Unit> = safeCall("markCollected $requestId") {
        client.from(REQUESTS).update(
            buildJsonObject {
                put("status", SyncStatus.COLLECTED.name)
                put("updated_at", formatServerTimestamp(System.currentTimeMillis()))
            },
        ) {
            filter {
                eq("id", requestId)
                eq("collector_id", collectorId)
            }
        }
        Unit
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

/** Statuses a collector may set from the respond actions. */
val COLLECTOR_RESPONSES = setOf(SyncStatus.ACCEPTED, SyncStatus.DECLINED, SyncStatus.RESCHEDULED)

/** Collector-side server calls, behind an interface so CollectorRepository can be tested. */
interface CollectorRemote {
    suspend fun fetchCollectorRequests(collectorId: String): Result<List<CollectionRequestRowDto>>

    /** Returns the updated rows; empty when no row matched (already taken). */
    suspend fun respondToRequest(
        id: String,
        collectorId: String,
        status: SyncStatus,
        response: String?,
        proposedDate: String?,
    ): Result<List<CollectionRequestRowDto>>
}
