package com.example.opencv_gl_assignment

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaActionSound
import android.opengl.GLSurfaceView
import android.os.*
import android.provider.MediaStore
import android.util.Range
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.opencv_gl_assignment.gl.GLRenderer
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    companion object {
        init { System.loadLibrary("native-lib") }
        private const val REQ_CAMERA = 100
    }

    // JNI: returns RGBA bytes (mode: 0 = raw, 1 = edge)
    external fun processFrameYuv420(
        y: ByteArray, u: ByteArray, v: ByteArray,
        yStride: Int, uStride: Int, vStride: Int,
        width: Int, height: Int, mode: Int
    ): ByteArray?

    private lateinit var glView: GLSurfaceView
    private val renderer = GLRenderer()

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    private var tvFps: TextView? = null
    private var tvMode: TextView? = null

    private var mode = 1 // 1 = edge, 0 = raw
    private var lastTs = 0L
    private var fps = 0.0

    // Zoom & shutter sound
    private var zoomLevel = 1.0f
    private val maxZoom = 5.0f
    private val minZoom = 1.0f
    private val sound = MediaActionSound()

    // Lifecycle flag to stop callback spam
    private var isActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views from XML
        glView = findViewById(R.id.glView)
        tvFps = findViewById(R.id.tvFps)
        tvMode = findViewById(R.id.tvMode)
        val btnCapture: ImageButton = findViewById(R.id.btnCapture)
        val btnZoomIn: ImageButton = findViewById(R.id.btnZoomIn)
        val btnZoomOut: ImageButton = findViewById(R.id.btnZoomOut)

        // GLSurfaceView setup
        glView.setEGLContextClientVersion(2)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        // Toggle mode by tapping the right-top label
        tvMode?.setOnClickListener {
            mode = 1 - mode
            tvMode?.text = if (mode == 1) "Mode: EDGE" else "Mode: RAW"
        }

        // Shutter sound + capture
        btnCapture.setOnClickListener {
            sound.play(MediaActionSound.SHUTTER_CLICK)
            saveCurrentFrame()
        }

        // Zoom controls
        btnZoomIn.setOnClickListener {
            zoomLevel = (zoomLevel + 0.5f).coerceAtMost(maxZoom)
            updateZoom()
        }
        btnZoomOut.setOnClickListener {
            zoomLevel = (zoomLevel - 0.5f).coerceAtLeast(minZoom)
            updateZoom()
        }

        // Permissions (camera will actually start in onResume)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // Will start in onResume
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            isActive = true
            startCamera()
        }
    }

    override fun onPause() {
        isActive = false
        stopCamera()
        glView.onPause()
        super.onPause()
    }

    // ---------- Threads ----------
    private fun startBgThread() {
        bgThread = HandlerThread("camera-bg").also { it.start() }
        bgHandler = Handler(bgThread!!.looper)
    }

    private fun stopBgThread() {
        try { bgThread?.quitSafely() } catch (_: Throwable) {}
        try { bgThread?.join() } catch (_: Throwable) {}
        bgThread = null
        bgHandler = null
    }

    // ---------- Camera pipeline ----------
    private fun startCamera() {
        startBgThread()

        // Choose back camera
        val cameraId = cameraManager.cameraIdList.first { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
        }

        // Size compatible with your native pipeline
        val w = 640
        val h = 480

        imageReader = ImageReader.newInstance(w, h, ImageFormat.YUV_420_888, 3).apply {
            setOnImageAvailableListener({ reader ->
                if (!isActive) return@setOnImageAvailableListener

                var image: Image? = null
                try {
                    image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

                    // Read props & copy data immediately (before closing image)
                    val iw = image.width
                    val ih = image.height
                    val planes = image.planes

                    val yBytes = planes[0].buffer.toByteArray()
                    val uBytes = planes[1].buffer.toByteArray()
                    val vBytes = planes[2].buffer.toByteArray()
                    val yStride = planes[0].rowStride
                    val uStride = planes[1].rowStride
                    val vStride = planes[2].rowStride

                    val rgba = processFrameYuv420(
                        yBytes, uBytes, vBytes,
                        yStride, uStride, vStride,
                        iw, ih, mode
                    ) ?: return@setOnImageAvailableListener

                    // Push to GL renderer
                    renderer.pendingFrameRGBA = rgba
                    renderer.frameWidth = iw
                    renderer.frameHeight = ih

                    // FPS
                    val now = SystemClock.elapsedRealtime()
                    if (lastTs > 0L) {
                        val dt = now - lastTs
                        fps = 1000.0 / dt
                    }
                    lastTs = now
                    tvFps?.post { tvFps?.text = "FPS: ${(fps * 10).roundToInt() / 10.0}" }

                    // Render the new frame
                    glView.requestRender()
                } catch (_: IllegalStateException) {
                    // Image already closed or invalid; ignore this frame
                } catch (_: Throwable) {
                    // Swallow per-frame errors to keep the pipeline alive
                } finally {
                    try { image?.close() } catch (_: Throwable) {}
                }
            }, bgHandler)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) return

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(cd: CameraDevice) {
                cameraDevice = cd
                val surface = imageReader!!.surface
                val req = cd.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(surface)
                    set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(15, 30))
                }
                cd.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            if (!isActive) return
                            captureSession = session
                            session.setRepeatingRequest(req.build(), null, bgHandler)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Toast.makeText(
                                this@MainActivity,
                                "Camera config failed", Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    bgHandler
                )
            }

            override fun onDisconnected(cd: CameraDevice) {
                cd.close()
            }

            override fun onError(cd: CameraDevice, err: Int) {
                cd.close()
            }
        }, bgHandler)
    }

    private fun stopCamera() {
        try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Throwable) {}
        try { captureSession?.close() } catch (_: Throwable) {}
        try { cameraDevice?.close() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}

        captureSession = null
        cameraDevice = null
        imageReader = null

        stopBgThread()
    }

    // ---------- Zoom ----------
    private fun updateZoom() {
        val camId = cameraManager.cameraIdList.firstOrNull() ?: return
        val chars = cameraManager.getCameraCharacteristics(camId)
        val rect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return

        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val deltaX = (0.5f * rect.width() / zoomLevel).toInt()
        val deltaY = (0.5f * rect.height() / zoomLevel).toInt()
        val zoomRect = Rect(
            centerX - deltaX,
            centerY - deltaY,
            centerX + deltaX,
            centerY + deltaY
        )

        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val target = imageReader?.surface ?: return

        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(target)
            set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
            set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(15, 30))
        }
        session.setRepeatingRequest(req.build(), null, bgHandler)
    }

    // ---------- Capture & upload ----------
    private fun saveCurrentFrame() {
        val frame = renderer.pendingFrameRGBA ?: run {
            Toast.makeText(this, "No frame to save yet!", Toast.LENGTH_SHORT).show()
            return
        }

        val width = renderer.frameWidth
        val height = renderer.frameHeight
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(frame))

        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "edge_capture_${System.currentTimeMillis()}.jpg"
            )
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/EdgeCaptures"
            )
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            Toast.makeText(
                this,
                " Frame saved to Gallery (EdgeCaptures)",
                Toast.LENGTH_SHORT
            ).show()
            uploadToWebServer(bmp)
        } else {
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadToWebServer(bmp: Bitmap) {
        Thread {
            try {
                val url = URL("http://10.1.97.61:5000/upload") // <-- your server
                val boundary = "----EdgeBoundary${System.currentTimeMillis()}"
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }

                val output = conn.outputStream
                val writer = output.bufferedWriter()
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"frame\"; filename=\"latest_frame.jpg\"\r\n")
                writer.append("Content-Type: image/jpeg\r\n\r\n")
                writer.flush()

                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                output.write(baos.toByteArray())
                output.flush()

                writer.append("\r\n--$boundary--\r\n")
                writer.flush()
                writer.close()
                output.close()

                val code = conn.responseCode
                println("Upload response code: $code")
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // ---------- Buffer helper ----------
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val bytes = ByteArray(remaining())
        get(bytes)
        return bytes
    }
}
