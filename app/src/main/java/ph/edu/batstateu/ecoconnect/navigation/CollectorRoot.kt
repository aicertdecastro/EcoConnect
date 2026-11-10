package ph.edu.batstateu.ecoconnect.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ph.edu.batstateu.ecoconnect.data.auth.AuthState
import ph.edu.batstateu.ecoconnect.ui.collector.CollectorHomeRoute
import ph.edu.batstateu.ecoconnect.ui.collector.CollectorRequestDetailsRoute
import ph.edu.batstateu.ecoconnect.ui.viewmodel.CollectorViewModel

object CollectorDestinations {
    const val HOME = "collector_home"
    const val ARG_REQUEST_ID = "requestId"
    const val REQUEST_PATTERN = "collector_request/{$ARG_REQUEST_ID}"
    fun request(id: String) = "collector_request/$id"
}

/**
 * Collector-only graph. AppRoot shows it solely for role COLLECTOR, and the
 * resident graph (EcoNavHost) contains none of these routes, so a user account
 * can never navigate here.
 */
@Composable
fun CollectorRoot(user: AuthState.SignedIn, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    // One ViewModel for the whole graph so home and details share state and messages.
    val viewModel: CollectorViewModel = viewModel(factory = CollectorViewModel.Factory)
    NavHost(navController, startDestination = CollectorDestinations.HOME, modifier = modifier) {
        composable(CollectorDestinations.HOME) {
            CollectorHomeRoute(
                user = user,
                onRequestClick = { navController.navigate(CollectorDestinations.request(it)) { launchSingleTop = true } },
                onSignOut = onSignOut,
                viewModel = viewModel,
            )
        }
        composable(
            CollectorDestinations.REQUEST_PATTERN,
            arguments = listOf(navArgument(CollectorDestinations.ARG_REQUEST_ID) { type = NavType.StringType }),
        ) { entry ->
            CollectorRequestDetailsRoute(
                requestId = entry.arguments?.getString(CollectorDestinations.ARG_REQUEST_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
    }
}
