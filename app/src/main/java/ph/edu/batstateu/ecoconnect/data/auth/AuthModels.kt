package ph.edu.batstateu.ecoconnect.data.auth

/** Role from `profiles.role`. Unknown values fall back to [USER] (least privilege). */
enum class UserRole(val serverValue: String, val label: String) {
    USER("user", "Resident"),
    COLLECTOR("collector", "Collector"),
    ;

    companion object {
        fun fromServer(value: String?): UserRole =
            entries.firstOrNull { it.serverValue.equals(value?.trim(), ignoreCase = true) } ?: USER
    }
}

sealed interface AuthState {
    /** Restoring a saved session at startup. */
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(
        val userId: String,
        val email: String?,
        val fullName: String?,
        val role: UserRole,
    ) : AuthState
}

sealed interface SignUpResult {
    /** Session created; the app switches to the signed-in screens. */
    data object SignedIn : SignUpResult

    /** The project requires email confirmation before the first sign-in. */
    data class NeedsConfirmation(val email: String) : SignUpResult
}
