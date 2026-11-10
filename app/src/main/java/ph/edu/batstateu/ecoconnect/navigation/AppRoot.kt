package ph.edu.batstateu.ecoconnect.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ph.edu.batstateu.ecoconnect.data.auth.AuthState
import ph.edu.batstateu.ecoconnect.data.auth.UserRole
import ph.edu.batstateu.ecoconnect.ui.auth.LoginRoute
import ph.edu.batstateu.ecoconnect.ui.auth.SignUpRoute
import ph.edu.batstateu.ecoconnect.ui.collector.CollectorHomeScreen
import ph.edu.batstateu.ecoconnect.ui.viewmodel.AuthViewModel

/**
 * Top of the UI, chosen by auth state:
 * no session → Login; role user → resident app (Home); role collector → Collector Home.
 * Each branch has its own nav graph, so signing out drops the whole back stack.
 */
@Composable
fun AppRoot(viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (val state = authState) {
        AuthState.Loading -> Splash()
        AuthState.SignedOut -> AuthNavHost(viewModel)
        is AuthState.SignedIn -> key(state.userId) {
            when (state.role) {
                UserRole.COLLECTOR -> CollectorHomeScreen(user = state, onSignOut = viewModel::signOut)
                UserRole.USER -> EcoConnectRoot(user = state, onSignOut = viewModel::signOut)
            }
        }
    }
}

private const val LOGIN = "login"
private const val SIGN_UP = "sign_up"

@Composable
private fun AuthNavHost(viewModel: AuthViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LOGIN) {
        composable(LOGIN) {
            LoginRoute(
                viewModel = viewModel,
                onCreateAccount = { navController.navigate(SIGN_UP) { launchSingleTop = true } },
            )
        }
        composable(SIGN_UP) {
            SignUpRoute(
                viewModel = viewModel,
                onBackToLogin = {
                    if (!navController.popBackStack(LOGIN, inclusive = false)) navController.navigate(LOGIN)
                },
            )
        }
    }
}

@Composable
private fun Splash() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
