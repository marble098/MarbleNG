package com.marbleng.app.ui

// MARBLE_QR_CAMERA_V123
//
// Scan a config QR code with the camera.
//
// Importing a QR code used to have exactly one door: the system image picker. That is the right
// door for a screenshot, and it stays — but a code is just as often held up on another phone, and
// reaching it meant taking a picture first, in another app, and then coming back. This is the
// second door: a live viewfinder that reads the code as soon as it is in focus.
//
// It is written directly against the platform Camera2 API and ZXing's pure-Java core
// ([com.marbleng.app.core.QrFrameDecoder]) rather than a camera library, so the product adds no
// dependency, no ML model and no third-party runtime for one feature. Nothing leaves the device:
// the frames are decoded in process and are never written to disk.
//
// The viewfinder is deliberately forgiving — it keeps scanning while the sheet is open, it offers
// the gallery inside the sheet so switching doors costs one tap, and every failure states what to
// do next instead of showing a black rectangle.

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.marbleng.app.core.QrFrameDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The camera QR scanner.
 *
 * @param onDecoded called once with the text of the first code that decodes; the caller owns
 *   importing it
 * @param onPickGallery opens the image picker, the other way in
 * @param onDismiss closes the scanner without a result
 */
@Composable
internal fun MarbleQrCameraScanner(
    onDecoded: (String) -> Unit,
    onPickGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scannerState by remember { mutableStateOf(ScannerState.STARTING) }
    // One code is enough: the flag stops the frame loop from firing a second import while the
    // first one is still on its way to the repository.
    val delivered = remember { AtomicBoolean(false) }
    val worker = remember { ScannerWorker(context) { text ->
        if (delivered.compareAndSet(false, true)) onDecoded(text)
    } }

    DisposableEffect(worker) {
        onDispose { worker.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Aether.Void)
    ) {
        AndroidView(
            factory = { viewContext ->
                // The view is named rather than captured as `this`: inside the listener object,
                // `this` is the listener.
                TextureView(viewContext).also { texture ->
                    texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            worker.configureTransform(texture, width, height)
                            worker.start(surface, width, height) { state -> scannerState = state }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            worker.configureTransform(texture, width, height)
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            worker.release()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // The viewfinder: a dimmed surround, a clear window and four corner brackets, so the user
        // knows which part of the frame is being read.
        //
        // The palette is read here, in composable scope: Aether.* are @Composable getters and a
        // DrawScope is not one.
        val bracketTone = Aether.Cyan
        val frameTone = Aether.Cyan.copy(alpha = .55f)
        val scrim = Color.Black.copy(alpha = .38f)
        Canvas(Modifier.fillMaxSize()) {
            val inset = size.minDimension * .13f
            val window = Offset(inset, size.height * .22f)
            val windowSize = Size(
                size.width - inset * 2f,
                size.minDimension * .62f
            )
            val radius = CornerRadius(28.dp.toPx(), 28.dp.toPx())

            drawRoundRect(
                color = scrim,
                topLeft = Offset.Zero,
                size = size
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = .001f),
                topLeft = window,
                size = windowSize,
                cornerRadius = radius
            )
            drawRoundRect(
                color = frameTone,
                topLeft = window,
                size = windowSize,
                cornerRadius = radius,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Corner brackets.
            val arm = windowSize.width * .16f
            val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            val corners = listOf(
                window to Offset(1f, 1f),
                Offset(window.x + windowSize.width, window.y) to Offset(-1f, 1f),
                Offset(window.x, window.y + windowSize.height) to Offset(1f, -1f),
                Offset(window.x + windowSize.width, window.y + windowSize.height) to Offset(-1f, -1f)
            )
            corners.forEach { (corner, direction) ->
                drawLine(
                    color = bracketTone,
                    start = corner,
                    end = Offset(corner.x + arm * direction.x, corner.y),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = bracketTone,
                    start = corner,
                    end = Offset(corner.x, corner.y + arm * direction.y),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        trx("Scan QR code"),
                        color = Aether.Ink,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when (scannerState) {
                            ScannerState.STARTING -> trx("Starting the camera…")
                            ScannerState.SCANNING -> trx("Point the camera at the code")
                            ScannerState.NO_CAMERA -> trx("No camera is available on this device")
                            ScannerState.FAILED -> trx("The camera could not start • use the gallery")
                        },
                        color = if (scannerState == ScannerState.SCANNING) {
                            Aether.InkFaint
                        } else {
                            Aether.Amber
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                PrismIconButton(
                    onClick = onDismiss,
                    size = 40.dp,
                    descriptiveLabel = "Close"
                ) {
                    ScannerCloseGlyph(Aether.Ink, Modifier.size(15.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (scannerState == ScannerState.STARTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Aether.Cyan,
                        strokeWidth = 2.dp
                    )
                }
                PrismButton(
                    label = trx("Choose from gallery"),
                    onClick = onPickGallery,
                    tone = Aether.Cyan,
                    variant = PrismButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
                PrismButton(
                    label = trx("Cancel"),
                    onClick = onDismiss,
                    tone = Aether.InkMuted,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** What the viewfinder is doing right now; drives the one line of copy under the title. */
private enum class ScannerState { STARTING, SCANNING, NO_CAMERA, FAILED }

/** The sheet's close mark, drawn so the scanner needs no bitmap and no font. */
@Composable
private fun ScannerCloseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(
            width = size.minDimension * .12f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * .22f, size.height * .22f),
            end = Offset(size.width * .78f, size.height * .78f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * .78f, size.height * .22f),
            end = Offset(size.width * .22f, size.height * .78f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Owns the camera for the lifetime of the sheet: one background thread for camera callbacks, one
 * image reader, and a frame loop that hands each Y plane to [QrFrameDecoder].
 */
private class ScannerWorker(
    private val context: Context,
    private val onDecoded: (String) -> Unit
) {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var sensorOrientation = 0
    private var frameWidth = 0
    private var frameHeight = 0
    @Volatile
    private var released = false

    /** The frame size the reader is built for, kept so the preview transform can match it. */
    private var previewWidth = 0
    private var previewHeight = 0

    @SuppressLint("MissingPermission") // the caller holds CAMERA before this sheet can open
    fun start(
        previewSurface: SurfaceTexture,
        viewWidth: Int,
        viewHeight: Int,
        onState: (ScannerState) -> Unit
    ) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: run {
            onState(ScannerState.FAILED)
            return
        }
        val cameraId = runCatching {
            manager.getCameraIdList().firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.getCameraIdList().firstOrNull()
        }.getOrNull()
        if (cameraId == null) {
            onState(ScannerState.NO_CAMERA)
            return
        }

        val characteristics = runCatching { manager.getCameraCharacteristics(cameraId) }.getOrNull()
        if (characteristics == null) {
            onState(ScannerState.FAILED)
            return
        }
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val frameSize = map?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.minByOrNull { kotlin.math.abs(it.width * it.height - TARGET_PIXELS) }
        val surfaceSize = map?.getOutputSizes(SurfaceTexture::class.java)
            ?.minByOrNull { kotlin.math.abs(it.width * it.height - TARGET_PIXELS) }
        if (frameSize == null || surfaceSize == null) {
            onState(ScannerState.FAILED)
            return
        }
        frameWidth = frameSize.width
        frameHeight = frameSize.height
        previewWidth = surfaceSize.width
        previewHeight = surfaceSize.height
        previewSurface.setDefaultBufferSize(previewWidth, previewHeight)

        val workerThread = HandlerThread("marble-qr-scanner").apply { start() }
        thread = workerThread
        val workerHandler = Handler(workerThread.looper)
        handler = workerHandler

        val imageReader = ImageReader.newInstance(
            frameWidth,
            frameHeight,
            ImageFormat.YUV_420_888,
            2
        )
        imageReader.setOnImageAvailableListener({ source -> onFrame(source) }, workerHandler)
        reader = imageReader

        runCatching {
            manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        device = camera
                        beginCapture(camera, imageReader.surface, Surface(previewSurface), workerHandler, onState)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        device = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        device = null
                        onState(ScannerState.FAILED)
                    }
                },
                workerHandler
            )
        }.onFailure { onState(ScannerState.FAILED) }
    }

    private fun beginCapture(
        camera: CameraDevice,
        analysisSurface: Surface,
        previewSurface: Surface,
        workerHandler: Handler,
        onState: (ScannerState) -> Unit
    ) {
        runCatching {
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                addTarget(analysisSurface)
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
            }.build()

            camera.createCaptureSession(
                listOf(previewSurface, analysisSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(captureSession: CameraCaptureSession) {
                        session = captureSession
                        runCatching {
                            captureSession.setRepeatingRequest(request, null, workerHandler)
                        }.onFailure { onState(ScannerState.FAILED) }
                        onState(ScannerState.SCANNING)
                    }

                    override fun onConfigureFailed(captureSession: CameraCaptureSession) {
                        onState(ScannerState.FAILED)
                    }
                },
                workerHandler
            )
        }.onFailure { onState(ScannerState.FAILED) }
    }

    /** Decode one frame. The image is closed on every path — a leaked frame stalls the reader. */
    private fun onFrame(source: ImageReader) {
        if (released) return
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer ?: return
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val text = QrFrameDecoder.decode(
                luminance = bytes,
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                sensorOrientation = sensorOrientation
            )
            if (!text.isNullOrBlank()) onDecoded(text)
        } finally {
            runCatching { image.close() }
        }
    }

    /**
     * Centre-crop the sensor image into the view. The back camera of a portrait phone delivers a
     * landscape frame, so the texture is rotated by 90° before it is scaled to cover.
     */
    fun configureTransform(view: TextureView, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0 || previewWidth <= 0) return
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewHeight.toFloat(), previewWidth.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        val scale = maxOf(
            viewHeight.toFloat() / previewHeight,
            viewWidth.toFloat() / previewWidth
        )
        matrix.postScale(scale, scale, centerX, centerY)
        view.setTransform(matrix)
    }

    fun release() {
        released = true
        runCatching { session?.close() }
        session = null
        runCatching { device?.close() }
        device = null
        runCatching { reader?.close() }
        reader = null
        runCatching { thread?.quitSafely() }
        thread = null
        handler = null
    }

    private companion object {
        /** Roughly 720p: dense enough for a small code, cheap enough to decode every frame. */
        const val TARGET_PIXELS = 1280 * 720
    }
}
