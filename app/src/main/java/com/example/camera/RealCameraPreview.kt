package com.example.camera

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Composable Viewfinder Kamera Nyata menggunakan CameraX dan ML Kit Barcode Scanning.
 * Mendeteksi QR Code dan Barcode langsung dari umpan kamera.
 */
@Composable
fun RealCameraPreview(
    onQrDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val barcodeScannerRef = remember { mutableStateOf<com.google.mlkit.vision.barcode.BarcodeScanner?>(null) }
    val lastScannedTimestamp = remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderRef.value?.unbindAll()
            } catch (_: Exception) {
                // ignore
            }
            barcodeScannerRef.value?.close() // lepas resource ML Kit saat kamera ditutup
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    // Menggunakan COMPATIBLE (TextureView) untuk mencegah error BufferQueue has been abandoned pada SurfaceView di emulator/Compose
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProviderRef.value = cameraProvider

                        val preview = Preview.Builder().build().also {
                            try {
                                it.surfaceProvider = previewView.surfaceProvider
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // Hanya QR code (barcode jenis lain diabaikan supaya tidak ada data yang bukan dari QR)
                        val barcodeOptions = BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                        val barcodeScanner = BarcodeScanning.getClient(barcodeOptions)
                        barcodeScannerRef.value = barcodeScanner

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        var isProcessing = false

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            try {
                                processImageProxy(
                                    barcodeScanner = barcodeScanner,
                                    imageProxy = imageProxy,
                                    isProcessing = isProcessing,
                                    setProcessing = { isProcessing = it },
                                    onBarcodeDetected = { qrCode ->
                                        val now = System.currentTimeMillis()
                                        // Throttle scan to at most once every 1200ms
                                        if (now - lastScannedTimestamp.value > 1200L) {
                                            lastScannedTimestamp.value = now
                                            onQrDetected(qrCode)
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            null
                        }

                        if (cameraSelector != null) {
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    isProcessing: Boolean,
    setProcessing: (Boolean) -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null && !isProcessing) {
        setProcessing(true)
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue ?: barcode.displayValue
                    if (!rawValue.isNullOrEmpty()) {
                        onBarcodeDetected(rawValue)
                        break
                    }
                }
            }
            .addOnCompleteListener {
                setProcessing(false)
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
