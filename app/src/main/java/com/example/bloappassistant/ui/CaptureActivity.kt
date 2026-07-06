package com.example.bloappassistant.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.bloappassistant.R
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
                // Hand off to the accessibility service and return to the target app
                // immediately, instead of keeping this activity in the foreground for
                // the duration of the OpenAI network call.
                val intent = Intent("com.example.bloappassistant.PHOTO_CAPTURED")
                intent.putExtra("photo_path", currentPhotoPath)
                sendBroadcast(intent)
            } else {
                Toast.makeText(this, "Image file not found.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Capture cancelled", Toast.LENGTH_SHORT).show()
        }
        // This activity runs in its own task (it was launched with FLAG_ACTIVITY_NEW_TASK
        // from the accessibility service, which has no existing task to return to).
        // moveTaskToBack reveals whatever task was in front before it (the BLO app, exactly
        // as the user left it) instead of finish()'s "go home" fallback, and without sending
        // the target app a fresh launch intent that would reset it to its start screen.
        moveTaskToBack(true)
        finish()
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
