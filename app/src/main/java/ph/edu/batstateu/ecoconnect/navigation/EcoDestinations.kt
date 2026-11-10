package ph.edu.batstateu.ecoconnect.navigation

/** Route names for Navigation Compose. */
object EcoDestinations {
    const val HOME = "home"
    const val REQUEST_PICKUP = "request_pickup"
    const val WASTE_GUIDE = "waste_guide"
    const val MY_REQUESTS = "my_requests"
    const val BLUETOOTH_CONFIRM = "bluetooth_confirm"
    const val PROFILE = "profile"

    /** Routes that show the bottom navigation bar. */
    val topLevel = setOf(HOME, WASTE_GUIDE, MY_REQUESTS, PROFILE)
}
