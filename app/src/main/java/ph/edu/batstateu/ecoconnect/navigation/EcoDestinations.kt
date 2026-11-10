package ph.edu.batstateu.ecoconnect.navigation

/** Route names for Navigation Compose. */
object EcoDestinations {
    const val HOME = "home"
    const val WASTE_GUIDE = "waste_guide"
    const val MY_REQUESTS = "my_requests"
    const val BLUETOOTH_CONFIRM = "bluetooth_confirm"
    const val PROFILE = "profile"

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

    /** Routes that show the bottom navigation bar. */
    val topLevel = setOf(HOME, WASTE_GUIDE, MY_REQUESTS, PROFILE)
}
