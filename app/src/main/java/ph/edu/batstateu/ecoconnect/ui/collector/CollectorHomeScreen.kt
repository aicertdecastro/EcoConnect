package ph.edu.batstateu.ecoconnect.ui.collector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.auth.AuthState
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.requests.formatLastSynced
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen700
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen800
import ph.edu.batstateu.ecoconnect.ui.theme.HeaderShape
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPending
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPendingContainer
import ph.edu.batstateu.ecoconnect.ui.viewmodel.CollectorHomeUiState
import ph.edu.batstateu.ecoconnect.ui.viewmodel.PickupGroups
import ph.edu.batstateu.ecoconnect.ui.viewmodel.groupPickups

enum class CollectorTab(val label: String) { New("New"), MyPickups("My pickups"), History("History") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectorHomeScreen(
    user: AuthState.SignedIn,
    state: CollectorHomeUiState,
    selectedTab: CollectorTab,
    onTabSelected: (CollectorTab) -> Unit,
    onRefresh: () -> Unit,
    onRequestClick: (requestId: String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Header(user = user, newCount = state.newCount, todayCount = state.todayCount, onSignOut = onSignOut)
            StatusLine(isOnline = state.isOnline, lastUpdatedAt = state.lastUpdatedAt)
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                CollectorTab.entries.forEach { tab ->
                    val count = when (tab) {
                        CollectorTab.New -> state.newCount
                        CollectorTab.MyPickups -> state.myPickups.let { it.overdue.size + it.today.size + it.upcoming.size }
                        CollectorTab.History -> state.history.size
                    }
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(if (count > 0) "${tab.label} ($count)" else tab.label, maxLines = 1) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (selectedTab) {
                        CollectorTab.New -> requestList(state.newRequests, onRequestClick, "No new requests", "New pickup requests from residents appear here.")
                        CollectorTab.MyPickups -> pickupGroups(state.myPickups, onRequestClick)
                        CollectorTab.History -> requestList(state.history, onRequestClick, "No history yet", "Declined and collected requests appear here.")
                    }
                }
            }
        }
    }
}

private fun LazyListScope.requestList(
    requests: List<CollectionRequestEntity>,
    onRequestClick: (String) -> Unit,
    emptyTitle: String,
    emptyBody: String,
) {
    if (requests.isEmpty()) {
        item { EmptyState(emptyTitle, emptyBody) }
    } else {
        items(requests, key = { it.id }) { CollectorRequestCard(it, onClick = { onRequestClick(it.id) }) }
    }
}

private fun LazyListScope.pickupGroups(groups: PickupGroups, onRequestClick: (String) -> Unit) {
    if (groups.isEmpty) {
        item { EmptyState("No pickups yet", "Requests you accept or reschedule appear here.") }
        return
    }
    listOf("Overdue" to groups.overdue, "Today" to groups.today, "Upcoming" to groups.upcoming)
        .filter { it.second.isNotEmpty() || it.first == "Today" }
        .forEach { (title, list) ->
            item(key = "header-$title") {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (title == "Overdue") StatusPending else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (list.isEmpty()) {
                item(key = "empty-$title") {
                    Text(
                        "Nothing scheduled for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.id }) { CollectorRequestCard(it, onClick = { onRequestClick(it.id) }) }
        }
}

@Composable
private fun Header(user: AuthState.SignedIn, newCount: Int, todayCount: Int, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EcoGreen800, HeaderShape)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Profile chip
            Surface(shape = PillShape, color = EcoGreen700, contentColor = Color.White, modifier = Modifier.weight(1f, fill = false)) {
                Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(Color.White.copy(alpha = 0.2f), PillShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            user.fullName?.takeIf { it.isNotBlank() } ?: user.email ?: "Collector",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(user.role.label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
            Box(Modifier.weight(1f))
            IconButton(onClick = onSignOut) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Log out", tint = Color.White)
            }
        }
        Row(Modifier.padding(top = 16.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile(value = newCount, label = "New requests", modifier = Modifier.weight(1f))
            SummaryTile(value = todayCount, label = "Today's pickups", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryTile(value: Int, label: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = Color.White, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusLine(isOnline: Boolean, lastUpdatedAt: Long?) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!isOnline) {
            Surface(color = StatusPendingContainer, contentColor = StatusPending, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Offline — showing saved data", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(
                if (lastUpdatedAt == null) "Not updated yet" else "Last updated: ${formatLastSynced(lastUpdatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---------- Previews ----------

private const val PREVIEW_UPDATED = 1_795_168_320_000L

private val previewState = CollectorHomeUiState(
    newRequests = FakeData.collectorNew,
    myPickups = groupPickups(
        FakeData.requests.filter { it.syncStatus == SyncStatus.ACCEPTED || it.syncStatus == SyncStatus.RESCHEDULED },
        today = FakeData.requests[3].pickupDate,
    ),
    history = listOf(FakeData.requests[1].copy(id = "h1", syncStatus = SyncStatus.DECLINED, collectorResponse = "Outside service area")),
    lastUpdatedAt = PREVIEW_UPDATED,
)

@Preview(showSystemUi = true, name = "Collector home – New")
@Composable
private fun CollectorHomeNewPreview() {
    EcoConnectTheme {
        CollectorHomeScreen(FakeData.collectorUser, previewState, CollectorTab.New, {}, {}, {}, {})
    }
}

@Preview(showSystemUi = true, name = "Collector home – My pickups")
@Composable
private fun CollectorHomeMinePreview() {
    EcoConnectTheme {
        CollectorHomeScreen(FakeData.collectorUser, previewState, CollectorTab.MyPickups, {}, {}, {}, {})
    }
}

@Preview(showSystemUi = true, name = "Collector home – offline, empty history")
@Composable
private fun CollectorHomeOfflinePreview() {
    EcoConnectTheme {
        CollectorHomeScreen(
            FakeData.collectorUser,
            previewState.copy(history = emptyList(), isOnline = false),
            CollectorTab.History, {}, {}, {}, {},
        )
    }
}
