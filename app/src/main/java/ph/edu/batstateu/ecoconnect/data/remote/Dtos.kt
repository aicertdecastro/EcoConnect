package ph.edu.batstateu.ecoconnect.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ph.edu.batstateu.ecoconnect.data.local.WasteItem

/** Row of `collection_requests` on Supabase (snake_case columns). */
@Serializable
data class CollectionRequestDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    /** jsonb: `[{"category": "RECYCLABLE", "kg": 1.5}]`. */
    @SerialName("waste_items") val wasteItems: List<WasteItem>,
    @SerialName("total_kg") val totalKg: Double,
    @SerialName("address") val address: String,
    /** Postgres `date`: "yyyy-MM-dd". */
    @SerialName("pickup_date") val pickupDate: String,
    /** "HH:mm" when sent; Postgres `time` returns "HH:mm:ss". */
    @SerialName("pickup_time") val pickupTime: String,
    @SerialName("notes") val notes: String? = null,
    /** 'SENT' | 'ACCEPTED' | 'DECLINED' | 'RESCHEDULED' | 'COLLECTED'. */
    @SerialName("status") val status: String,
    @SerialName("collector_id") val collectorId: String? = null,
    @SerialName("collector_response") val collectorResponse: String? = null,
    @SerialName("proposed_date") val proposedDate: String? = null,
    /** ISO-8601 timestamps (timestamptz). */
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Row of `collection_receipts`. Unique on (request_id, uploaded_by). */
@Serializable
data class CollectionReceiptDto(
    @SerialName("receipt_id") val receiptId: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("collector_id") val collectorId: String,
    @SerialName("confirmed_at") val confirmedAt: String,
    @SerialName("uploaded_by") val uploadedBy: String,
)

/** Row of `profiles`. Role is 'user' or 'collector' and can't be changed from the app. */
@Serializable
data class ProfileDto(
    @SerialName("id") val id: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("role") val role: String? = null,
)

/** `{ "full_name": ... }` embedded from `profiles`. */
@Serializable
data class ProfileNameDto(
    @SerialName("full_name") val fullName: String? = null,
)

/**
 * `collection_requests` row as READ from the server, with the requester's and
 * collector's names embedded. Never sent back (the embeds aren't columns).
 * Use [REQUEST_ROW_SELECT] as the select string.
 */
@Serializable
data class CollectionRequestRowDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("waste_items") val wasteItems: List<WasteItem>,
    @SerialName("total_kg") val totalKg: Double? = null,
    @SerialName("address") val address: String,
    @SerialName("pickup_date") val pickupDate: String,
    @SerialName("pickup_time") val pickupTime: String,
    @SerialName("notes") val notes: String? = null,
    @SerialName("status") val status: String,
    @SerialName("collector_id") val collectorId: String? = null,
    @SerialName("collector_response") val collectorResponse: String? = null,
    @SerialName("proposed_date") val proposedDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("requester") val requester: ProfileNameDto? = null,
    @SerialName("collector") val collector: ProfileNameDto? = null,
)

/**
 * collection_requests has two foreign keys to profiles (user_id, collector_id),
 * so each embed names its relationship explicitly.
 */
const val REQUEST_ROW_SELECT =
    "*, requester:profiles!collection_requests_user_id_fkey(full_name), " +
        "collector:profiles!collection_requests_collector_id_fkey(full_name)"
