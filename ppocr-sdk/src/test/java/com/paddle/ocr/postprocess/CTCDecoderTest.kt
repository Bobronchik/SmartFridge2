package com.paddle.ocr.postprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CTCDecoderTest {

    @Test
    fun collapsesRepeatedCharactersAndSkipsBlank() {
        // Classes: blank, A, B
        val output = floatArrayOf(
            0.90f, 0.08f, 0.02f,
            0.05f, 0.90f, 0.05f,
            0.04f, 0.92f, 0.04f,
            0.90f, 0.05f, 0.05f,
            0.03f, 0.07f, 0.90f
        )

        val result = CTCDecoder.decode(
            output = output,
            shape = longArrayOf(1, 5, 3),
            characterList = listOf("A", "B")
        )

        assertEquals("AB", result.single().first)
        assertTrue(result.single().second > 0.85f)
    }

    @Test
    fun computesConfidenceFromRawLogits() {
        // Argmax sequence is A, B; rows are logits rather than probabilities.
        val output = floatArrayOf(
            0.0f, 5.0f, 1.0f,
            0.0f, 1.0f, 5.0f
        )

        val result = CTCDecoder.decode(
            output = output,
            shape = longArrayOf(1, 2, 3),
            characterList = listOf("A", "B")
        )

        assertEquals("AB", result.single().first)
        assertTrue(result.single().second > 0.9f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDictionaryThatDoesNotMatchModelOutput() {
        CTCDecoder.decode(
            output = floatArrayOf(0.1f, 0.9f, 0.0f),
            shape = longArrayOf(1, 1, 3),
            characterList = listOf("A")
        )
    }
}
