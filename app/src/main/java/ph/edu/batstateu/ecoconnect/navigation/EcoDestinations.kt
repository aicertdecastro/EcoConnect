package ph.edu.batstateu.ecoconnect.navigation

/** Route names for Navigation Compose. */
object EcoDestinations {
    const val HOME = "home"
    const val WASTE_GUIDE = "waste_guide"
    const val MY_REQUESTS = "my_requests"
    const val PROFILE = "profile"

    // Waste scanner (test screen for the Gemini integration)
    const val SCAN = "scan"

    // Request Pickup, with an optional pre-selected waste category id.
    const val REQUEST_PICKUP = "request_pickup"
    const val ARG_CATEGORY = "category"
    const val REQUEST_PICKUP_PATTERN = "$REQUEST_PICKUP?$ARG_CATEGORY={$ARG_CATEGORY}"
    fun requestPickup(categoryId: String? = null): String =
        if (categoryId == null) REQUEST_PICKUP else "$REQUEST_PICKUP?$ARG_CATEGORY=$categoryId"

    // Waste guide flow
    const val GUIDE_QUIZ = "guide_quiz"
    const val ARG_CATEGORY_ID = "categoryId"
    const val GUIDE_RESULT_PATTERN = "guide_result/{$ARG_CATEGORY_ID}"
    fun guideResult(categoryId: String): String = "guide_result/$categoryId"

    // Request details
    const val ARG_REQUEST_ID = "requestId"
    const val REQUEST_DETAILS_PATTERN = "request_details/{$ARG_REQUEST_ID}"
    fun requestDetails(requestId: String): String = "request_details/$requestId"

    // Bluetooth pickup confirmation (resident side; the collector graph has its own route)
    const val CONFIRM_PICKUP_PATTERN = "confirm_pickup/{$ARG_REQUEST_ID}"
    fun confirmPickup(requestId: String): String = "confirm_pickup/$requestId"

    /** Routes that show the bottom navigation bar. */
    val topLevel = setOf(HOME, WASTE_GUIDE, MY_REQUESTS, PROFILE)
}
