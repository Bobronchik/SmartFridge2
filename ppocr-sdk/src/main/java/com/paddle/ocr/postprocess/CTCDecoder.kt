// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.postprocess

import kotlin.math.abs
import kotlin.math.exp

object CTCDecoder {
    private const val BLANK_IDX = 0
    private const val PROBABILITY_SUM_TOLERANCE = 0.05f

    fun decode(output: FloatArray, shape: LongArray, characterList: List<String>): List<Pair<String, Float>> {
        require(shape.size == 3) {
            "Recognition output must have [batch, time, classes] shape, got ${shape.contentToString()}"
        }

        val batchSize = shape[0].toInt()
        val timeSteps = shape[1].toInt()
        val numClasses = shape[2].toInt()
        require(batchSize >= 0 && timeSteps > 0 && numClasses > 1) {
            "Invalid recognition output shape: ${shape.contentToString()}"
        }
        require(numClasses == characterList.size + 1) {
            "Recognition dictionary mismatch: model has $numClasses classes, " +
                "but dictionary has ${characterList.size} characters (+ CTC blank expected)"
        }
        require(output.size == batchSize * timeSteps * numClasses) {
            "Recognition output size ${output.size} does not match shape ${shape.contentToString()}"
        }

        val results = mutableListOf<Pair<String, Float>>()
        for (b in 0 until batchSize) {
            val baseOffset = b * timeSteps * numClasses
            val indices = IntArray(timeSteps)
            val probs = FloatArray(timeSteps)

            for (t in 0 until timeSteps) {
                val offset = baseOffset + t * numClasses
                var maxIdx = 0
                var maxVal = Float.NEGATIVE_INFINITY
                var minVal = Float.POSITIVE_INFINITY
                var rowSum = 0.0

                for (c in 0 until numClasses) {
                    val value = output[offset + c]
                    if (!value.isFinite()) continue
                    if (value > maxVal) {
                        maxVal = value
                        maxIdx = c
                    }
                    if (value < minVal) minVal = value
                    rowSum += value
                }

                if (!maxVal.isFinite()) {
                    indices[t] = BLANK_IDX
                    probs[t] = 0f
                    continue
                }

                // Paddle exports may expose post-softmax probabilities or raw logits.
                // Argmax is identical; normalize only when the row is not already probabilities.
                val isProbabilityRow =
                    minVal >= 0f &&
                    maxVal <= 1f &&
                    abs(rowSum - 1.0) <= PROBABILITY_SUM_TOLERANCE

                val confidence = if (isProbabilityRow) {
                    maxVal
                } else {
                    var denominator = 0.0
                    for (c in 0 until numClasses) {
                        val value = output[offset + c]
                        if (value.isFinite()) {
                            denominator += exp((value - maxVal).toDouble())
                        }
                    }
                    if (denominator > 0.0) (1.0 / denominator).toFloat() else 0f
                }

                indices[t] = maxIdx
                probs[t] = confidence
            }

            val keptProbs = mutableListOf<Float>()
            val sb = StringBuilder()
            var prevIdx = -1
            for (t in 0 until timeSteps) {
                val idx = indices[t]
                if (idx != BLANK_IDX && idx != prevIdx) {
                    val charIdx = idx - 1
                    sb.append(characterList[charIdx])
                    keptProbs.add(probs[t])
                }
                prevIdx = idx
            }

            val confidence = if (keptProbs.isNotEmpty()) keptProbs.average().toFloat() else 0f
            results.add(Pair(sb.toString(), confidence))
        }
        return results
    }
}
