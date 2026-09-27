package com.example.koreansignlanguage

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var resultTextView: TextView
    
    private var handLandmarker: HandLandmarker? = null
    private var tfliteInterpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: Executor

    private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        resultTextView = findViewById(R.id.resultTextView)

        cameraExecutor = ContextCompat.getMainExecutor(this)

        if (allPermissionsGranted()) {
            initializeModels()
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
    }

    private fun initializeModels() {
        lifecycleScope.launch {
            try {
                // Initialize Hand Landmarker
                val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(
                        com.google.mediapipe.tasks.core.BaseOptions.builder()
                            .setModelAssetPath("hand_landmarker.task")
                            .build()
                    )
                    .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.LIVE_STREAM)
                    .setResultListener { result, mpImage ->
                        handleHandLandmarkerResult(result)
                    }

                handLandmarker = HandLandmarker.createFromOptions(this@MainActivity, optionsBuilder.build())

                // Initialize TensorFlow Lite for fingerspelling
                val tfliteModel = FileUtil.loadMappedFile(this@MainActivity, "fingerspelling_lstm.tflite")
                tfliteInterpreter = Interpreter(tfliteModel)

                // Load labels
                labels = loadLabels()

                resultTextView.text = "Models loaded successfully"
            } catch (e: Exception) {
                resultTextView.text = "Error loading models: ${e.message}"
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                resultTextView.text = "Camera error: ${exc.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)

            val mpImage = BitmapImageBuilder(bitmap).build()

            // 두 번째 인자는 회전 각도가 아니라 타임스탬프입니다.
            handLandmarker?.detectAsync(
                mpImage,
                SystemClock.uptimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    private fun handleHandLandmarkerResult(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) {
            runOnUiThread {
                resultTextView.text = "No hand detected"
            }
            return
        }

        try {
            val landmarks = result.landmarks()[0]
            
            // Convert landmarks to input for LSTM
            val inputArray = FloatArray(landmarks.size * 2)
            landmarks.forEachIndexed { idx, landmark ->
                inputArray[idx * 2] = landmark.x()
                inputArray[idx * 2 + 1] = landmark.y()
            }

            // Run TensorFlow Lite inference
            val output = Array(1) { FloatArray(labels.size) }
            
            tfliteInterpreter?.run(
                arrayOf(inputArray),
                mapOf(0 to output)
            )

            // Get predicted label
            val predictions = output[0]
            val maxIdx = predictions.indices.maxByOrNull { predictions[it] } ?: 0
            val confidence = predictions[maxIdx]
            
            val resultText = if (confidence > 0.5f) {
                "${labels.getOrNull(maxIdx) ?: "Unknown"} (${String.format("%.2f", confidence * 100)}%)"
            } else {
                "Confidence too low"
            }

            runOnUiThread {
                resultTextView.text = resultText
            }
        } catch (e: Exception) {
            runOnUiThread {
                resultTextView.text = "Inference error: ${e.message}"
            }
        }
    }

    private fun loadLabels(): List<String> {
        val labels = mutableListOf<String>()
        try {
            val inputStream = assets.open("labels.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    labels.add(line.trim())
                }
            }
            reader.close()
        } catch (e: Exception) {
            runOnUiThread {
                resultTextView.text = "Error loading labels: ${e.message}"
            }
        }
        return labels
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // NV21은 Y + V + U 순서입니다.
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val outputStream = ByteArrayOutputStream()

        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            100,
            outputStream
        )

        val jpegBytes = outputStream.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(
            jpegBytes,
            0,
            jpegBytes.size
        )

        val matrix = Matrix()

        // CameraX 이미지 회전 적용
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

        // 전면 카메라 좌우 반전
        matrix.postScale(-1f, 1f)

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    private fun allPermissionsGranted(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                initializeModels()
                startCamera()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarker?.close()
        tfliteInterpreter?.close()
    }
}
