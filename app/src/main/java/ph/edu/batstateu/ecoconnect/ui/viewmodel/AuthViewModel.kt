package ph.edu.batstateu.ecoconnect.ui.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.EcoConnectApp
import ph.edu.batstateu.ecoconnect.data.auth.AuthRepository
import ph.edu.batstateu.ecoconnect.data.auth.AuthState
import ph.edu.batstateu.ecoconnect.data.auth.SignUpResult

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Shown after sign-up when the email must be confirmed first. */
    val info: String? = null,
)

data class SignUpUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

const val MIN_PASSWORD_LENGTH = 6

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val isConfigured: Boolean,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _login = MutableStateFlow(LoginUiState(error = notConfiguredMessage()))
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private val _signUp = MutableStateFlow(SignUpUiState())
    val signUp: StateFlow<SignUpUiState> = _signUp.asStateFlow()

    fun onLoginChange(email: String = _login.value.email, password: String = _login.value.password) =
        _login.update { it.copy(email = email, password = password, error = notConfiguredMessage()) }

    fun onSignUpChange(
        fullName: String = _signUp.value.fullName,
        email: String = _signUp.value.email,
        password: String = _signUp.value.password,
        confirmPassword: String = _signUp.value.confirmPassword,
    ) = _signUp.update {
        it.copy(fullName = fullName, email = email, password = password, confirmPassword = confirmPassword, error = null)
    }

    fun signIn() {
        val form = _login.value
        if (form.loading) return
        val error = when {
            !isValidEmail(form.email) -> "Enter a valid email address."
            form.password.isEmpty() -> "Enter your password."
            else -> null
        }
        if (error != null) return _login.update { it.copy(error = error, info = null) }

        _login.update { it.copy(loading = true, error = null, info = null) }
        viewModelScope.launch {
            val result = authRepository.signIn(form.email, form.password)
            // On success the auth state changes and the app leaves this screen.
            _login.update { it.copy(loading = false, password = "", error = result.exceptionOrNull()?.message) }
        }
    }

    /** [onNeedsConfirmation] returns to Login, which then shows a "check your email" note. */
    fun signUp(onNeedsConfirmation: () -> Unit) {
        val form = _signUp.value
        if (form.loading) return
        val error = when {
            form.fullName.isBlank() -> "Enter your full name."
            !isValidEmail(form.email) -> "Enter a valid email address."
            form.password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters."
            form.password != form.confirmPassword -> "Passwords don't match."
            else -> null
        }
        if (error != null) return _signUp.update { it.copy(error = error) }

        _signUp.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            authRepository.signUp(form.email, form.password, form.fullName)
                .onSuccess { result ->
                    _signUp.value = SignUpUiState()
                    if (result is SignUpResult.NeedsConfirmation) {
                        _login.value = LoginUiState(
                            email = result.email,
                            info = "Account created. Check ${result.email} for a confirmation link, then sign in.",
                        )
                        onNeedsConfirmation()
                    }
                }
                .onFailure { e -> _signUp.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun notConfiguredMessage(): String? =
        if (isConfigured) null else "Supabase isn't configured. Add SUPABASE_URL and SUPABASE_KEY to local.properties."

    private fun isValidEmail(email: String) = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as EcoConnectApp
                AuthViewModel(app.authRepository, app.isSupabaseConfigured)
            }
        }
    }
}
