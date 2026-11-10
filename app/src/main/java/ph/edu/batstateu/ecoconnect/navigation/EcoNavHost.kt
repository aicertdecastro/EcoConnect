package ph.edu.batstateu.ecoconnect.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ph.edu.batstateu.ecoconnect.ui.components.EcoBottomBar
import ph.edu.batstateu.ecoconnect.ui.components.PlaceholderScreen
import ph.edu.batstateu.ecoconnect.ui.home.HomeRoute
import ph.edu.batstateu.ecoconnect.ui.home.HomeTile
import ph.edu.batstateu.ecoconnect.ui.pickup.RequestPickupRoute

/** App root: bottom bar on top-level routes + the navigation graph. */
@Composable
fun EcoConnectRoot(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen handles its own system-bar insets.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentRoute in EcoDestinations.topLevel) {
                EcoBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { navController.navigateTopLevel(it) },
                )
            }
        },
    ) { padding ->
        EcoNavHost(navController = navController, modifier = Modifier.padding(padding))
    }
}

@Composable
fun EcoNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = EcoDestinations.HOME,
        modifier = modifier,
    ) {
        composable(EcoDestinations.HOME) {
            HomeRoute(
                onTileClick = { tile ->
                    when (tile) {
                        HomeTile.RequestPickup -> navController.navigate(EcoDestinations.REQUEST_PICKUP)
                        HomeTile.BluetoothConfirm -> navController.navigate(EcoDestinations.BLUETOOTH_CONFIRM)
                        HomeTile.WasteGuide -> navController.navigateTopLevel(EcoDestinations.WASTE_GUIDE)
                        HomeTile.MyRequests -> navController.navigateTopLevel(EcoDestinations.MY_REQUESTS)
                    }
                },
            )
        }
        composable(EcoDestinations.REQUEST_PICKUP) {
            RequestPickupRoute(
                onBack = { navController.popBackStack() },
                // Saved to Room as PENDING_SUBMISSION; go back to Home, which updates from the database.
                onSubmitted = { navController.popBackStack() },
            )
        }
        composable(EcoDestinations.WASTE_GUIDE) { PlaceholderScreen(title = "Waste Guide") }
        composable(EcoDestinations.MY_REQUESTS) { PlaceholderScreen(title = "My Requests") }
        composable(EcoDestinations.PROFILE) { PlaceholderScreen(title = "Profile") }
        composable(EcoDestinations.BLUETOOTH_CONFIRM) {
            PlaceholderScreen(title = "Bluetooth Confirm", onBack = { navController.popBackStack() })
        }
    }
}

/** Switch bottom-nav tabs without stacking duplicates, keeping each tab's state. */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
