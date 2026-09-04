package com.marbleng.app.ui

// MARBLE_QR_CAMERA_V123 — the second QR intake.
//
// MarbleNG could already read a QR code out of a picture (screenshot, saved photo, a photo of
// somebody's screen) through the system image picker, which needs no permission at all. What it
// could not do is read the code that is in front of the user: a sticker on a box, a poster, a
// friend's screen. This is that path — a live viewfinder that decodes on-device with the same ZXing
// core the gallery path uses.
//
// Two rules it never breaks:
//   • nothing leaves the phone. There is no ML model to download, no cloud decode, no frame is ever
//     written to storage; each YUV frame goes straight into the binarizer and is dropped;
//   • the camera is only ever open while this sheet is composed. The permission is asked for when
//     the user opens the scanner, the provider is unbound when the sheet closes, and a denial falls
//     back to the gallery path instead of dead-ending.

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.marbleng.app.core.QrImageDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR scanner sheet.
 *
 * [onDecoded] fires exactly once, on the main thread, with the raw payload of the first code that
 * decoded; the caller decides what a share link or a subscription URL means. [onPickFromGallery] is
 * the escape hatch shown when the camera is unavailable or its permission was refused.
 */
@Composable
internal fun MarbleQrScannerSheet(
    onDismiss: () -> Unit,
    onDecoded: (String) -> Unit,
    onPickFromGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed
        asked = true
    }

    LaunchedEffect(Unit) {
        if (!granted && hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Aether.Void)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (granted && hasCamera && lifecycleOwner != null) {
                MarbleQrViewfinder(
                    lifecycleOwner = lifecycleOwner,
                    onDecoded = onDecoded,
                    modifier = Modifier.fillMaxSize()
                )
                MarbleQrFraming()
            } else {
                MarbleQrCameraUnavailable(
                    denied = asked || !hasCamera,
                    onPickFromGallery = onPickFromGallery
                )
            }

            PrismIconButton(
                onClick = onDismiss,
                tone = Aether.Ink,
                size = 40.dp,
                descriptiveLabel = "Close",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Text("✕", color = Aether.Ink, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The framing reticle, the hint and the "use the gallery instead" affordance. */
@Composable
private fun MarbleQrFraming() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(248.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(2.dp, Aether.Cyan.copy(alpha = .85f), RoundedCornerShape(28.dp))
        )
        Spacer(Modifier.height(20.dp))
        Text(
            trx("Point the camera at the QR code"),
            color = Aether.Ink,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            trx("Decoded on this device • nothing is uploaded"),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

/** Shown while the permission is pending, and as the fallback when it is refused. */
@Composable
private fun MarbleQrCameraUnavailable(
    denied: Boolean,
    onPickFromGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            trx(if (denied) "Camera unavailable" else "Waiting for camera permission"),
            color = Aether.Ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            trx(
                if (denied) {
                    "You can still read a code from a screenshot or photo."
                } else {
                    "Marble only reads codes while this screen is open."
                }
            ),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        if (denied) {
            Spacer(Modifier.height(18.dp))
            PrismIconButton(
                onClick = onPickFromGallery,
                tone = Aether.Cyan,
                size = 46.dp,
                descriptiveLabel = "Pick an image"
            ) {
                Text(
                    trx("Gallery"),
                    color = Aether.Cyan,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * The live viewfinder plus its analysis pipe.
 *
 * CameraX owns the surface and the frame queue; the only work Marble does per frame is handing the
 * Y plane to ZXing on a single background thread with `STRATEGY_KEEP_ONLY_LATEST`, so a slow decode
 * drops frames instead of building a backlog. The first successful decode wins and every later
 * frame is ignored — a code held still would otherwise fire thirty callbacks a second.
 */
@Composable
private fun MarbleQrViewfinder(
    lifecycleOwner: LifecycleOwner,
    onDecoded: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val fired = remember { AtomicBoolean(false) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        onDispose {
            // Unbind first: the analyzer thread must not outlive the surface it feeds. The provider
            // future is answered on the main executor rather than blocked on here.
            runCatching {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    { runCatching { future.get().unbindAll() } },
                    ContextCompat.getMainExecutor(context)
                )
            }
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                // COMPATIBLE renders through a TextureView: it survives the dialog window and the
                // odd GPU driver that refuses a SurfaceView inside one.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener(
                {
                    val provider = runCatching { providerFuture.get() }.getOrNull()
                        ?: return@addListener
                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        try {
                            if (fired.get()) return@setAnalyzer
                            val plane = proxy.planes.firstOrNull() ?: return@setAnalyzer
                            val buffer = plane.buffer
                            val luminance = ByteArray(buffer.remaining()).also { buffer.get(it) }
                            val payload = QrImageDecoder.decodeLuminance(
                                yuv = luminance,
                                rowStride = plane.rowStride,
                                width = proxy.width,
                                height = proxy.height
                            )
                            if (!payload.isNullOrBlank() && fired.compareAndSet(false, true)) {
                                // The analyzer runs on its own thread; the payload closes a sheet
                                // and writes to the library, both of which belong to the main
                                // thread.
                                mainHandler.post { onDecoded(payload) }
                            }
                        } finally {
                            proxy.close()
                        }
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    )
}

/**
 * Walks a (possibly wrapped) Context back to the host that owns a lifecycle.
 *
 * A Compose `Dialog` hands its content a themed wrapper, not the activity, so `LocalContext` here is
 * not a [LifecycleOwner]. CameraX binds to a lifecycle, and the activity's is the correct one: the
 * camera is released with the screen that opened it.
 */
private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}
