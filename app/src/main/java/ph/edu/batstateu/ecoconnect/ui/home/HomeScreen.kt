package ph.edu.batstateu.ecoconnect.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.navigation.EcoDestinations
import ph.edu.batstateu.ecoconnect.ui.components.EcoBottomBar
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen100
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen700
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen800
import ph.edu.batstateu.ecoconnect.ui.theme.HeaderShape
import ph.edu.batstateu.ecoconnect.ui.theme.StatValueStyle
import ph.edu.batstateu.ecoconnect.ui.theme.TileAmber
import ph.edu.batstateu.ecoconnect.ui.theme.TileBlue
import ph.edu.batstateu.ecoconnect.ui.theme.TileTeal

/** Summary numbers shown on the home stats card. */
data class HomeStats(
    val kgSegregated: Double = 0.0,
    val totalRequests: Int = 0,
    val pendingRequests: Int = 0,
)

/** Shortcut tiles on the home screen. */
enum class HomeTile(val label: String, val icon: ImageVector, val accent: Color) {
    WasteGuide("Waste Guide", Icons.AutoMirrored.Outlined.MenuBook, EcoGreen100),
    RequestPickup("Request Pickup", Icons.Outlined.LocalShipping, TileAmber),
    MyRequests("My Requests", Icons.AutoMirrored.Outlined.ListAlt, TileBlue),
    BluetoothConfirm("Bluetooth Confirm", Icons.Outlined.Bluetooth, TileTeal),
}

@Composable
fun HomeScreen(
    stats: HomeStats,
    onTileClick: (HomeTile) -> Unit,
    modifier: Modifier = Modifier,
    userName: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Box {
            HomeHeader(userName = userName)
            StatsCard(
                stats = stats,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 112.dp, start = 20.dp, end = 20.dp),
            )
        }

        Text(
            "What would you like to do?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
        )
        TileGrid(onTileClick = onTileClick, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeHeader(userName: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EcoGreen800, HeaderShape)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(168.dp)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(EcoGreen700, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Eco, contentDescription = null, tint = Color.White)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("EcoConnect", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    if (userName.isNullOrBlank()) "Welcome back!" else "Hello, $userName!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun StatsCard(stats: HomeStats, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 20.dp),
        ) {
            StatItem(value = formatKg(stats.kgSegregated), unit = "kg", label = "Segregated", modifier = Modifier.weight(1f))
            VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            StatItem(value = stats.totalRequests.toString(), label = "Requests", modifier = Modifier.weight(1f))
            VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            StatItem(value = stats.pendingRequests.toString(), label = "Pending", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier, unit: String? = null) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = StatValueStyle, color = MaterialTheme.colorScheme.primary)
            if (unit != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TileGrid(onTileClick: (HomeTile) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeTile.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    TileCard(tile = tile, onClick = { onTileClick(tile) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TileCard(tile: HomeTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.height(120.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(tile.accent, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tile.icon, contentDescription = null, tint = EcoGreen800, modifier = Modifier.size(26.dp))
            }
            Text(
                tile.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
            )
        }
    }
}

private fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(java.util.Locale.US, "%.1f", kg)

@Preview(showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    EcoConnectTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = { EcoBottomBar(currentRoute = EcoDestinations.HOME, onNavigate = {}) },
        ) { padding ->
            HomeScreen(
                stats = FakeData.homeStats,
                userName = FakeData.userName,
                onTileClick = {},
                modifier = Modifier.padding(padding),
            )
        }
    }
}
