package ph.edu.batstateu.ecoconnect.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import ph.edu.batstateu.ecoconnect.navigation.EcoDestinations
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

val EcoBottomNavItems = listOf(
    BottomNavItem(EcoDestinations.HOME, "Home", Icons.Outlined.Home),
    BottomNavItem(EcoDestinations.WASTE_GUIDE, "Guide", Icons.AutoMirrored.Outlined.MenuBook),
    BottomNavItem(EcoDestinations.MY_REQUESTS, "Requests", Icons.AutoMirrored.Outlined.ListAlt),
    BottomNavItem(EcoDestinations.PROFILE, "Profile", Icons.Outlined.Person),
)

@Composable
fun EcoBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        EcoBottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun EcoBottomBarPreview() {
    EcoConnectTheme {
        EcoBottomBar(currentRoute = EcoDestinations.HOME, onNavigate = {})
    }
}
