package com.example.smartfridge.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class YoloDetection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class YOLODetector(context: Context) {

    companion object {
        private const val MODEL_FILE = "best.tflite"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.20f
        private const val IOU_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 50

        private val labels = listOf(
            "Алкоголь",                         // 0
            "Конфеты",                          // 1
            "Консервы",                         // 2
            "Шоколад",                          // 3
            "Десерт",                           // 4
            "Сушёные продукты",                 // 5
            "Сухофрукты",                       // 6
            "Напитки",                          // 7
            "Жвачка",                           // 8
            "Растворимые напитки",              // 9
            "Лапша быстрого приготовления",     // 10
            "Молоко",                           // 11
            "Средства гигиены",                 // 12
            "Снеки",                            // 13
            "Приправы",                         // 14
            "Канцелярия",                       // 15
            "Бумажные салфетки"                 // 16
        )
    }

    private val modelBuffer: ByteBuffer =
        context.assets.openFd(MODEL_FILE).use { afd ->
            FileInputStream(afd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            ).order(ByteOrder.nativeOrder())
        }

    private var interpreter: Interpreter? = null

    private fun getInterpreter(): Interpreter {
        if (interpreter == null) {
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
        }
        return interpreter!!
    }

    @Synchronized
    fun detect(bitmap: Bitmap): List<YoloDetection> {
        val model = getInterpreter()

        val inputTensor = model.getInputTensor(0)
        val inputShape = inputTensor.shape()

        val isNCHW = inputShape.contentEquals(
            intArrayOf(1, 3, INPUT_SIZE, INPUT_SIZE)
        )

        val isNHWC = inputShape.contentEquals(
            intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3)
        )

        require(isNCHW || isNHWC) {
            "Неподдерживаемый вход модели: ${inputShape.contentToString()}"
        }

        require(inputTensor.dataType() == DataType.FLOAT32) {
            "Модель должна иметь FLOAT32 вход: ${inputTensor.dataType()}"
        }

        val scale = min(
            INPUT_SIZE.toFloat() / bitmap.width,
            INPUT_SIZE.toFloat() / bitmap.height
        )

        val resizedWidth = (bitmap.width * scale).roundToInt()
        val resizedHeight = (bitmap.height * scale).roundToInt()

        val padLeft = (INPUT_SIZE - resizedWidth) / 2
        val padTop = (INPUT_SIZE - resizedHeight) / 2

        val resized = Bitmap.createBitmap(
            INPUT_SIZE,
            INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(resized)
        canvas.drawColor(Color.rgb(114, 114, 114))

        canvas.drawBitmap(
            bitmap,
            null,
            RectF(
                padLeft.toFloat(),
                padTop.toFloat(),
                (padLeft + resizedWidth).toFloat(),
                (padTop + resizedHeight).toFloat()
            ),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

        resized.getPixels(
            pixels,
            0,
            INPUT_SIZE,
            0,
            0,
            INPUT_SIZE,
            INPUT_SIZE
        )

        resized.recycle()

        val input = ByteBuffer.allocateDirect(
            INPUT_SIZE * INPUT_SIZE * 3 * 4
        ).order(ByteOrder.nativeOrder())

        if (isNCHW) {
            for (channel in 0..2) {
                for (pixel in pixels) {
                    val value = when (channel) {
                        0 -> Color.red(pixel)
                        1 -> Color.green(pixel)
                        else -> Color.blue(pixel)
                    }
                    input.putFloat(value / 255f)
                }
            }
        } else {
            for (pixel in pixels) {
                input.putFloat(Color.red(pixel) / 255f)
                input.putFloat(Color.green(pixel) / 255f)
                input.putFloat(Color.blue(pixel) / 255f)
            }
        }

        input.rewind()

        val outputShape = model.getOutputTensor(0).shape()

        require(outputShape.size == 3 && outputShape[0] == 1) {
            "Неподдерживаемый выход YOLO: ${outputShape.contentToString()}"
        }

        val output = Array(1) {
            Array(outputShape[1]) {
                FloatArray(outputShape[2])
            }
        }

        model.run(input, output)

        val channelFirst = outputShape[1] < outputShape[2]

        val channels = if (channelFirst) {
            outputShape[1]
        } else {
            outputShape[2]
        }

        val predictions = if (channelFirst) {
            outputShape[2]
        } else {
            outputShape[1]
        }

        require(channels == labels.size + 4) {
            "Ожидалось ${labels.size + 4} каналов, получено $channels. " +
                    "Форма: ${outputShape.contentToString()}"
        }

        fun value(channel: Int, index: Int): Float {
            return if (channelFirst) {
                output[0][channel][index]
            } else {
                output[0][index][channel]
            }
        }

        val candidates = mutableListOf<YoloDetection>()
        var maxConfidence = 0f

        for (i in 0 until predictions) {
            val centerX = value(0, i)
            val centerY = value(1, i)
            val width = value(2, i)
            val height = value(3, i)

            var bestClass = -1
            var bestScore = 0f

            for (c in 4 until channels) {
                val score = value(c, i)

                if (score > bestScore) {
                    bestScore = score
                    bestClass = c - 4
                }
            }

            if (bestScore > maxConfidence) {
                maxConfidence = bestScore
            }

            if (bestScore < CONFIDENCE_THRESHOLD) continue
            if (bestClass !in labels.indices) continue

            val coordinateScale = if (
                maxOf(
                    abs(centerX),
                    abs(centerY),
                    abs(width),
                    abs(height)
                ) <= 2f
            ) INPUT_SIZE.toFloat() else 1f

            val cx = centerX * coordinateScale
            val cy = centerY * coordinateScale
            val boxWidth = width * coordinateScale
            val boxHeight = height * coordinateScale

            val x1 = cx - boxWidth / 2f
            val y1 = cy - boxHeight / 2f
            val x2 = cx + boxWidth / 2f
            val y2 = cy + boxHeight / 2f

            val left = ((x1 - padLeft) / scale)
                .coerceIn(0f, bitmap.width.toFloat())

            val top = ((y1 - padTop) / scale)
                .coerceIn(0f, bitmap.height.toFloat())

            val right = ((x2 - padLeft) / scale)
                .coerceIn(0f, bitmap.width.toFloat())

            val bottom = ((y2 - padTop) / scale)
                .coerceIn(0f, bitmap.height.toFloat())

            if (right <= left || bottom <= top) continue

            candidates.add(
                YoloDetection(
                    classId = bestClass,
                    label = labels[bestClass],
                    confidence = bestScore,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom
                )
            )
        }

        Log.d(
            "YOLO11",
            "input=${inputShape.contentToString()}, " +
                    "output=${outputShape.contentToString()}, " +
                    "maxConfidence=$maxConfidence, " +
                    "candidates=${candidates.size}"
        )

        return applyNms(candidates)
    }

    private fun applyNms(
        detections: List<YoloDetection>
    ): List<YoloDetection> {

        val remaining = detections
            .sortedByDescending { it.confidence }
            .toMutableList()

        val result = mutableListOf<YoloDetection>()

        while (
            remaining.isNotEmpty() &&
            result.size < MAX_DETECTIONS
        ) {
            val best = remaining.removeAt(0)
            result.add(best)

            val iterator = remaining.iterator()

            while (iterator.hasNext()) {
                val other = iterator.next()

                if (
                    best.classId == other.classId &&
                    calculateIoU(best, other) > IOU_THRESHOLD
                ) {
                    iterator.remove()
                }
            }
        }

        return result
    }

    private fun calculateIoU(
        a: YoloDetection,
        b: YoloDetection
    ): Float {

        val x1 = max(a.left, b.left)
        val y1 = max(a.top, b.top)
        val x2 = min(a.right, b.right)
        val y2 = min(a.bottom, b.bottom)

        val intersection = max(0f, x2 - x1) *
                max(0f, y2 - y1)

        val areaA = (a.right - a.left) *
                (a.bottom - a.top)

        val areaB = (b.right - b.left) *
                (b.bottom - b.top)

        val union = areaA + areaB - intersection

        return if (union <= 0f) {
            0f
        } else {
            intersection / union
        }
    }

    @Synchronized
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
