package ph.edu.batstateu.ecoconnect.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.totalKg
import ph.edu.batstateu.ecoconnect.ui.components.SyncStatusChip
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRequestsScreen(
    requests: List<CollectionRequestEntity>,
    pendingCount: Int,
    syncing: Boolean,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My Requests") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SyncBar(pendingCount = pendingCount, syncing = syncing, onSyncNow = onSyncNow)
            if (requests.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(requests, key = { it.id }) { RequestCard(it) }
                }
            }
        }
    }
}

/** Temporary manual sync (automatic background sync comes later). */
@Composable
private fun SyncBar(pendingCount: Int, syncing: Boolean, onSyncNow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (pendingCount) {
                0 -> "Everything is sent"
                1 -> "1 request waiting to send"
                else -> "$pendingCount requests waiting to send"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onSyncNow, enabled = !syncing, shape = PillShape) {
            if (syncing) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(if (syncing) "Syncing…" else "Sync now", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun RequestCard(request: CollectionRequestEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "${formatDate(request.pickupDate)} · ${request.pickupTime}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                )
                SyncStatusChip(request.syncStatus)
            }
            Text(
                request.wasteItems.joinToString { item ->
                    "${WasteCategory.fromId(item.category)?.label ?: item.category} ${formatKg(item.kg)} kg"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Total ${formatKg(request.totalKg)} kg · ${request.address}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            request.proposedDate?.let {
                Text(
                    "Collector proposed: ${formatDate(it)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            request.collectorResponse?.takeIf { it.isNotBlank() }?.let {
                Text("“$it”", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Outlined.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text("No requests yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Requests you make from Request Pickup will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Pickup dates are UTC-midnight millis, so format in UTC. */
private fun formatDate(utcMillis: Long): String =
    SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(utcMillis)

private fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(Locale.US, "%.1f", kg)

// ---------- Previews ----------

@Preview(showSystemUi = true, name = "My Requests")
@Composable
private fun MyRequestsPreview() {
    EcoConnectTheme { MyRequestsScreen(requests = FakeData.requests, pendingCount = 1, syncing = false, onSyncNow = {}) }
}

@Preview(showSystemUi = true, name = "My Requests – syncing")
@Composable
private fun MyRequestsSyncingPreview() {
    EcoConnectTheme { MyRequestsScreen(requests = FakeData.requests, pendingCount = 1, syncing = true, onSyncNow = {}) }
}

@Preview(showSystemUi = true, name = "My Requests – empty")
@Composable
private fun MyRequestsEmptyPreview() {
    EcoConnectTheme { MyRequestsScreen(requests = emptyList(), pendingCount = 0, syncing = false, onSyncNow = {}) }
}
