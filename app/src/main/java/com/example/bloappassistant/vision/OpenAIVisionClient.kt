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

    private const val MAX_DIMENSION = 1600

    private fun decodeSampledBitmap(imageFile: File, maxDimension: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, boundsOptions)

        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxDimension || boundsOptions.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions)
    }

    fun analyzeForm(imageFile: File, callback: (String?) -> Unit) {
        // Camera photos can be 12MP+ (~48MB as a raw bitmap); downsample to avoid OOM on decode.
        val bitmap = decodeSampledBitmap(imageFile, MAX_DIMENSION)
        if (bitmap == null) {
            callback(null)
            return
        }

        // Compress image to save bandwidth/tokens
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        bitmap.recycle()
        val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        val systemPrompt = """
            System Context:
            You are an expert data extraction agent. You will be provided with images of forms from the Election Commission of India. The layout of these forms is static and will always follow the same format.
            
            Task:
            Your objective is to locate the "Personal Details" section (found in the lower half of the table) and accurately extract the handwritten values for specific fields.
            
            Fields to Extract (and their strict JSON keys):
            You must extract the data corresponding to the following exact field names. Note that the form's printed labels are in Punjabi (Gurmukhi), while the handwritten answers may be in English, numerals, or Punjabi.
            - date_of_birth : Date of Birth (Printed as: ਜਨਮ ਮਿਤੀ) - Expected format: DD/MM/YYYY.
            - aadhaar_no : Aadhaar Number (Printed as: ਆਧਾਰ ਨੰ.) - 12-digit number.
            - mobile_no : Mobile Number (Printed as: ਮੋਬਾਇਲ ਨੰ.) - 10-digit number.
            - father_name : Father/Guardian's Name (Printed as: ਪਿਤਾ/ਸਰਪ੍ਰਸਤ ਦਾ ਨਾਮ).
            - mother_name : Mother's Name (Printed as: ਮਾਤਾ ਦਾ ਨਾਮ).
            - spouse_name : Spouse (Husband/Wife) Name (Printed as: ਪਤੀ ਜਾਂ ਪਤਨੀ ਦਾ ਨਾਮ).
            - spouse_epic : Spouse EPIC/Voter ID Number.
            
            Extraction Rules:
            Language: Always output every value in English using only the Latin alphabet, regardless
            of whether the handwriting is in English or Punjabi. If a name is written in Gurmukhi
            script, transliterate it phonetically into English (for example, ਹਰਜੀਤ ਸਿੰਘ becomes
            Harjit Singh). Never output Gurmukhi characters in the JSON values.
            Missing Data: If a field is left completely blank or marked with a dash (-), output an empty string "".
            No Hallucinations: Do not guess missing numbers or correct spelling mistakes in names.
            
            Return ONLY valid JSON using the strict keys listed above. No conversational text, no markdown formatting.
        """.trimIndent()

        val jsonBody = org.json.JSONObject().apply {
            put("model", "gpt-5.4")
            put("messages", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(org.json.JSONObject().apply {
                    put("role", "user")
                    put("content", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", org.json.JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                        })
                    })
                })
            })
            put("max_completion_tokens", 1000)
            put("response_format", org.json.JSONObject().apply {
                put("type", "json_object")
            })
        }.toString()

        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $API_KEY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "API call failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "API error: ${response.code} - ${response.body?.string()}")
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
