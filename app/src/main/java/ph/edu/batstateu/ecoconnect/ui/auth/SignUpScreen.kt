package ph.edu.batstateu.ecoconnect.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.viewmodel.MIN_PASSWORD_LENGTH
import ph.edu.batstateu.ecoconnect.ui.viewmodel.SignUpUiState

@Composable
fun SignUpScreen(
    state: SignUpUiState,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .verticalScroll(rememberScrollState()),
    ) {
        AuthHeader(title = "Create your account", subtitle = "Request waste pickups in your barangay.")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Sign up", style = MaterialTheme.typography.titleLarge)
                AuthTextField(
                    value = state.fullName,
                    onValueChange = onFullNameChange,
                    label = "Full name",
                    icon = Icons.Outlined.Badge,
                    enabled = !state.loading,
                )
                AuthTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = "Email",
                    icon = Icons.Outlined.Email,
                    enabled = !state.loading,
                    keyboardType = KeyboardType.Email,
                )
                PasswordField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = "Password (at least $MIN_PASSWORD_LENGTH characters)",
                    enabled = !state.loading,
                )
                PasswordField(
                    value = state.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = "Confirm password",
                    enabled = !state.loading,
                    imeAction = ImeAction.Done,
                    onDone = onSignUp,
                )
                state.error?.let { FormMessage(it, isError = true) }
                PrimaryPillButton(text = "Create account", loading = state.loading, onClick = onSignUp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Already have an account?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onSignIn, enabled = !state.loading) { Text("Sign in") }
        }
    }
}

// ---------- Previews ----------

@Preview(showSystemUi = true, name = "Sign up")
@Composable
private fun SignUpPreview() {
    EcoConnectTheme {
        SignUpScreen(SignUpUiState(fullName = "Juan Dela Cruz", email = "juan@example.com"), {}, {}, {}, {}, {}, {})
    }
}

@Preview(showSystemUi = true, name = "Sign up – loading")
@Composable
private fun SignUpLoadingPreview() {
    EcoConnectTheme {
        SignUpScreen(
            SignUpUiState(fullName = "Juan Dela Cruz", email = "juan@example.com", password = "secret1", confirmPassword = "secret1", loading = true),
            {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(showSystemUi = true, name = "Sign up – error")
@Composable
private fun SignUpErrorPreview() {
    EcoConnectTheme {
        SignUpScreen(
            SignUpUiState(fullName = "Juan Dela Cruz", email = "juan@example.com", error = "Passwords don't match."),
            {}, {}, {}, {}, {}, {},
        )
    }
}
