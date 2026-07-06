package com.example.bloappassistant.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONObject

object OpenAIVisionClient {
    private const val TAG = "VisionClient"
    private val API_KEY = com.example.bloappassistant.BuildConfig.OPENAI_API_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun analyzeForm(imageFile: File, callback: (String?) -> Unit) {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        if (bitmap == null) {
            callback(null)
            return
        }

        // Compress image to save bandwidth/tokens
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        val systemPrompt = """
            You are an OCR assistant. Read the handwritten fields in this BLO voter form.
            Extract the data and return it in strict JSON format using these exact keys:
            - date_of_birth
            - aadhaar_no
            - mobile_no
            - father_name
            - mother_name
            - spouse_name
            - spouse_epic

            If a field is empty, leave the value blank. Return ONLY valid JSON, with no conversational text.
        """.trimIndent()

        val jsonBody = """
        {
            "model": "gpt-4o",
            "messages": [
                {
                    "role": "system",
                    "content": "$systemPrompt"
                },
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": "data:image/jpeg;base64,$base64Image"
                            }
                        }
                    ]
                }
            ],
            "max_tokens": 500,
            "response_format": { "type": "json_object" }
        }
        """.trimIndent()

        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer ${"$"}API_KEY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "API call failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "API error: ${"$"}{response.code} - ${"$"}{response.body?.string()}")
                    callback(null)
                    return
                }

                try {
                    val responseBody = response.body?.string() ?: ""
                    val jsonObject = JSONObject(responseBody)
                    val content = jsonObject.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    callback(content)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing response", e)
                    callback(null)
                }
            }
        })
    }
}
