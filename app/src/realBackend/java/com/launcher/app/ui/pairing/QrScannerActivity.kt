package com.launcher.app.ui.pairing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.launcher.app.R
import com.launcher.ui.theme.LauncherTheme
import java.util.concurrent.Executors

/**
 * Admin-side QR scanner (spec 007 FR-005, T089). CameraX preview + ML Kit
 * barcode detection. On the first successful scan of a `launcher://pair?token=`
 * URI, this Activity routes the deep-link back into [PairingActivity] so the
 * existing claim FSM runs through `PairingViewModel.claimAsAdmin`.
 *
 * Lives in the **realBackend** source set because CameraX + ML Kit are
 * realBackend-only dependencies (see app/build.gradle.kts). mockBackend
 * doesn't need a scanner — its Fake pairing path uses adb deep-links.
 */
class QrScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme(preset = null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ScannerRoot(
                        onScanned = { deepLink ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                                .setPackage(packageName)
                            startActivity(intent)
                            finish()
                        },
                        onClose = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerRoot(
    onScanned: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val activity = context as ComponentActivity
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    when {
        hasPermission -> CameraPreview(onScanned = onScanned, onClose = onClose)
        permissionDenied -> PermissionDeniedScreen(
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", activity.packageName, null))
                activity.startActivity(intent)
            },
            onClose = onClose,
        )
        else -> Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    }
}

@Composable
private fun CameraPreview(
    onScanned: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    // Guard against re-entry — once a valid token is captured we close,
    // but the analyzer can still fire a few frames before camera shuts down.
    var consumed by remember { mutableStateOf(false) }

    val tokenRegex = remember { Regex("^[A-HJ-NP-Z2-9]{6}$") }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val resolution = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build()
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(resolution)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                processImage(imageProxy, scanner, tokenRegex) { deepLink ->
                                    if (consumed) return@processImage
                                    consumed = true
                                    onScanned(deepLink)
                                }
                            }
                        }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analyzer,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.pairing_scan_hint),
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .height(56.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(text = stringResource(R.string.pairing_close), fontSize = 18.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun PermissionDeniedScreen(onOpenSettings: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.pairing_scan_permission_denied),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(56.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Text(text = stringResource(R.string.pairing_scan_open_settings), fontSize = 18.sp)
        }
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(56.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Text(text = stringResource(R.string.pairing_close), fontSize = 18.sp)
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    tokenRegex: Regex,
    onHit: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            for (b in barcodes) {
                val raw = b.rawValue ?: continue
                val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: continue
                if (uri.scheme == "launcher" && uri.host == "pair") {
                    val token = uri.getQueryParameter("token") ?: continue
                    if (tokenRegex.matches(token)) {
                        onHit(raw)
                        return@addOnSuccessListener
                    }
                }
            }
        }
        .addOnCompleteListener { imageProxy.close() }
}
