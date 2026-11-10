package ph.edu.batstateu.ecoconnect.ui.collector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.data.local.totalKg
import ph.edu.batstateu.ecoconnect.ui.components.SyncStatusChip
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.requests.describe
import ph.edu.batstateu.ecoconnect.ui.requests.formatKg
import ph.edu.batstateu.ecoconnect.ui.requests.formatPickupDate
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme

/** Requester, address, pickup date/time, waste items with kg, total and status. */
@Composable
fun CollectorRequestCard(request: CollectionRequestEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    request.requesterName ?: "Resident",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SyncStatusChip(request.syncStatus)
            }
            IconLine(Icons.Outlined.Place, request.address)
            IconLine(
                Icons.Outlined.CalendarMonth,
                if (request.syncStatus == SyncStatus.RESCHEDULED && request.proposedDate != null) {
                    "${formatPickupDate(request.proposedDate)} (proposed) · ${request.pickupTime}"
                } else {
                    "${formatPickupDate(request.pickupDate)} · ${request.pickupTime}"
                },
            )
            Text(
                request.wasteItems.joinToString { it.describe() },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "Total ${formatKg(request.totalKg)} kg",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Preview(showBackground = true, name = "Collector request card")
@Composable
private fun CollectorRequestCardPreview() {
    EcoConnectTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollectorRequestCard(FakeData.collectorNew.first(), onClick = {})
            CollectorRequestCard(FakeData.requests[2], onClick = {})
        }
    }
}
