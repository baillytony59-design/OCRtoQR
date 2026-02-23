package com.example.ocrtoqr

import android.graphics.*
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.ocrtoqr.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            lateinit var camera: Camera

            analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                val bitmap = binding.previewView.bitmap
                if (bitmap != null) {

                    val cropped = cropToScanArea(bitmap)

                    if (!isImageSharp(cropped)) {
                        binding.scanFrame.setBackgroundResource(R.drawable.scan_frame_blur)
                        imageProxy.close()
                        return@setAnalyzer
                    } else {
                        binding.scanFrame.setBackgroundResource(R.drawable.scan_frame)
                    }

                    val image = InputImage.fromBitmap(cropped, 0)

                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            val raw = result.text.uppercase()
                            val match = Regex("#[A-Z0-9]{4}").find(raw)
                            if (match != null && match.value != lastText) {
                                lastText = match.value
                                binding.qrImage.setImageBitmap(createQr(lastText))
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } else {
                    imageProxy.close()
                }
            }

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )

            val factory = binding.previewView.meteringPointFactory
            val point = factory.createPoint(
                binding.previewView.width / 2f,
                binding.previewView.height / 2f
            )

            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .setAutoCancel(false)
                .build()

            camera.cameraControl.startFocusAndMetering(action)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun cropToScanArea(bitmap: Bitmap): Bitmap {
        val frameWidth = bitmap.width * 0.7f
        val frameHeight = bitmap.height * 0.25f
        val left = (bitmap.width - frameWidth) / 2
        val top = (bitmap.height - frameHeight) / 2
        return Bitmap.createBitmap(
            bitmap,
            left.toInt(),
            top.toInt(),
            frameWidth.toInt(),
            frameHeight.toInt()
        )
    }

    private fun isImageSharp(bitmap: Bitmap, threshold: Double = 100.0): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            gray[i] = 0.299 * ((c shr 16) and 0xFF) +
                      0.587 * ((c shr 8) and 0xFF) +
                      0.114 * (c and 0xFF)
        }

        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val lap = -4 * gray[i] +
                        gray[i - 1] +
                        gray[i + 1] +
                        gray[i - w] +
                        gray[i + w]
                sum += lap
                sumSq += lap * lap
                count++
            }
        }

        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return variance > threshold
    }

    private fun createQr(text: String): Bitmap {
        val writer = QRCodeWriter()
        val matrix = writer.encode(text, BarcodeFormat.QR_CODE, 400, 400)
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.RGB_565)
        for (x in 0 until 400) {
            for (y in 0 until 400) {
                bitmap.setPixel(x, y,
                    if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}