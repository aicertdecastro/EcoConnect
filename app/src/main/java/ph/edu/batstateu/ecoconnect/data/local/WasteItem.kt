package ph.edu.batstateu.ecoconnect.data.local

import kotlinx.serialization.Serializable

/**
 * One waste category in a request and its estimated weight.
 * [category] is the `WasteCategory` enum name (e.g. "Biodegradable").
 * Stored as a JSON array in Room; maps to a `jsonb` column in Supabase later.
 */
@Serializable
data class WasteItem(
    val category: String,
    val kg: Double,
)

val List<WasteItem>.totalKg: Double get() = sumOf { it.kg }
