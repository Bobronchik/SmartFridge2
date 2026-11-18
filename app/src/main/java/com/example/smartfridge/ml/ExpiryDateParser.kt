package com.example.smartfridge.ml

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ExpiryDateParser {

    private val patterns =
        listOf(

            Regex(
                """(?:годен|годна|годно|употребить)\s*(?:до)?\s*:?\s*(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""",
                RegexOption.IGNORE_CASE
            ),

            Regex(
                """(?:exp|best before)\s*:?\s*(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""",
                RegexOption.IGNORE_CASE
            ),

            Regex(
                """(\d{1,2})[./-](\d{1,2})[./-](\d{4})"""
            ),

            Regex(
                """(\d{1,2})[./-](\d{1,2})[./-](\d{2})"""
            )
        )

    fun findExpiryDate(
        text: String
    ): Long? {

        for (pattern in patterns) {

            val match =
                pattern.find(text)
                    ?: continue

            val day =
                match.groupValues[1]
                    .toIntOrNull()
                    ?: continue

            val month =
                match.groupValues[2]
                    .toIntOrNull()
                    ?: continue

            var year =
                match.groupValues[3]
                    .toIntOrNull()
                    ?: continue

            if (year < 100) {
                year += 2000
            }

            if (
                day !in 1..31 ||
                month !in 1..12
            ) {
                continue
            }

            return createDate(
                day,
                month,
                year
            )
        }

        return null
    }

    private fun createDate(
        day: Int,
        month: Int,
        year: Int
    ): Long? {

        return try {

            val calendar =
                Calendar.getInstance().apply {
                    isLenient = false
                }

            calendar.clear()

            calendar.set(
                year,
                month - 1,
                day,
                23,
                59,
                59
            )

            calendar.timeInMillis

        } catch (_: Exception) {

            null
        }
    }
}