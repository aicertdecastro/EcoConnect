package ph.edu.batstateu.ecoconnect.data.auth

import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ph.edu.batstateu.ecoconnect.data.remote.ProfileDto

/**
 * Supabase Auth wrapper. The session itself is persisted by supabase-kt
 * (Android settings storage), so it survives app restarts. We additionally cache
 * the last user id + role so the app still opens offline.
 */
class AuthRepository(
    private val client: SupabaseClient,
    private val prefs: SharedPreferences,
    scope: CoroutineScope,
    private val isConfigured: Boolean,
) {
    private val auth get() = client.auth

    @OptIn(ExperimentalCoroutinesApi::class)
    val authState: StateFlow<AuthState> = auth.sessionStatus
        .transformLatest { status ->
            when (status) {
                is SessionStatus.Initializing -> emit(AuthState.Loading)
                is SessionStatus.NotAuthenticated -> {
                    if (status.isSignOut) clearCache()
                    emit(AuthState.SignedOut)
                }
                is SessionStatus.Authenticated -> {
                    val user = status.session.user ?: auth.currentUserOrNull()
                    val userId = user?.id ?: cachedUserId()
                    if (userId == null) {
                        emit(AuthState.SignedOut)
                        return@transformLatest
                    }
                    rememberUser(userId)
                    // Show the cached role at once, then confirm it with the server.
                    cachedRole(userId)?.let { emit(signedIn(userId, user, it)) }
                    emit(signedIn(userId, user, fetchRole(userId)))
                }
                // Saved session couldn't be refreshed (usually offline): keep working offline.
                is SessionStatus.RefreshFailure -> {
                    val userId = auth.currentUserOrNull()?.id ?: cachedUserId()
                    if (userId == null) {
                        emit(AuthState.SignedOut)
                    } else {
                        emit(signedIn(userId, auth.currentUserOrNull(), cachedRole(userId) ?: UserRole.USER))
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    fun currentUserId(): String? = (authState.value as? AuthState.SignedIn)?.userId

    /**
     * Waits (up to [timeoutMs]) for the saved session to load from storage.
     * Returns the signed-in user id, or null if nobody is signed in.
     */
    suspend fun awaitSignedInUserId(timeoutMs: Long = SESSION_LOAD_TIMEOUT_MS): String? {
        val state = withTimeoutOrNull(timeoutMs) { authState.first { it !is AuthState.Loading } }
        return (state as? AuthState.SignedIn)?.userId
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runAuth {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, fullName: String): Result<SignUpResult> = runAuth {
        val trimmedEmail = email.trim()
        auth.signUpWith(Email) {
            this.email = trimmedEmail
            this.password = password
            data = buildJsonObject { put("full_name", fullName.trim()) }
        }
        if (auth.currentSessionOrNull() != null) SignUpResult.SignedIn
        else SignUpResult.NeedsConfirmation(trimmedEmail)
    }

    /** Always ends signed out locally, even if the server can't be reached. */
    suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Remote sign-out failed; clearing local session", e)
            runCatching { auth.clearSession() }
        }
        clearCache()
    }

    /** Reads `profiles.role`; falls back to the cached role, then USER. */
    suspend fun fetchRole(userId: String): UserRole {
        val profile = try {
            withTimeoutOrNull(ROLE_TIMEOUT_MS) {
                client.from("profiles").select {
                    filter { eq("id", userId) }
                }.decodeSingleOrNull<ProfileDto>()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't fetch role: ${e.message}")
            null
        }
        if (profile == null) return cachedRole(userId) ?: UserRole.USER
        profile.fullName?.let { prefs.edit().putString(KEY_NAME_PREFIX + userId, it).apply() }
        return UserRole.fromServer(profile.role).also {
            prefs.edit().putString(KEY_ROLE_PREFIX + userId, it.name).apply()
        }
    }

    private fun signedIn(userId: String, user: UserInfo?, role: UserRole) = AuthState.SignedIn(
        userId = userId,
        email = user?.email ?: prefs.getString(KEY_EMAIL, null),
        fullName = user?.userMetadata?.get("full_name")?.jsonPrimitive?.content
            ?: prefs.getString(KEY_NAME_PREFIX + userId, null),
        role = role,
    )

    private fun rememberUser(userId: String) {
        prefs.edit()
            .putString(KEY_LAST_USER, userId)
            .putString(KEY_EMAIL, auth.currentUserOrNull()?.email ?: prefs.getString(KEY_EMAIL, null))
            .apply()
    }

    private fun cachedUserId(): String? = prefs.getString(KEY_LAST_USER, null)

    private fun cachedRole(userId: String): UserRole? =
        prefs.getString(KEY_ROLE_PREFIX + userId, null)?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }

    private fun clearCache() {
        prefs.edit().remove(KEY_LAST_USER).remove(KEY_EMAIL).apply()
    }

    private suspend fun <T> runAuth(block: suspend () -> T): Result<T> {
        if (!isConfigured) return Result.failure(AuthException("Supabase isn't configured. Add SUPABASE_URL and SUPABASE_KEY to local.properties."))
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Auth call failed: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(AuthException(friendlyMessage(e), e))
        }
    }

    companion object {
        private const val TAG = "AuthRepository"
        private const val ROLE_TIMEOUT_MS = 10_000L
        private const val SESSION_LOAD_TIMEOUT_MS = 10_000L
        private const val KEY_LAST_USER = "last_user_id"
        private const val KEY_EMAIL = "last_email"
        private const val KEY_ROLE_PREFIX = "role_"
        private const val KEY_NAME_PREFIX = "name_"
        const val PREFS_NAME = "auth_cache"

        fun friendlyMessage(e: Throwable): String = when (e) {
            is AuthRestException -> when {
                e.error.contains("invalid_credentials", true) ||
                    e.errorDescription.contains("invalid login", true) -> "Wrong email or password."
                e.error.contains("email_not_confirmed", true) ||
                    e.errorDescription.contains("not confirmed", true) -> "Please confirm your email first, then sign in."
                e.error.contains("user_already_exists", true) ||
                    e.errorDescription.contains("already registered", true) -> "An account with this email already exists."
                else -> e.errorDescription.ifBlank { "Sign-in failed. Please try again." }
            }
            is RestException -> e.description?.takeIf { it.isNotBlank() } ?: e.error
            else -> "Can't reach the server. Check your internet connection and try again."
        }
    }
}

class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
