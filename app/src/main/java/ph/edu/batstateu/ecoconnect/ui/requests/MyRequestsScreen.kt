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
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.totalKg
import ph.edu.batstateu.ecoconnect.ui.components.SyncStatusChip
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPending
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPendingContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRequestsScreen(
    requests: List<CollectionRequestEntity>,
    pendingCount: Int,
    isSyncing: Boolean,
    lastSyncedAt: Long?,
    onRetryNow: () -> Unit,
    onRequestClick: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
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
            SyncStatusLine(isSyncing = isSyncing, lastSyncedAt = lastSyncedAt)
            if (pendingCount > 0) {
                PendingBanner(pendingCount = pendingCount, isSyncing = isSyncing, onRetryNow = onRetryNow)
            }
            if (requests.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(requests, key = { it.id }) { RequestCard(it, onClick = { onRequestClick(it.id) }) }
                }
            }
        }
    }
}

/** "Last synced: 7:12 PM" plus a small "Syncing…" indicator while WorkManager is running. */
@Composable
private fun SyncStatusLine(isSyncing: Boolean, lastSyncedAt: Long?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CloudDone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            if (lastSyncedAt == null) "Not synced yet" else "Last synced: ${formatLastSynced(lastSyncedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 6.dp)
                .weight(1f),
        )
        if (isSyncing) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Text(
                "Syncing…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Shown while anything is still on the phone only. "Retry now" is the manual retry from the risk plan. */
@Composable
private fun PendingBanner(pendingCount: Int, isSyncing: Boolean, onRetryNow: () -> Unit) {
    Surface(
        color = StatusPendingContainer,
        contentColor = StatusPending,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                if (pendingCount == 1) "1 request waiting for internet" else "$pendingCount requests waiting for internet",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            )
            TextButton(onClick = onRetryNow, enabled = !isSyncing) { Text("Retry now") }
        }
    }
}

@Composable
private fun RequestCard(request: CollectionRequestEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
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
                    "${formatPickupDate(request.pickupDate)} · ${request.pickupTime}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                )
                SyncStatusChip(request.syncStatus)
            }
            Text(request.wasteItems.joinToString { it.describe() }, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Total ${formatKg(request.totalKg)} kg · ${request.address}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            request.proposedDate?.let {
                Text(
                    "Collector proposed: ${formatPickupDate(it)}",
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

// ---------- Previews ----------

private const val PREVIEW_LAST_SYNC = 1_795_168_320_000L

@Preview(showSystemUi = true, name = "My Requests – waiting for internet")
@Composable
private fun MyRequestsPendingPreview() {
    EcoConnectTheme {
        MyRequestsScreen(FakeData.requests, pendingCount = 1, isSyncing = false, lastSyncedAt = PREVIEW_LAST_SYNC, onRetryNow = {}, onRequestClick = {})
    }
}

@Preview(showSystemUi = true, name = "My Requests – syncing")
@Composable
private fun MyRequestsSyncingPreview() {
    EcoConnectTheme {
        MyRequestsScreen(FakeData.requests, pendingCount = 1, isSyncing = true, lastSyncedAt = PREVIEW_LAST_SYNC, onRetryNow = {}, onRequestClick = {})
    }
}

@Preview(showSystemUi = true, name = "My Requests – all sent")
@Composable
private fun MyRequestsAllSentPreview() {
    EcoConnectTheme {
        MyRequestsScreen(FakeData.requests.drop(1), pendingCount = 0, isSyncing = false, lastSyncedAt = PREVIEW_LAST_SYNC, onRetryNow = {}, onRequestClick = {})
    }
}

@Preview(showSystemUi = true, name = "My Requests – empty")
@Composable
private fun MyRequestsEmptyPreview() {
    EcoConnectTheme {
        MyRequestsScreen(emptyList(), pendingCount = 0, isSyncing = false, lastSyncedAt = null, onRetryNow = {}, onRequestClick = {})
    }
}
