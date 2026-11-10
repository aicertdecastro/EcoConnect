package ph.edu.batstateu.ecoconnect.ui.preview

import ph.edu.batstateu.ecoconnect.data.guide.GuideCategory
import ph.edu.batstateu.ecoconnect.data.guide.GuideItem
import ph.edu.batstateu.ecoconnect.data.guide.GuideQuestion
import ph.edu.batstateu.ecoconnect.data.guide.Outcome
import ph.edu.batstateu.ecoconnect.data.guide.SearchHit
import ph.edu.batstateu.ecoconnect.data.guide.UnsureAdvice
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuide
import ph.edu.batstateu.ecoconnect.data.guide.Weekday
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory
import ph.edu.batstateu.ecoconnect.ui.home.HomeStats
import ph.edu.batstateu.ecoconnect.ui.pickup.CategoryAmount
import ph.edu.batstateu.ecoconnect.ui.pickup.RequestPickupUiState

/**
 * Sample values for @Preview functions ONLY.
 * Do not reference this from runtime code; real data comes from Room and the bundled guide asset.
 */
internal object FakeData {
    const val userName = "Juan"

    val homeStats = HomeStats(
        kgSegregated = 42.5,
        totalRequests = 8,
        pendingRequests = 2,
    )

    // 2026-11-20 00:00 UTC, a Friday (DatePicker works in UTC millis)
    private const val SAMPLE_DATE_MILLIS = 1_795_132_800_000L

    val pickupState = RequestPickupUiState(
        address = "Blk 4 Lot 12, Mabini St., Brgy. Alangilan, Batangas City",
        dateMillis = SAMPLE_DATE_MILLIS,
        hour = 7,
        minute = 30,
        categories = listOf(
            CategoryAmount(WasteCategory.BIODEGRADABLE, 3.5),
            CategoryAmount(WasteCategory.RECYCLABLE, 2.0),
            CategoryAmount(WasteCategory.RESIDUAL, 1.0),
            CategoryAmount(WasteCategory.SPECIAL, 0.0),
        ),
        status = SyncStatus.PENDING_SUBMISSION,
    )

    const val datePickerInitialMillis = SAMPLE_DATE_MILLIS

    // ---- Waste guide (shortened copy of the asset, previews only) ----

    private val biodegradable = GuideCategory(
        id = "BIODEGRADABLE", name = "Biodegradable",
        examples = listOf("Food scraps", "Peels", "Leaves"),
        instructions = listOf("Keep separate in a covered container.", "Compost suitable garden and food waste if possible."),
        requestable = true,
        collectionDays = listOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY, Weekday.SUNDAY),
    )
    private val recyclable = GuideCategory(
        id = "RECYCLABLE", name = "Recyclable",
        examples = listOf("Clean bottles", "Cans", "Paper"),
        instructions = listOf("Keep separate from other waste.", "Clean and dry recyclables where practical."),
        requestable = true,
        collectionDays = listOf(Weekday.TUESDAY, Weekday.THURSDAY, Weekday.SATURDAY),
    )
    private val residual = GuideCategory(
        id = "RESIDUAL", name = "Residual",
        examples = listOf("Wrappers", "Sanitary waste"),
        instructions = listOf("Bag securely.", "Keep separate from compostables and recyclables."),
        requestable = true,
        scheduleNote = "No separate residual pickup day is published for Alangilan. Confirm with your barangay.",
    )
    private val special = GuideCategory(
        id = "SPECIAL", name = "Special / Hazardous",
        examples = listOf("Batteries", "Chemicals", "E-waste"),
        instructions = listOf("Keep out of regular household bags.", "Ask the barangay or City ENRO how to hand these over."),
        requestable = false,
        scheduleNote = "Not collected door-to-door. Ask the barangay or City ENRO.",
    )

    val guideCategories = listOf(biodegradable, recyclable, residual, special)

    val guide = WasteGuide(
        version = 1,
        community = "Barangay Alangilan, Batangas City",
        source = "Batangas City 2023 Socio-Economic Profile",
        policy = "No Segregation, No Collection",
        generalNote = "Schedules may change. Confirm the current route and any holiday adjustments with your barangay. No pickup times are published.",
        startQuestionId = "Q1",
        unsure = UnsureAdvice(
            title = "Not sure yet",
            instructions = listOf("Keep this item separate for now.", "Ask your barangay how to dispose of it.", "Do not include it in a pickup request yet."),
        ),
        categories = guideCategories,
        questions = listOf(
            GuideQuestion(
                id = "Q1",
                text = "Is it a battery, chemical, lamp, electronic item, or medical waste?",
                hint = "Includes chargers, old phones, paint, and expired medicine.",
                yes = Outcome(result = "SPECIAL"),
                no = Outcome(next = "Q2"),
            ),
        ),
        items = listOf(
            GuideItem("Sachets", "RESIDUAL", listOf("sachet")),
            GuideItem("Shampoo bottle", "RECYCLABLE", listOf("shampoo"), tip = "Rinse and dry."),
        ),
    )

    val searchHits = listOf(
        SearchHit(GuideItem("Sachets", "RESIDUAL", listOf("sachet", "shampoo sachet")), residual),
        SearchHit(GuideItem("Plastic containers (HDPE)", "RECYCLABLE", listOf("shampoo bottle"), tip = "Rinse and dry."), recyclable),
    )
}
