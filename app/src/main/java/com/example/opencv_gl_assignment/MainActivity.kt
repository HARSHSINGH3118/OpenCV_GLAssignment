package com.example.opencv_gl_assignment

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    companion object {
        init { System.loadLibrary("native-lib") }
    }

    // 🔹 Native C++ function
    external fun cannyProcessImage(inputPath: String): ByteArray

    private lateinit var tv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout with a button and status text
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        tv = TextView(this)
        val pickButton = Button(this).apply { text = "Select Image for Canny Edge Detection" }

        layout.addView(pickButton)
        layout.addView(tv)
        setContentView(layout)

        // Ask for read permission if needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
        }

        pickButton.setOnClickListener {
            // Pick image from gallery
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                uri?.let {
                    val imagePath = getRealPathFromURI(it)
                    if (imagePath != null) {
                        processImage(imagePath)
                    } else {
                        tv.text = "❌ Could not get image path"
                    }
                }
            }
        }

    private fun processImage(imagePath: String) {
        tv.text = "Processing image..."
        val processedBytes = cannyProcessImage(imagePath)

        // Save processed image to Downloads
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "edges_output.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        var success = false
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out: OutputStream ->
                out.write(processedBytes)
                success = true
            }
        }
        tv.text = if (success)
            " Edge image saved to Downloads!"
        else
            " Failed to save edge image"
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        contentResolver.query(uri, projection, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return cursor.getString(columnIndex)
            }
        }
        return null
    }
}
