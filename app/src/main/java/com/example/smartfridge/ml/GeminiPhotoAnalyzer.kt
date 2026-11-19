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
 * Sends the original camera frame directly to Gemini.
 * No YOLO, OCR, cropping or local image processing is performed.
 */
class GeminiPhotoAnalyzer(private val apiKey: String) {
    companion object {
        private const val MODEL = "gemini-3.8-flash"
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    }

    suspend fun analyze(photo: Bitmap): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Не задан GEMINI_API_KEY в local.properties" }

        val imageBytes = ByteArrayOutputStream().use { output ->
            photo.compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.toByteArray()
        }
        val prompt = """
            Проанализируй фотографию холодильника и перечисли только продукты,
            которые действительно хорошо видны. Для каждого продукта укажи
            название и примерное количество. Если на упаковке читается срок годности,
            укажи его, иначе напиши «не удалось прочитать».
            Не выдумывай продукты, марки и даты. Если фото не холодильника,
            кратко опиши, что на нём изображено.
            Ответ дай на русском языке, структурированным списком.
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
                .put("maxOutputTokens", 700))

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
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
