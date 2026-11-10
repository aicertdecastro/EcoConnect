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
