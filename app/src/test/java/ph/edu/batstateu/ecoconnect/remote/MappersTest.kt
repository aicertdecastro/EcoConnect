package ph.edu.batstateu.ecoconnect.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.WasteItem
import ph.edu.batstateu.ecoconnect.data.remote.CollectionRequestDto
import ph.edu.batstateu.ecoconnect.data.remote.CollectionRequestRowDto
import ph.edu.batstateu.ecoconnect.data.remote.MergeAction
import ph.edu.batstateu.ecoconnect.data.remote.formatServerDate
import ph.edu.batstateu.ecoconnect.data.remote.formatServerTimestamp
import ph.edu.batstateu.ecoconnect.data.remote.mergeServerRequest
import ph.edu.batstateu.ecoconnect.data.remote.normalizeTime
import ph.edu.batstateu.ecoconnect.data.remote.parseServerDate
import ph.edu.batstateu.ecoconnect.data.remote.parseServerTimestamp
import ph.edu.batstateu.ecoconnect.data.remote.syncStatusFromServer
import ph.edu.batstateu.ecoconnect.data.remote.toDto
import ph.edu.batstateu.ecoconnect.data.remote.toEntity

private val lenientJson = Json { ignoreUnknownKeys = true }

class MappersTest {

    // 2026-11-20 00:00:00 UTC
    private val nov20 = 1_795_132_800_000L

    // 2026-11-18 08:30:15.250 UTC
    private val createdAt = 1_795_132_800_000L - 2 * 86_400_000L + (8 * 3600 + 30 * 60 + 15) * 1000L + 250

    private val entity = CollectionRequestEntity(
        id = "8d3f6a2e-1111-4222-8333-944455556666",
        userId = "user-uuid",
        wasteItems = listOf(WasteItem("BIODEGRADABLE", 3.5), WasteItem("RECYCLABLE", 2.0)),
        address = "Blk 4 Lot 12, Brgy. Alangilan",
        pickupDate = nov20,
        pickupTime = "07:30",
        notes = "Gate is blue",
        syncStatus = SyncStatus.PENDING_SUBMISSION,
        createdAt = createdAt,
        updatedAt = createdAt + 1000,
    )

    // ---------- Entity → DTO ----------

    @Test
    fun pendingSubmissionIsUploadedAsSent() {
        assertEquals("SENT", entity.toDto().status)
    }

    @Test
    fun otherStatusesKeepTheirName() {
        SyncStatus.entries.filter { it != SyncStatus.PENDING_SUBMISSION }.forEach {
            assertEquals(it.name, entity.copy(syncStatus = it).toDto().status)
        }
    }

    @Test
    fun entityToDtoMapsEveryField() {
        val dto = entity.toDto()
        assertEquals(entity.id, dto.id)
        assertEquals("user-uuid", dto.userId)
        assertEquals(entity.wasteItems, dto.wasteItems)
        assertEquals(5.5, dto.totalKg, 1e-9)
        assertEquals(entity.address, dto.address)
        assertEquals("2026-11-20", dto.pickupDate)
        assertEquals("07:30", dto.pickupTime)
        assertEquals("Gate is blue", dto.notes)
        assertEquals("2026-11-18T08:30:15.250Z", dto.createdAt)
        assertEquals("2026-11-18T08:30:16.250Z", dto.updatedAt)
        assertNull(dto.collectorId)
        assertNull(dto.collectorResponse)
        assertNull(dto.proposedDate)
    }

    @Test
    fun proposedDateIsFormattedAsDate() {
        val dto = entity.copy(syncStatus = SyncStatus.RESCHEDULED, proposedDate = nov20 + 86_400_000L).toDto()
        assertEquals("2026-11-21", dto.proposedDate)
    }

    // ---------- DTO → entity ----------

    private val serverDto = CollectionRequestDto(
        id = "server-id",
        userId = "user-uuid",
        wasteItems = listOf(WasteItem("RESIDUAL", 1.0)),
        totalKg = 1.0,
        address = "Somewhere",
        pickupDate = "2026-11-20",
        pickupTime = "08:00:00",
        status = "RESCHEDULED",
        collectorId = "collector-uuid",
        collectorResponse = "Truck is full",
        proposedDate = "2026-11-24",
        createdAt = "2026-11-18T08:30:15.250123+00:00",
        updatedAt = "2026-11-19T16:30:00+08:00",
    )

    @Test
    fun dtoToEntityMapsEveryField() {
        val e = serverDto.toEntity()
        assertEquals("server-id", e.id)
        assertEquals("user-uuid", e.userId)
        assertEquals(listOf(WasteItem("RESIDUAL", 1.0)), e.wasteItems)
        assertEquals(nov20, e.pickupDate)
        assertEquals("08:00", e.pickupTime)
        assertEquals(SyncStatus.RESCHEDULED, e.syncStatus)
        assertEquals("Truck is full", e.collectorResponse)
        assertEquals(nov20 + 4 * 86_400_000L, e.proposedDate)
        assertEquals(createdAt, e.createdAt)
        // 16:30 at +08:00 is 08:30 UTC on Nov 19
        assertEquals(nov20 - 86_400_000L + (8 * 3600 + 30 * 60) * 1000L, e.updatedAt)
    }

    @Test
    fun missingOptionalFieldsBecomeNullOrFallback() {
        val e = serverDto.copy(collectorResponse = null, proposedDate = null, updatedAt = null, notes = null).toEntity()
        assertNull(e.collectorResponse)
        assertNull(e.proposedDate)
        assertNull(e.notes)
        assertEquals(e.createdAt, e.updatedAt)
    }

    @Test
    fun unknownOrPendingServerStatusFallsBackToSent() {
        assertEquals(SyncStatus.SENT, syncStatusFromServer("SOMETHING_NEW"))
        assertEquals(SyncStatus.SENT, syncStatusFromServer(null))
        assertEquals(SyncStatus.SENT, syncStatusFromServer("PENDING_SUBMISSION"))
        assertEquals(SyncStatus.COLLECTED, syncStatusFromServer("collected"))
    }

    @Test
    fun roundTripKeepsEverythingExceptPendingStatus() {
        val back = entity.toDto().toEntity()
        assertEquals(entity.copy(syncStatus = SyncStatus.SENT), back)
        val accepted = entity.copy(syncStatus = SyncStatus.ACCEPTED, collectorResponse = "OK")
        assertEquals(accepted, accepted.toDto().toEntity())
    }

    // ---------- Date / time helpers ----------

    @Test
    fun timestampVariantsParse() {
        val base = parseServerTimestamp("2026-11-20T00:00:00Z")
        assertEquals(nov20, base)
        assertEquals(nov20, parseServerTimestamp("2026-11-20 08:00:00+08"))
        assertEquals(nov20, parseServerTimestamp("2026-11-19T19:00:00-05:00"))
        assertEquals(nov20, parseServerTimestamp("2026-11-20T00:00:00"))
        assertEquals(nov20 + 123, parseServerTimestamp("2026-11-20T00:00:00.123456Z"))
        assertEquals(nov20 + 500, parseServerTimestamp("2026-11-20T00:00:00.5+00:00"))
        assertEquals(nov20, parseServerTimestamp(formatServerTimestamp(nov20)))
    }

    @Test
    fun dateHelpersRoundTrip() {
        assertEquals("2026-11-20", formatServerDate(nov20))
        assertEquals(nov20, parseServerDate("2026-11-20"))
        assertEquals("2026-02-28", formatServerDate(parseServerDate("2026-02-28")))
    }

    @Test
    fun timeIsNormalizedToHoursAndMinutes() {
        assertEquals("08:00", normalizeTime("08:00:00"))
        assertEquals("07:05", normalizeTime("7:05"))
        assertEquals("13:45", normalizeTime("13:45"))
    }

    // ---------- Merge ----------

    private val sent = entity.copy(syncStatus = SyncStatus.SENT)

    @Test
    fun serverOnlyRowIsInserted() {
        val server = serverDto.toEntity()
        assertEquals(MergeAction.Insert(server), mergeServerRequest(local = null, server = server))
    }

    @Test
    fun newerServerStatusUpdatesSentRow() {
        val server = sent.copy(syncStatus = SyncStatus.ACCEPTED, collectorResponse = "See you", updatedAt = sent.updatedAt + 5000)
        val action = mergeServerRequest(sent, server)
        assertEquals(MergeAction.UpdateFromServer(server), action)
    }

    @Test
    fun unsentLocalRowIsNeverOverwritten() {
        val server = entity.copy(syncStatus = SyncStatus.ACCEPTED)
        assertEquals(MergeAction.Skip, mergeServerRequest(entity, server))
    }

    @Test
    fun identicalRowIsSkipped() {
        assertEquals(MergeAction.Skip, mergeServerRequest(sent, sent.copy(updatedAt = sent.updatedAt + 99)))
    }

    // ---------- Receipt ----------

    @Test
    fun receiptToDto() {
        val receipt = CollectionReceiptEntity(
            receiptId = "r-1",
            requestId = entity.id,
            userId = "user-uuid",
            collectorId = "collector-uuid",
            confirmedAt = nov20,
        )
        val dto = receipt.toDto(uploadedBy = "collector-uuid")
        assertEquals("r-1", dto.receiptId)
        assertEquals(entity.id, dto.requestId)
        assertEquals("user-uuid", dto.userId)
        assertEquals("collector-uuid", dto.collectorId)
        assertEquals("collector-uuid", dto.uploadedBy)
        assertEquals("2026-11-20T00:00:00.000Z", dto.confirmedAt)
    }

    // ---------- JSON shape sent to Supabase ----------

    @Test
    fun dtoSerializesWithSnakeCaseKeysAndJsonbArray() {
        val json = Json.encodeToJsonElement(CollectionRequestDto.serializer(), entity.toDto()).jsonObject
        val expected = setOf(
            "id", "user_id", "waste_items", "total_kg", "address", "pickup_date", "pickup_time", "notes",
            "status", "collector_id", "collector_response", "proposed_date", "created_at", "updated_at",
        )
        assertTrue("keys were ${json.keys}", json.keys.containsAll(setOf("id", "user_id", "waste_items", "total_kg", "status")))
        assertTrue("unexpected keys ${json.keys - expected}", (json.keys - expected).isEmpty())
        val items = json["waste_items"] as JsonArray
        assertEquals("BIODEGRADABLE", items[0].jsonObject["category"]!!.jsonPrimitive.content)
        assertEquals("3.5", items[0].jsonObject["kg"]!!.jsonPrimitive.content)
        assertEquals("SENT", json["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun serverJsonDecodes() {
        val body = """
            {"id":"a","user_id":"u","waste_items":[{"category":"RECYCLABLE","kg":2}],"total_kg":2,
             "address":"x","pickup_date":"2026-11-20","pickup_time":"07:30:00","notes":null,
             "status":"ACCEPTED","collector_id":"c","collector_response":null,"proposed_date":null,
             "created_at":"2026-11-18T08:30:15.25+00:00","updated_at":"2026-11-18T08:30:15.25+00:00",
             "extra_column":"ignored"}
        """.trimIndent()
        val dto = lenientJson.decodeFromString(CollectionRequestDto.serializer(), body)
        val e = dto.toEntity()
        assertEquals(SyncStatus.ACCEPTED, e.syncStatus)
        assertEquals(listOf(WasteItem("RECYCLABLE", 2.0)), e.wasteItems)
        assertEquals("07:30", e.pickupTime)
        assertEquals(createdAt, e.createdAt)
    }

    // ---------- Collector fields ----------

    private val rowJson = """
        {"id":"r1","user_id":"u1","waste_items":[{"category":"RECYCLABLE","kg":4}],"total_kg":4,
         "address":"Brgy. Alangilan","pickup_date":"2026-11-20","pickup_time":"07:30:00","notes":null,
         "status":"RESCHEDULED","collector_id":"c1","collector_response":"Truck is full","proposed_date":"2026-11-21",
         "created_at":"2026-11-18T08:30:15.25+00:00","updated_at":"2026-11-18T08:30:15.25+00:00",
         "requester":{"full_name":"Juan Dela Cruz"},"collector":{"full_name":"Pedro Santos"}}
    """.trimIndent()

    @Test
    fun embeddedRowMapsCollectorFields() {
        val e = lenientJson.decodeFromString(CollectionRequestRowDto.serializer(), rowJson).toEntity()
        assertEquals("Juan Dela Cruz", e.requesterName)
        assertEquals("Pedro Santos", e.collectorName)
        assertEquals("c1", e.collectorId)
        assertEquals(SyncStatus.RESCHEDULED, e.syncStatus)
        assertEquals("Truck is full", e.collectorResponse)
        assertEquals(nov20 + 86_400_000L, e.proposedDate)
    }

    @Test
    fun nullEmbedsGiveNullNames() {
        val body = rowJson.replace("""{"full_name":"Pedro Santos"}""", "null")
            .replace(""""collector_id":"c1"""", """"collector_id":null""")
            .replace("""{"full_name":"Juan Dela Cruz"}""", """{"full_name":"  "}""")
        val e = lenientJson.decodeFromString(CollectionRequestRowDto.serializer(), body).toEntity()
        assertNull(e.collectorName)
        assertNull(e.collectorId)
        assertNull("blank names are treated as missing", e.requesterName)
    }

    @Test
    fun missingEmbedKeysDecode() {
        val body = rowJson.substringBefore("\"requester\"").trimEnd().removeSuffix(",") + "}"
        val e = lenientJson.decodeFromString(CollectionRequestRowDto.serializer(), body).toEntity()
        assertNull(e.requesterName)
        assertNull(e.collectorName)
    }

    @Test
    fun collectorIdIsPushedAndPulled() {
        assertEquals("c9", entity.copy(collectorId = "c9").toDto().collectorId)
        assertEquals("c", lenientJson.decodeFromString(CollectionRequestDto.serializer(), serverDtoJsonWithCollector).toEntity().collectorId)
    }

    private val serverDtoJsonWithCollector = """
        {"id":"a","user_id":"u","waste_items":[],"total_kg":0,"address":"x","pickup_date":"2026-11-20",
         "pickup_time":"07:30","status":"ACCEPTED","collector_id":"c"}
    """.trimIndent()

    @Test
    fun collectorNameChangeTriggersUpdateButKeepsSentAt() {
        val local = sent.copy(sentAt = 42L, collectorId = "c1", syncStatus = SyncStatus.ACCEPTED)
        val server = local.copy(sentAt = null, collectorName = "Pedro Santos")
        val action = mergeServerRequest(local, server)
        assertTrue(action is MergeAction.UpdateFromServer)
        assertEquals("Pedro Santos", (action as MergeAction.UpdateFromServer).server.collectorName)
        assertEquals(MergeAction.Skip, mergeServerRequest(server, server))
    }
}
