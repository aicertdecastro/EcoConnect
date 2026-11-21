package ph.edu.batstateu.ecoconnect.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.bluetooth.referenceCode
import ph.edu.batstateu.ecoconnect.data.local.CollectionReceiptEntity
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.StatusCollected
import ph.edu.batstateu.ecoconnect.ui.theme.StatusCollectedContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPending
import java.text.SimpleDateFormat
import java.util.Locale

/** Bluetooth confirmation may start (or be repeated to fill a missing receipt on the other phone). */
fun canConfirmPickup(request: CollectionRequestEntity, receipt: CollectionReceiptEntity?): Boolean =
    request.syncStatus == SyncStatus.ACCEPTED || request.syncStatus == SyncStatus.RESCHEDULED ||
        (request.syncStatus == SyncStatus.COLLECTED && receipt != null)

/** "Collected on …" plus whether the receipt reached the server yet. */
@Composable
fun ReceiptSection(receipt: CollectionReceiptEntity, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StatusCollectedContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = StatusCollected)
                Text(
                    "Collected on ${SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US).format(receipt.confirmedAt)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = StatusCollected,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                "Reference ${referenceCode(receipt.requestId)}" +
                    (receipt.durationMs?.let { " · confirmed in ${String.format(Locale.US, "%.1f", it / 1000.0)} s" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (receipt.uploaded) Icons.Outlined.CloudDone else Icons.Outlined.CloudUpload,
                    contentDescription = null,
                    tint = if (receipt.uploaded) StatusCollected else StatusPending,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (receipt.uploaded) "Receipt uploaded" else "Receipt waiting for internet",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (receipt.uploaded) StatusCollected else StatusPending,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Preview(name = "Receipt – waiting")
@Composable
private fun ReceiptWaitingPreview() {
    EcoConnectTheme {
        ReceiptSection(CollectionReceiptEntity("r1", "3f9a1c00-0000", "u", "c", 1_795_300_000_000L, uploaded = false, durationMs = 2_340))
    }
}

@Preview(name = "Receipt – uploaded")
@Composable
private fun ReceiptUploadedPreview() {
    EcoConnectTheme {
        ReceiptSection(CollectionReceiptEntity("r1", "3f9a1c00-0000", "u", "c", 1_795_300_000_000L, uploaded = true, durationMs = 2_340))
    }
}
