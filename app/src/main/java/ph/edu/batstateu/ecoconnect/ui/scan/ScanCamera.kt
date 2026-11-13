package ph.edu.batstateu.ecoconnect.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ph.edu.batstateu.ecoconnect.data.scan.MAX_IMAGE_SIDE
import ph.edu.batstateu.ecoconnect.data.scan.scaledSize
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Live back-camera preview + still capture with CameraX. One instance per scanner screen;
 * [bind] while the screen is visible, [unbind] when it leaves.
 */
class ScanCamera {
    private val preview = Preview.Builder().build().apply {
        setSurfaceProvider { request -> _surfaceRequest.value = request }
    }
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    /** Feed for `CameraXViewfinder`. */
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _hasFlash = MutableStateFlow(false)
    val hasFlash: StateFlow<Boolean> = _hasFlash.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    /** Returns false if no usable camera (e.g. emulator without one). */
    suspend fun bind(context: Context, owner: LifecycleOwner): Boolean = try {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context)
        provider = cameraProvider
        val selector = when {
            cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> null
        }
        if (selector == null) {
            false
        } else {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(owner, selector, preview, imageCapture)
            _hasFlash.value = camera?.cameraInfo?.hasFlashUnit() == true
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "Camera bind failed: ${e.javaClass.simpleName}")
        false
    }

    fun unbind() {
        runCatching { provider?.unbindAll() }
        camera = null
        _surfaceRequest.value = null
    }

    fun setTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
    }

    /** Captures one frame, upright and downscaled. */
    suspend fun capture(context: Context): Bitmap {
        val proxy = suspendCancellableCoroutine { cont ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        cont.resume(image)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                },
            )
        }
        return withContext(Dispatchers.Default) {
            proxy.use { image ->
                val rotation = image.imageInfo.rotationDegrees
                image.toBitmap().downscaled().rotated(rotation)
            }
        }
    }

    private companion object {
        const val TAG = "ScanCamera"
    }
}

// ---------- Bitmap helpers ----------

fun Bitmap.downscaled(maxSide: Int = MAX_IMAGE_SIDE): Bitmap {
    val (w, h) = scaledSize(width, height, maxSide)
    return if (w == width && h == height) this else Bitmap.createScaledBitmap(this, w, h, true)
}

fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees % 360 == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.toJpeg(quality: Int = 80): ByteArray =
    ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

/** Decodes a gallery image with subsampling so huge photos don't run out of memory. */
suspend fun decodeGalleryImage(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.downscaled()
    } catch (e: Exception) {
        null
    }
}
