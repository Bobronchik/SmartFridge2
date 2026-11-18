package com.example.smartfridge.ml

import java.util.Calendar
import java.util.Locale

object ExpiryDateParser {
    private val explicitExpiryPatterns = listOf(
        Regex(
            """(?:срок\s*годности|годен|годна|годно|употребить|употреблять)\s*(?:до)?\s*:?\s*(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """(?:exp(?:iry)?(?:\s*date)?|best\s*before|use\s*by)\s*:?\s*(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""",
            RegexOption.IGNORE_CASE
        )
    )

    private val explicitMonthYearPatterns = listOf(
        Regex("""(?:срок\s*годности|годен|годна|годно|употребить)\s*(?:до)?\s*:?\s*(\d{1,2})[./-](\d{4})""", RegexOption.IGNORE_CASE),
        Regex("""(?:exp(?:iry)?(?:\s*date)?|best\s*before|use\s*by)\s*:?\s*(\d{1,2})[./-](\d{4})""", RegexOption.IGNORE_CASE)
    )

    private val genericDatePattern = Regex("""(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""")
    private val manufacturingMarker = Regex(
        """(дата\s*изготовления|изготовлен[оа]?|произведен[оа]?|дата\s*производства|mfg|manufactured)""",
        RegexOption.IGNORE_CASE
    )
    private val explicitExpiryMarker = Regex(
        """(срок\s*годности|годен|годна|годно|употребить|exp(?:iry)?(?:\s*date)?|best\s*before|use\s*by)""",
        RegexOption.IGNORE_CASE
    )

    fun findExpiryDate(text: String): Long? {
        if (text.isBlank()) return null

        for (pattern in explicitExpiryPatterns) {
            val match = pattern.find(text) ?: continue
            val day = match.groupValues[1].toIntOrNull() ?: continue
            val month = match.groupValues[2].toIntOrNull() ?: continue
            var year = match.groupValues[3].toIntOrNull() ?: continue
            if (year < 100) year += 2000
            createDate(day, month, year)?.let { return it }
        }

        for (pattern in explicitMonthYearPatterns) {
            val match = pattern.find(text) ?: continue
            val month = match.groupValues[1].toIntOrNull() ?: continue
            val year = match.groupValues[2].toIntOrNull() ?: continue
            createDate(lastDayOfMonth(month, year), month, year)?.let { return it }
        }

        // A bare date is ambiguous. If the package explicitly labels a production
        // date but OCR missed the expiry label, never mistake MFG for EXP.
        if (manufacturingMarker.containsMatchIn(text) && !explicitExpiryMarker.containsMatchIn(text)) {
            return null
        }

        val match = genericDatePattern.find(text) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        var year = match.groupValues[3].toIntOrNull() ?: return null
        if (year < 100) year += 2000
        return createDate(day, month, year)
    }

    private fun lastDayOfMonth(month: Int, year: Int): Int? {
        if (month !in 1..12 || year !in 2000..2200) return null
        return Calendar.getInstance(Locale.ROOT).apply {
            clear()
            set(year, month - 1, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun createDate(day: Int?, month: Int, year: Int): Long? {
        if (day == null || day !in 1..31 || month !in 1..12 || year !in 2000..2200) return null
        return try {
            Calendar.getInstance().apply {
                isLenient = false
                clear()
                set(year, month - 1, day, 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        } catch (_: Exception) {
            null
        }
    }
}
