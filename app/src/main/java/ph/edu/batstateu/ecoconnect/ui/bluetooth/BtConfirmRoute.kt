package ph.edu.batstateu.ecoconnect.ui.bluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ph.edu.batstateu.ecoconnect.EcoConnectApp
import ph.edu.batstateu.ecoconnect.bluetooth.BluetoothController
import ph.edu.batstateu.ecoconnect.bluetooth.BtAvailability
import ph.edu.batstateu.ecoconnect.bluetooth.BtRole
import ph.edu.batstateu.ecoconnect.bluetooth.referenceCode
import ph.edu.batstateu.ecoconnect.bluetooth.requiredBtPermissions

/**
 * Bluetooth confirmation for one request. Before any Bluetooth call it checks, in order:
 * adapter present → permissions → Bluetooth on. Leaving the screen cancels the session.
 */
@Composable
fun BtConfirmRoute(
    role: BtRole,
    requestId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BtConfirmViewModel = viewModel(
        key = "bt-$role-$requestId",
        factory = BtConfirmViewModel.factory(role, requestId),
    ),
) {
    val context = LocalContext.current
    val controller = remember { (context.applicationContext as EcoConnectApp).bluetoothController }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val request by viewModel.request.collectAsStateWithLifecycle()

    var denied by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var need by remember { mutableStateOf(controller.setupNeed(role, denied, permanentlyDenied)) }
    val recheck = { need = controller.setupNeed(role, denied, permanentlyDenied) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val missing = result.filterValues { !it }.keys
        if (missing.isEmpty()) {
            denied = false
            permanentlyDenied = false
        } else {
            denied = true
            // No rationale after a denial = "Don't ask again" (or denied twice): only Settings helps.
            val activity = context.findActivity()
            permanentlyDenied = activity != null && missing.none { activity.shouldShowRequestPermissionRationale(it) }
        }
        recheck()
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { recheck() }
    val visibleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // On success the result code is the visibility duration in seconds.
        if (result.resultCode > 0) viewModel.onVisible(result.resultCode)
    }

    // Back from Settings, or Bluetooth toggled from the quick settings panel.
    LifecycleResumeEffect(Unit) {
        recheck()
        onPauseOrDispose { }
    }

    // Start once everything is ready; fall back to the gate if Bluetooth goes away mid-wait.
    LaunchedEffect(need) {
        if (need == null) viewModel.start() else viewModel.stopIfNotFinished()
    }

    // Ticks the "visible for N s" countdown.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while ((state as? BtConfirmUiState.CollectorWaiting)?.visibleUntil != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val close = {
        viewModel.cancel()
        onClose()
    }
    BackHandler(onBack = close)

    val shown = need?.takeIf { !state.isFinished }?.let { BtConfirmUiState.Setup(it) } ?: state
    BtConfirmScreen(
        role = role,
        referenceCode = referenceCode(requestId),
        request = request,
        state = shown,
        now = now,
        actions = BtConfirmActions(
            onBack = close,
            onGrantPermission = { permissionLauncher.launch(requiredBtPermissions(role).toTypedArray()) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onTurnOnBluetooth = {
                runCatching { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                    .onFailure { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            onMakeVisible = {
                runCatching {
                    visibleLauncher.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, VISIBLE_SECONDS),
                    )
                }
            },
            onCancel = close,
            onScan = viewModel::scan,
            onDeviceClick = viewModel::connect,
            onRetry = {
                recheck()
                // Bluetooth went off (or permission removed) since: show the gate; it restarts by itself.
                if (need == null) viewModel.retry() else viewModel.reset()
            },
            onChooseAnotherPhone = viewModel::chooseAnotherPhone,
            onDone = close,
        ),
        modifier = modifier,
    )
}

private const val VISIBLE_SECONDS = 120

/** Null when Bluetooth is ready to use. */
private fun BluetoothController.setupNeed(role: BtRole, denied: Boolean, permanentlyDenied: Boolean): BtSetupNeed? = when {
    availability() == BtAvailability.NoAdapter -> BtSetupNeed.NoAdapter
    !hasPermissions(role) -> when {
        permanentlyDenied -> BtSetupNeed.PermissionPermanentlyDenied
        denied -> BtSetupNeed.PermissionDenied
        else -> BtSetupNeed.Permission
    }
    availability() == BtAvailability.Off -> BtSetupNeed.BluetoothOff
    else -> null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
