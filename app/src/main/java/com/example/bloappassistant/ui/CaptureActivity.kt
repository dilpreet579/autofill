package com.example.bloappassistant.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.bloappassistant.R
import com.example.bloappassistant.vision.OpenAIVisionClient
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureActivity : AppCompatActivity() {

    private var currentPhotoPath: String = ""

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val file = File(currentPhotoPath)
            if (file.exists()) {
                Toast.makeText(this, "Analyzing image...", Toast.LENGTH_SHORT).show()
                OpenAIVisionClient.analyzeForm(file) { jsonResponse ->
                    runOnUiThread {
                        if (jsonResponse != null) {
                            Toast.makeText(this, "JSON Extracted!", Toast.LENGTH_SHORT).show()
                            Log.d("CaptureActivity", "JSON Output: $jsonResponse")
                            
                            // Broadcast the JSON to the InspectorService so it can autofill
                            val intent = Intent("com.example.bloappassistant.AUTOFILL_DATA")
                            intent.putExtra("json_data", jsonResponse)
                            sendBroadcast(intent)
                        } else {
                            Toast.makeText(this, "Failed to analyze image.", Toast.LENGTH_LONG).show()
                        }
                        finish()
                    }
                }
            } else {
                Toast.makeText(this, "Image file not found.", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, "Capture cancelled", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_capture)

        dispatchTakePictureIntent()
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                null
            }
            
            photoFile?.also {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureLauncher.launch(takePictureIntent)
            } ?: finish()
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }
}
