package ph.edu.batstateu.ecoconnect.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.ui.viewmodel.ScanUiState
import ph.edu.batstateu.ecoconnect.ui.viewmodel.ScanViewModel

@Composable
fun ScanRoute(
    onBack: () -> Unit,
    onRequestPickup: (categoryId: String) -> Unit,
    onOpenGuide: (categoryId: String) -> Unit,
    onStartQuiz: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    val camera = remember { ScanCamera() }
    val hasFlash by camera.hasFlash.collectAsStateWithLifecycle()
    var torchOn by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(context.cameraPermission()) }
    var cameraStatus by remember { mutableStateOf(CameraStatus.Starting) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = when {
            granted -> CameraStatus.Ready
            // "Don't ask again" (or denied twice): only Settings can turn it back on.
            context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false -> CameraStatus.Denied
            else -> CameraStatus.NeedsPermission
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bitmap = decodeGalleryImage(context, uri)
                if (bitmap != null) viewModel.analyze(bitmap) else viewModel.showError("That image couldn't be opened.")
            }
        }
    }

    // Ask once when the scanner opens; re-check when returning from Settings.
    LaunchedEffect(Unit) {
        if (permission == CameraStatus.NeedsPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LifecycleResumeEffect(Unit) {
        if (context.cameraPermission() == CameraStatus.Ready) permission = CameraStatus.Ready
        onPauseOrDispose { }
    }

    // Bind the camera while permitted and on screen.
    LaunchedEffect(permission) {
        if (permission != CameraStatus.Ready) {
            cameraStatus = permission
            return@LaunchedEffect
        }
        cameraStatus = CameraStatus.Starting
        cameraStatus = if (camera.bind(context, lifecycleOwner)) CameraStatus.Ready else CameraStatus.Unavailable
    }
    DisposableEffect(Unit) { onDispose { camera.unbind() } }

    ScanScreen(
        state = state,
        cameraStatus = cameraStatus,
        isOnline = isOnline,
        torchOn = torchOn,
        hasFlash = hasFlash,
        viewfinder = { m ->
            val request by camera.surfaceRequest.collectAsStateWithLifecycle()
            request?.let { CameraXViewfinder(surfaceRequest = it, contentScale = ContentScale.Crop, modifier = m) } ?: Box(m)
        },
        onCapture = {
            if (state is ScanUiState.Live) {
                scope.launch {
                    try {
                        val photo = camera.capture(context)
                        if (torchOn) {
                            torchOn = false
                            camera.setTorch(false)
                        }
                        viewModel.analyze(photo)
                    } catch (e: Exception) {
                        Log.w("ScanRoute", "Capture failed: ${e.javaClass.simpleName}")
                        viewModel.showError("Couldn't take the photo. Try again.")
                    }
                }
            }
        },
        onPickFromGallery = {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onToggleTorch = {
            torchOn = !torchOn
            camera.setTorch(torchOn)
        },
        onScanAgain = viewModel::scanAgain,
        onGrantPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onBack = onBack,
        onRequestPickup = onRequestPickup,
        onOpenGuide = onOpenGuide,
        onStartQuiz = onStartQuiz,
        modifier = modifier,
    )
}

private fun Context.cameraPermission(): CameraStatus =
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        CameraStatus.Ready
    } else {
        CameraStatus.NeedsPermission
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
