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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ph.edu.batstateu.ecoconnect.ui.components.EcoBottomBar
import ph.edu.batstateu.ecoconnect.ui.components.PlaceholderScreen
import ph.edu.batstateu.ecoconnect.ui.guide.GuideHomeRoute
import ph.edu.batstateu.ecoconnect.ui.guide.GuideQuizRoute
import ph.edu.batstateu.ecoconnect.ui.guide.GuideResultRoute
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
    val openRequestPickup: (String?) -> Unit = { categoryId ->
        navController.navigate(EcoDestinations.requestPickup(categoryId))
    }

    NavHost(
        navController = navController,
        startDestination = EcoDestinations.HOME,
        modifier = modifier,
    ) {
        composable(EcoDestinations.HOME) {
            HomeRoute(
                onTileClick = { tile ->
                    when (tile) {
                        HomeTile.RequestPickup -> openRequestPickup(null)
                        HomeTile.BluetoothConfirm -> navController.navigate(EcoDestinations.BLUETOOTH_CONFIRM)
                        HomeTile.WasteGuide -> navController.navigateTopLevel(EcoDestinations.WASTE_GUIDE)
                        HomeTile.MyRequests -> navController.navigateTopLevel(EcoDestinations.MY_REQUESTS)
                    }
                },
            )
        }
        composable(
            route = EcoDestinations.REQUEST_PICKUP_PATTERN,
            arguments = listOf(
                navArgument(EcoDestinations.ARG_CATEGORY) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            RequestPickupRoute(
                preselectedCategoryId = entry.arguments?.getString(EcoDestinations.ARG_CATEGORY),
                onBack = { navController.popBackStack() },
                // Saved to Room as PENDING_SUBMISSION; return to Home, which updates from the database.
                onSubmitted = { navController.popBackStack(EcoDestinations.HOME, inclusive = false) },
            )
        }

        // ---- Waste guide ----
        composable(EcoDestinations.WASTE_GUIDE) {
            GuideHomeRoute(
                onStartQuestions = { navController.navigate(EcoDestinations.GUIDE_QUIZ) },
                onOpenCategory = { navController.navigate(EcoDestinations.guideResult(it)) },
            )
        }
        composable(EcoDestinations.GUIDE_QUIZ) {
            GuideQuizRoute(
                onExit = { navController.popBackStack() },
                onRequestPickup = openRequestPickup,
            )
        }
        composable(
            route = EcoDestinations.GUIDE_RESULT_PATTERN,
            arguments = listOf(navArgument(EcoDestinations.ARG_CATEGORY_ID) { type = NavType.StringType }),
        ) { entry ->
            GuideResultRoute(
                categoryId = entry.arguments?.getString(EcoDestinations.ARG_CATEGORY_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onRequestPickup = openRequestPickup,
            )
        }

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
