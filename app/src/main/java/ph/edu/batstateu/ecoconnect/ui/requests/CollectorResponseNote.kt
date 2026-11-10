package ph.edu.batstateu.ecoconnect.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.local.CollectionRequestEntity
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.StatusAccepted
import ph.edu.batstateu.ecoconnect.ui.theme.StatusAcceptedContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusDeclined
import ph.edu.batstateu.ecoconnect.ui.theme.StatusDeclinedContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusRescheduled
import ph.edu.batstateu.ecoconnect.ui.theme.StatusRescheduledContainer

/**
 * The collector's answer, as residents see it:
 * ACCEPTED → collector name + message, DECLINED → reason,
 * RESCHEDULED → highlighted proposed date + message. Nothing for other statuses.
 */
@Composable
fun CollectorResponseNote(request: CollectionRequestEntity, modifier: Modifier = Modifier) {
    val message = request.collectorResponse?.takeIf { it.isNotBlank() }
    val by = request.collectorName?.takeIf { it.isNotBlank() }
    when (request.syncStatus) {
        SyncStatus.ACCEPTED -> NoteBox(
            icon = Icons.Outlined.CheckCircle,
            content = StatusAccepted,
            container = StatusAcceptedContainer,
            headline = if (by != null) "Accepted by $by" else "Accepted by a collector",
            body = message?.let { "“$it”" },
            modifier = modifier,
        )
        SyncStatus.DECLINED -> NoteBox(
            icon = Icons.Outlined.Block,
            content = StatusDeclined,
            container = StatusDeclinedContainer,
            headline = "Declined" + (by?.let { " by $it" } ?: ""),
            body = "Reason: ${message ?: "not given"}",
            modifier = modifier,
        )
        SyncStatus.RESCHEDULED -> NoteBox(
            icon = Icons.Outlined.EventRepeat,
            content = StatusRescheduled,
            container = StatusRescheduledContainer,
            headline = "Proposed new date: " + (request.proposedDate?.let(::formatPickupDate) ?: "—"),
            body = listOfNotNull(by?.let { "From $it" }, message?.let { "“$it”" }).joinToString(" · ").ifBlank { null },
            modifier = modifier,
        )
        else -> Unit
    }
}

@Composable
private fun NoteBox(
    icon: ImageVector,
    content: Color,
    container: Color,
    headline: String,
    body: String?,
    modifier: Modifier = Modifier,
) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Preview(showBackground = true, name = "Collector response notes")
@Composable
private fun CollectorResponseNotePreview() {
    EcoConnectTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FakeData.requests.forEach { CollectorResponseNote(it) }
            CollectorResponseNote(FakeData.requests[1].copy(syncStatus = SyncStatus.DECLINED, collectorResponse = "Outside service area"))
        }
    }
}
