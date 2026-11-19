package com.example.smartfridge.ml

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a resized JPEG camera frame to Gemini for a short product summary.
 * Local YOLO remains a separate first-stage detector in the ViewModel.
 */
class GeminiPhotoAnalyzer(private val apiKey: String) {
    companion object {
        private const val MODEL = "gemini-3.8-flash"
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    }

    suspend fun analyze(photo: Bitmap): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Не задан GEMINI_API_KEY в local.properties" }

        // Smaller payload reduces upload time on mobile/VPN connections.
        val maxSide = 1024
        val scale = minOf(1f, maxSide.toFloat() / maxOf(photo.width, photo.height))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                photo,
                (photo.width * scale).toInt().coerceAtLeast(1),
                (photo.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else photo
        val imageBytes = try {
            ByteArrayOutputStream().use { output ->
                resized.compress(Bitmap.CompressFormat.JPEG, 75, output)
                output.toByteArray()
            }
        } finally {
            if (resized !== photo) resized.recycle()
        }
        val prompt = """
            Назови по-русски только хорошо различимые продукты на фото.
            Для каждого укажи название и количество. Не угадывай.
            Если продуктов не видно, так и напиши. Ответ максимум 5 коротких пунктов.
        """.trimIndent()

        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inline_data", JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP))))

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.2)
                .put("maxOutputTokens", 250))

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw IllegalStateException("Gemini HTTP $status: ${detail.ifBlank { response.take(300) }}")
            }
            val candidates = JSONObject(response).optJSONArray("candidates")
                ?: throw IllegalStateException("Gemini не вернул candidates")
            if (candidates.length() == 0) throw IllegalStateException("Gemini вернул пустой ответ")
            val responseParts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("В ответе Gemini нет текста")
            buildString {
                for (i in 0 until responseParts.length()) {
                    append(responseParts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }.trim().ifBlank { throw IllegalStateException("Gemini вернул пустой текст") }
        } finally {
            connection?.disconnect()
        }
    }
}
