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
import ph.edu.batstateu.ecoconnect.ui.viewmodel.LoginUiState

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .verticalScroll(rememberScrollState()),
    ) {
        AuthHeader(title = "Welcome to EcoConnect", subtitle = "Sign in to request pickups and track them.")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Sign in", style = MaterialTheme.typography.titleLarge)
                state.info?.let { FormMessage(it, isError = false) }
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
                    label = "Password",
                    enabled = !state.loading,
                    imeAction = ImeAction.Done,
                    onDone = onSignIn,
                )
                state.error?.let { FormMessage(it, isError = true) }
                PrimaryPillButton(text = "Sign in", loading = state.loading, onClick = onSignIn)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("New here?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCreateAccount, enabled = !state.loading) { Text("Create an account") }
        }
    }
}

// ---------- Previews ----------

@Preview(showSystemUi = true, name = "Login")
@Composable
private fun LoginPreview() {
    EcoConnectTheme {
        LoginScreen(LoginUiState(email = "juan@example.com"), {}, {}, {}, {})
    }
}

@Preview(showSystemUi = true, name = "Login – loading")
@Composable
private fun LoginLoadingPreview() {
    EcoConnectTheme {
        LoginScreen(LoginUiState(email = "juan@example.com", password = "secret", loading = true), {}, {}, {}, {})
    }
}

@Preview(showSystemUi = true, name = "Login – error + info")
@Composable
private fun LoginErrorPreview() {
    EcoConnectTheme {
        LoginScreen(
            LoginUiState(
                email = "juan@example.com",
                info = "Account created. Check juan@example.com for a confirmation link, then sign in.",
                error = "Wrong email or password.",
            ),
            {}, {}, {}, {},
        )
    }
}
