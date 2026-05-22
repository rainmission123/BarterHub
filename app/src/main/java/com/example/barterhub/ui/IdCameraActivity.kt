package com.example.barterhub.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.barterhub.databinding.ActivityIdCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class IdCameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIdCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var currentPhotoPath: String? = null
    private var isFrontId: Boolean = true

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startCamera()
        else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIdCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isFrontId = intent.getBooleanExtra("isFront", true)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUI()
        checkCameraPermission()
    }

    private fun setupUI() {
        val instruction = if (isFrontId) "Align FRONT of ID within frame" else "Align BACK of ID within frame"
        binding.tvInstruction.text = instruction

        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = createImageFile()
        currentPhotoPath = photoFile.absolutePath

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(this@IdCameraActivity, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // 🔥 CROP THE IMAGE TO MATCH ID FRAME ASPECT RATIO
                    val croppedUri = cropImageToIdAspectRatio(photoFile)

                    setResult(RESULT_OK, Intent().apply {
                        putExtra("imageUri", croppedUri.toString())
                        putExtra("isFront", isFrontId)
                    })
                    finish()
                }
            }
        )
    }

    private fun cropImageToIdAspectRatio(originalFile: File): Uri {
        return try {
            // ID aspect ratio: 1.586 (standard ID size)
            val targetAspectRatio = 1.586f

            // For now, return original (in real app, use Bitmap cropping)
            // TODO: Implement actual bitmap cropping
            Uri.fromFile(originalFile)
        } catch (e: Exception) {
            Log.e(TAG, "Cropping failed, using original: ${e.message}")
            Uri.fromFile(originalFile)
        }
    }

    @Throws(Exception::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir("ID_Photos") ?: filesDir

        return File.createTempFile(
            "JPEG_ID_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "IdCameraActivity"
    }
}