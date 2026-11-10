package ph.edu.batstateu.ecoconnect.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.local.SyncStatus
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape
import ph.edu.batstateu.ecoconnect.ui.theme.StatusAccepted
import ph.edu.batstateu.ecoconnect.ui.theme.StatusAcceptedContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusCollected
import ph.edu.batstateu.ecoconnect.ui.theme.StatusCollectedContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusDeclined
import ph.edu.batstateu.ecoconnect.ui.theme.StatusDeclinedContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPending
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPendingContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusRescheduled
import ph.edu.batstateu.ecoconnect.ui.theme.StatusRescheduledContainer
import ph.edu.batstateu.ecoconnect.ui.theme.StatusSent
import ph.edu.batstateu.ecoconnect.ui.theme.StatusSentContainer

/** Human-readable label for a [SyncStatus]. */
val SyncStatus.label: String
    get() = when (this) {
        SyncStatus.PENDING_SUBMISSION -> "Pending Submission"
        SyncStatus.SENT -> "Sent"
        SyncStatus.ACCEPTED -> "Accepted"
        SyncStatus.DECLINED -> "Declined"
        SyncStatus.RESCHEDULED -> "Rescheduled"
        SyncStatus.COLLECTED -> "Collected"
    }

private data class StatusStyle(val icon: ImageVector, val content: Color, val container: Color)

private fun SyncStatus.style(): StatusStyle = when (this) {
    SyncStatus.PENDING_SUBMISSION -> StatusStyle(Icons.Outlined.HourglassTop, StatusPending, StatusPendingContainer)
    SyncStatus.SENT -> StatusStyle(Icons.Outlined.CloudUpload, StatusSent, StatusSentContainer)
    SyncStatus.ACCEPTED -> StatusStyle(Icons.Outlined.CheckCircle, StatusAccepted, StatusAcceptedContainer)
    SyncStatus.DECLINED -> StatusStyle(Icons.Outlined.Block, StatusDeclined, StatusDeclinedContainer)
    SyncStatus.RESCHEDULED -> StatusStyle(Icons.Outlined.EventRepeat, StatusRescheduled, StatusRescheduledContainer)
    SyncStatus.COLLECTED -> StatusStyle(Icons.Outlined.TaskAlt, StatusCollected, StatusCollectedContainer)
}

@Composable
fun SyncStatusChip(status: SyncStatus, modifier: Modifier = Modifier) {
    val style = status.style()
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = style.container,
        contentColor = style.content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(style.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(status.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SyncStatusChipPreview() {
    EcoConnectTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SyncStatus.entries.forEach { SyncStatusChip(it) }
        }
    }
}
