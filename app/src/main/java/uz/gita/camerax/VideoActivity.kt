package uz.gita.camerax

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoRecordEvent.Finalize
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import uz.gita.camerax.databinding.ActivityVideoBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VideoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoBinding

    private var service: ExecutorService? = null
    private var recording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (ActivityCompat.checkSelfPermission(
                this@VideoActivity,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera(cameraFacing)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.exchange.setOnClickListener {
            startActivity(Intent(this@VideoActivity, MainActivity::class.java))
            finish()
        }

        binding.capture.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    this@VideoActivity,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityResultLauncher.launch(Manifest.permission.CAMERA)
            } else if (ActivityCompat.checkSelfPermission(
                    this@VideoActivity,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityResultLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ActivityCompat.checkSelfPermission(
                    this@VideoActivity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityResultLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                captureVideo()
            }
        }
        if (ActivityCompat.checkSelfPermission(
                this@VideoActivity,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activityResultLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startCamera(cameraFacing)
        }
        binding.flipCamera.setOnClickListener {
            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera(cameraFacing)
        }
        service = Executors.newSingleThreadExecutor()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun captureVideo() {
        binding.capture.setImageResource(R.drawable.round_stop_circle_24)
        val recording1 = recording
        if (recording1 != null) {
            recording1.stop()
            recording = null
            return
        }

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(
            System.currentTimeMillis()
        )

        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Pictures/CameraX/Videos")
        val options = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues).build()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        recording =
            videoCapture!!.output.prepareRecording(this@VideoActivity, options).withAudioEnabled()
                .start(
                    ContextCompat.getMainExecutor(this@VideoActivity)
                ) { videoRecordEvent: VideoRecordEvent? ->
                    if (videoRecordEvent is VideoRecordEvent.Start) {
                        binding.capture.isEnabled = true
                    } else if (videoRecordEvent is Finalize) {
                        if (!videoRecordEvent.hasError()) {
                            val msg =
                                "Video capture succeeded: " + videoRecordEvent.outputResults
                                    .outputUri
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            recording!!.close()
                            recording = null
                            val msg = "Error: " + videoRecordEvent.error
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                        binding.capture.setImageResource(R.drawable.round_fiber_manual_record_24)
                    }
                }
    }

    private fun startCamera(cameraFacing: Int) {
        val processCameraProvider = ProcessCameraProvider.getInstance(this@VideoActivity)
        processCameraProvider.addListener({
            try {
                val cameraProvider = processCameraProvider.get()
                val preview =
                    Preview.Builder().build()
                preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                cameraProvider.unbindAll()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraFacing).build()
                val camera =
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
                binding.toggleFlash.setOnClickListener {
                    toggleFlash(
                        camera
                    )
                }
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this@VideoActivity))
    }

    private fun toggleFlash(camera: Camera) {
        if (camera.cameraInfo.hasFlashUnit()) {
            if (camera.cameraInfo.torchState.value == 0) {
                camera.cameraControl.enableTorch(true)
                binding.toggleFlash.setImageResource(R.drawable.round_flash_off_24)
            } else {
                camera.cameraControl.enableTorch(false)
                binding.toggleFlash.setImageResource(R.drawable.round_flash_on_24)
            }
        } else {
            runOnUiThread {
                Toast.makeText(
                    this@VideoActivity,
                    "Flash is not available currently",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        service!!.shutdown()
    }
}