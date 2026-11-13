package ph.edu.batstateu.ecoconnect.ui.scan

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.scan.ScanResult
import ph.edu.batstateu.ecoconnect.ui.components.UnsureStyle
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen500
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape
import ph.edu.batstateu.ecoconnect.ui.theme.StatusDeclined
import ph.edu.batstateu.ecoconnect.ui.theme.StatusPending
import ph.edu.batstateu.ecoconnect.ui.viewmodel.ScanUiState
import java.util.Locale
import kotlin.math.roundToInt

/** Whether the live camera can be shown. */
enum class CameraStatus { Starting, Ready, NeedsPermission, Denied, Unavailable }

/**
 * Waste scanner. [viewfinder] draws the live camera (a slot, so previews don't need CameraX).
 * Point at one item → shutter → Gemini's category, confidence and reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    state: ScanUiState,
    cameraStatus: CameraStatus,
    isOnline: Boolean,
    torchOn: Boolean,
    hasFlash: Boolean,
    viewfinder: @Composable (Modifier) -> Unit,
    onCapture: () -> Unit,
    onPickFromGallery: () -> Unit,
    onToggleTorch: () -> Unit,
    onScanAgain: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Scan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state is ScanUiState.Live && cameraStatus == CameraStatus.Ready && hasFlash) {
                        IconButton(onClick = onToggleTorch) {
                            Icon(
                                if (torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                                contentDescription = if (torchOn) "Turn flashlight off" else "Turn flashlight on",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CameraArea(
                state = state,
                cameraStatus = cameraStatus,
                viewfinder = viewfinder,
                onGrantPermission = onGrantPermission,
                onOpenSettings = onOpenSettings,
            )
            BottomCard(
                state = state,
                canCapture = isOnline && cameraStatus == CameraStatus.Ready,
                isOnline = isOnline,
                onCapture = onCapture,
                onPickFromGallery = onPickFromGallery,
                onScanAgain = onScanAgain,
            )
            Text(
                "Test mode · Photos are sent to Google Gemini for analysis and are not stored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

// ---------- Camera area ----------

@Composable
private fun CameraArea(
    state: ScanUiState,
    cameraStatus: CameraStatus,
    viewfinder: @Composable (Modifier) -> Unit,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val photo = when (state) {
        is ScanUiState.Analyzing -> state.photo
        is ScanUiState.Done -> state.photo
        is ScanUiState.Failed -> state.photo
        ScanUiState.Live -> null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Color(0xFF101410)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = "Captured photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            cameraStatus == CameraStatus.Ready -> viewfinder(Modifier.fillMaxSize())
            cameraStatus == CameraStatus.Starting -> CircularProgressIndicator(color = Color.White)
            else -> CameraBlocked(cameraStatus, onGrantPermission, onOpenSettings)
        }
        if (photo != null || cameraStatus == CameraStatus.Ready) {
            CornerBrackets(Modifier.fillMaxSize().padding(28.dp))
        }
        if (state is ScanUiState.Analyzing) ScanLine(Modifier.fillMaxSize().padding(horizontal = 28.dp))
    }
}

@Composable
private fun CameraBlocked(status: CameraStatus, onGrantPermission: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        Icon(Icons.Outlined.NoPhotography, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(
            when (status) {
                CameraStatus.Unavailable -> "No camera available on this device. You can still choose a photo from the gallery."
                CameraStatus.Denied -> "Camera access is turned off. Allow it in Settings to scan live, or choose a photo from the gallery."
                else -> "EcoConnect needs the camera to scan waste items."
            },
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        when (status) {
            CameraStatus.NeedsPermission -> Button(onClick = onGrantPermission, shape = PillShape) { Text("Allow camera") }
            CameraStatus.Denied -> Button(onClick = onOpenSettings, shape = PillShape) { Text("Open settings") }
            else -> Unit
        }
    }
}

@Composable
private fun CornerBrackets(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val len = size.minDimension * 0.18f
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        val color = EcoGreen500
        val w = size.width
        val h = size.height
        // top-left, top-right, bottom-left, bottom-right
        listOf(
            Offset(0f, 0f) to (1f to 1f),
            Offset(w, 0f) to (-1f to 1f),
            Offset(0f, h) to (1f to -1f),
            Offset(w, h) to (-1f to -1f),
        ).forEach { (corner, dir) ->
            drawLine(color, corner, corner + Offset(len * dir.first, 0f), stroke.width, StrokeCap.Round)
            drawLine(color, corner, corner + Offset(0f, len * dir.second), stroke.width, StrokeCap.Round)
        }
    }
}

@Composable
private fun ScanLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanLine",
    )
    Canvas(modifier) {
        val y = size.height * progress
        drawRect(
            brush = Brush.verticalGradient(
                listOf(EcoGreen500.copy(alpha = 0.45f), Color.Transparent),
                startY = y,
                endY = y + size.height * 0.25f,
            ),
            topLeft = Offset(0f, y),
            size = Size(size.width, size.height * 0.25f),
        )
        drawLine(EcoGreen500, Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
    }
}

// ---------- Bottom card ----------

@Composable
private fun BottomCard(
    state: ScanUiState,
    canCapture: Boolean,
    isOnline: Boolean,
    onCapture: () -> Unit,
    onPickFromGallery: () -> Unit,
    onScanAgain: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (state) {
                ScanUiState.Live -> LiveControls(canCapture, isOnline, onCapture, onPickFromGallery)
                is ScanUiState.Analyzing -> {
                    Text("Analyzing with Gemini…", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is ScanUiState.Done -> {
                    ResultContent(state.result, state.roundTripMs)
                    ScanAgainButton(onScanAgain)
                }
                is ScanUiState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = StatusDeclined)
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                    }
                    ScanAgainButton(onScanAgain)
                }
            }
        }
    }
}

@Composable
private fun LiveControls(canCapture: Boolean, isOnline: Boolean, onCapture: () -> Unit, onPickFromGallery: () -> Unit) {
    if (!isOnline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = StatusPending, modifier = Modifier.size(18.dp))
            Text(
                "You're offline. The scanner needs internet.",
                color = StatusPending,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    } else {
        Text(
            "Point the camera at one waste item, then tap the button.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = onPickFromGallery, enabled = isOnline, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Choose from gallery")
        }
        Spacer(Modifier.weight(1f))
        ShutterButton(enabled = canCapture, onClick = onCapture)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(52.dp))
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .size(76.dp)
            .border(4.dp, color, CircleShape),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(58.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun ResultContent(result: ScanResult, roundTripMs: Long) {
    val category = result.category
    val icon: ImageVector = category?.icon ?: UnsureStyle.icon
    val accent = category?.accent ?: UnsureStyle.accent
    val strong = category?.strong ?: UnsureStyle.strong

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = strong)
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(result.itemName, style = MaterialTheme.typography.titleMedium)
            Text(
                category?.label ?: "Not sure",
                style = MaterialTheme.typography.titleSmall,
                color = strong,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${(result.confidence * 100).roundToInt()}%", style = MaterialTheme.typography.titleLarge, color = strong)
            Text("confidence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (result.isUnsure) {
        val guess = WasteCategory.fromId(result.rawCategory)
        Text(
            if (guess != null) {
                "Low confidence. Best guess: ${guess.label}. Try a clearer photo of a single item, or check the Waste Guide."
            } else {
                "Couldn't tell what this is. Try a clearer photo of a single item, or check the Waste Guide."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (result.reason.isNotBlank()) {
        Text(result.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        "Answered in ${seconds(roundTripMs)}" +
            (result.latencyMs?.let { " (Gemini ${seconds(it)})" } ?: "") +
            (result.model?.let { " · $it" } ?: ""),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ScanAgainButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = PillShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Scan again", modifier = Modifier.padding(start = 8.dp))
    }
}

private fun seconds(ms: Long) = String.format(Locale.US, "%.1f s", ms / 1000.0)

// ---------- Previews ----------

private val previewPhoto: Bitmap by lazy { Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF7FA6C9.toInt()) } }

private val previewResult = ScanResult(
    category = WasteCategory.RECYCLABLE,
    itemName = "Plastic bottle",
    confidence = 0.94,
    reason = "Clear PET drinking bottle; rinse it and put it with recyclables.",
    model = "gemini-3.6-flash",
    latencyMs = 1_420,
    rawCategory = "RECYCLABLE",
)

@Composable
private fun PreviewScan(state: ScanUiState, cameraStatus: CameraStatus = CameraStatus.Ready, isOnline: Boolean = true) {
    EcoConnectTheme {
        ScanScreen(
            state = state,
            cameraStatus = cameraStatus,
            isOnline = isOnline,
            torchOn = false,
            hasFlash = true,
            viewfinder = { Box(it.background(Color(0xFF3A4A3A))) },
            onCapture = {}, onPickFromGallery = {}, onToggleTorch = {}, onScanAgain = {},
            onGrantPermission = {}, onOpenSettings = {}, onBack = {},
        )
    }
}

@Preview(showSystemUi = true, name = "Scan – live")
@Composable
private fun ScanLivePreview() = PreviewScan(ScanUiState.Live)

@Preview(showSystemUi = true, name = "Scan – result")
@Composable
private fun ScanResultPreview() = PreviewScan(ScanUiState.Done(previewPhoto, previewResult, 2_050))

@Preview(showSystemUi = true, name = "Scan – unsure")
@Composable
private fun ScanUnsurePreview() = PreviewScan(
    ScanUiState.Done(previewPhoto, previewResult.copy(category = null, itemName = "Mixed items", confidence = 0.31), 1_900),
)

@Preview(showSystemUi = true, name = "Scan – analyzing")
@Composable
private fun ScanAnalyzingPreview() = PreviewScan(ScanUiState.Analyzing(previewPhoto))

@Preview(showSystemUi = true, name = "Scan – no permission, offline")
@Composable
private fun ScanNoPermissionPreview() = PreviewScan(ScanUiState.Live, CameraStatus.NeedsPermission, isOnline = false)
