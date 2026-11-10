package ph.edu.batstateu.ecoconnect.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.edu.batstateu.ecoconnect.ui.viewmodel.AuthViewModel

@Composable
fun LoginRoute(
    viewModel: AuthViewModel,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.login.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onEmailChange = { viewModel.onLoginChange(email = it) },
        onPasswordChange = { viewModel.onLoginChange(password = it) },
        onSignIn = viewModel::signIn,
        onCreateAccount = onCreateAccount,
        modifier = modifier,
    )
}

/** [onBackToLogin] is used by the "Sign in" link and after a sign-up that needs email confirmation. */
@Composable
fun SignUpRoute(
    viewModel: AuthViewModel,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.signUp.collectAsStateWithLifecycle()
    SignUpScreen(
        state = state,
        onFullNameChange = { viewModel.onSignUpChange(fullName = it) },
        onEmailChange = { viewModel.onSignUpChange(email = it) },
        onPasswordChange = { viewModel.onSignUpChange(password = it) },
        onConfirmPasswordChange = { viewModel.onSignUpChange(confirmPassword = it) },
        onSignUp = { viewModel.signUp(onNeedsConfirmation = onBackToLogin) },
        onSignIn = onBackToLogin,
        modifier = modifier,
    )
}
