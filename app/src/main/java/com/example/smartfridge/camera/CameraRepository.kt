package com.example.smartfridge.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class CameraResult(
    val bitmap: Bitmap?,
    val error: String? = null
)

class CameraRepository {
    suspend fun capture(address: String): CameraResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val rawAddress = address.trim()
            require(rawAddress.isNotEmpty()) { "Адрес камеры не указан" }

            val baseUrl = when {
                rawAddress.startsWith("http://", ignoreCase = true) ||
                    rawAddress.startsWith("https://", ignoreCase = true) -> rawAddress
                else -> "http://$rawAddress"
            }.trimEnd('/')

            val endpoint = if (baseUrl.endsWith("/capture", ignoreCase = true)) {
                baseUrl
            } else {
                "$baseUrl/capture"
            }

            val url = URL(endpoint)
            require(url.protocol == "http" || url.protocol == "https") {
                "Поддерживаются только HTTP и HTTPS адреса камеры"
            }

            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                useCaches = false
                doInput = true
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext CameraResult(
                    bitmap = null,
                    error = "Камера вернула HTTP $responseCode"
                )
            }

            val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
            if (bitmap == null) {
                CameraResult(null, "Камера вернула пустое или неподдерживаемое изображение")
            } else {
                CameraResult(bitmap)
            }
        } catch (e: Exception) {
            Log.e("CameraRepository", "Failed to capture camera frame", e)
            CameraResult(null, e.message ?: "Не удалось связаться с камерой")
        } finally {
            connection?.disconnect()
        }
    }
}
