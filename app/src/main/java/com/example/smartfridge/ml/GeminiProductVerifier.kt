package com.example.smartfridge.ml

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional second-stage verifier. YOLO remains the primary detector; Gemini only
 * checks uncertain crops and may select one of the model's existing 17 labels.
 */
class GeminiProductVerifier(
    private val apiKey: String
) {
    companion object {
        private const val TAG = "GeminiProductVerifier"
        private const val MODEL = "gemini-3.8-flash"
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

        // Keep in sync with YOLODetector's existing best.tflite label order/names.
        private val ALLOWED_LABELS = listOf(
            "Алкоголь",
            "Конфеты",
            "Консервы",
            "Шоколад",
            "Десерт",
            "Сушёные продукты",
            "Сухофрукты",
            "Напитки",
            "Жвачка",
            "Растворимые напитки",
            "Лапша быстрого приготовления",
            "Молоко",
            "Средства гигиены",
            "Снеки",
            "Приправы",
            "Канцелярия",
            "Бумажные салфетки"
        )
    }

    @Volatile
    private var apiUnavailableForSession: Boolean = false

    suspend fun verify(crop: Bitmap, yoloLabel: String, ocrText: String): String? =
        withContext(Dispatchers.IO) {
            // A regional/API access rejection will not be retried for every
            // detected object. YOLO remains authoritative for this app session.
            if (apiKey.isBlank() || apiUnavailableForSession) return@withContext null

            var connection: HttpURLConnection? = null
            try {
                val imageBytes = ByteArrayOutputStream().use { output ->
                    crop.compress(Bitmap.CompressFormat.JPEG, 82, output)
                    output.toByteArray()
                }
                val prompt = """
                    You are checking one cropped object from a refrigerator photo.
                    Choose exactly one label from this list, or UNKNOWN if the object
                    cannot be identified confidently:
                    ${ALLOWED_LABELS.joinToString(", ")}.
                    The on-device YOLO model predicted: "$yoloLabel".
                    OCR text found on the package (may be empty or noisy): "$ocrText".
                    Use the visible object and OCR together. Do not invent a brand,
                    product name, quantity, or expiration date.
                    Return only JSON in this exact format: {"label":"one allowed label or UNKNOWN"}.
                """.trimIndent()

                val parts = JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject()
                                .put("mime_type", "image/jpeg")
                                .put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                        )
                    )

                val requestBody = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("temperature", 0.1)
                            .put("responseMimeType", "application/json")
                            .put("maxOutputTokens", 100)
                    )

                connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 18_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
                }

                connection.outputStream.use { stream ->
                    stream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                }

                val status = connection.responseCode
                val responseText = (if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (status !in 200..299) {
                    Log.w(TAG, "Gemini HTTP $status: ${responseText.take(500)}")
                    if (status == 400 || status == 403 || status == 404 || status == 451) {
                        apiUnavailableForSession = true
                    }
                    return@withContext null
                }

                val candidates = JSONObject(responseText).optJSONArray("candidates")
                    ?: return@withContext null
                if (candidates.length() == 0) return@withContext null

                val responseParts = candidates.getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?: return@withContext null

                val generatedText = buildString {
                    for (index in 0 until responseParts.length()) {
                        append(responseParts.optJSONObject(index)?.optString("text").orEmpty())
                    }
                }
                val jsonStart = generatedText.indexOf('{')
                val jsonEnd = generatedText.lastIndexOf('}')
                if (jsonStart < 0 || jsonEnd <= jsonStart) return@withContext null

                val label = JSONObject(generatedText.substring(jsonStart, jsonEnd + 1))
                    .optString("label")
                    .trim()

                if (label in ALLOWED_LABELS) {
                    Log.d(TAG, "YOLO '$yoloLabel' verified/refined as '$label'")
                    label
                } else {
                    Log.d(TAG, "Gemini could not verify '$yoloLabel'")
                    null
                }
            } catch (error: Exception) {
                Log.w(TAG, "Gemini verification skipped: ${error.message}")
                null
            } finally {
                connection?.disconnect()
            }
        }
}
