package ph.edu.batstateu.ecoconnect.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.totalKg
import ph.edu.batstateu.ecoconnect.ui.components.SyncStatusChip
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailsScreen(
    request: CollectionRequestEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Request details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SyncStatusChip(request.syncStatus, modifier = Modifier.padding(top = 8.dp))

            Section("Pickup") {
                Field("Date", formatPickupDate(request.pickupDate))
                Field("Time", request.pickupTime)
                Field("Address", request.address)
                request.notes?.takeIf { it.isNotBlank() }?.let { Field("Notes", it) }
            }

            Section("Waste") {
                request.wasteItems.forEach { Field(it.categoryLabel(), "${formatKg(it.kg)} kg") }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Field("Total", "${formatKg(request.totalKg)} kg")
            }

            CollectorResponseNote(request)

            Section("Sync") {
                Field("Created", formatMoment(request.createdAt))
                Field("Sent at", request.sentAt?.let(::formatMoment) ?: "Not sent yet")
                request.sentAt?.let { Field("Upload delay", formatDuration(it - request.createdAt)) }
                Field("Request ID", request.id)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f),
        )
    }
}

// ---------- Previews ----------

@Preview(showSystemUi = true, name = "Request details – sent")
@Composable
private fun RequestDetailsSentPreview() {
    val sent = FakeData.requests[2].let { it.copy(sentAt = it.createdAt + 4_200) }
    EcoConnectTheme { RequestDetailsScreen(request = sent, onBack = {}) }
}

@Preview(showSystemUi = true, name = "Request details – pending")
@Composable
private fun RequestDetailsPendingPreview() {
    val pending = FakeData.requests.first { it.syncStatus == SyncStatus.PENDING_SUBMISSION }
    EcoConnectTheme { RequestDetailsScreen(request = pending, onBack = {}) }
}
