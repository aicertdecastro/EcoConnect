package ph.edu.batstateu.ecoconnect.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun syncStatusToString(status: SyncStatus): String = status.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun wasteItemsToJson(items: List<WasteItem>): String = json.encodeToString(items)

    @TypeConverter
    fun jsonToWasteItems(value: String): List<WasteItem> = json.decodeFromString(value)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
