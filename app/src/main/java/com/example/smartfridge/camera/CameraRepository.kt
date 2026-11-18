package com.example.smartfridge.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class CameraResult(
    val bitmap: Bitmap?,
    val error: String? = null
)

class CameraRepository {

    suspend fun capture(
        address: String
    ): CameraResult =
        withContext(Dispatchers.IO) {

            var connection:
                    HttpURLConnection? = null

            try {

                val cleanAddress =
                    address
                        .trim()
                        .removePrefix("http://")
                        .removePrefix("https://")
                        .trimEnd('/')

                connection =
                    URL(
                        "http://$cleanAddress/capture"
                    )
                        .openConnection()
                            as HttpURLConnection

                connection.connectTimeout =
                    10000

                connection.readTimeout =
                    15000

                connection.requestMethod =
                    "GET"

                connection.useCaches =
                    false

                if (
                    connection.responseCode != 200
                ) {

                    return@withContext CameraResult(
                        bitmap = null,
                        error = "Не удалось получить снимок"
                    )
                }

                val bitmap =
                    connection.inputStream
                        .use {
                            BitmapFactory
                                .decodeStream(it)
                        }

                if (bitmap == null) {

                    CameraResult(
                        null,
                        "Камера не вернула изображение"
                    )

                } else {

                    CameraResult(
                        bitmap
                    )
                }

            } catch (_: Exception) {

                CameraResult(
                    null,
                    "Не удалось связаться с камерой"
                )

            } finally {

                connection?.disconnect()
            }
        }
}

