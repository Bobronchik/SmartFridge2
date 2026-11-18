package com.example.smartfridge.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ExpiryDateParserTest {
    @Test
    fun rejectsImpossibleCalendarDate() {
        assertNull(ExpiryDateParser.findExpiryDate("Годен до 31.02.2027"))
    }

    @Test
    fun prefersExplicitExpiryOverManufacturingDate() {
        val parsed = ExpiryDateParser.findExpiryDate(
            "Дата изготовления 01.02.2026. Годен до 31.03.2026"
        )
        val calendar = Calendar.getInstance().apply { timeInMillis = requireNotNull(parsed) }
        assertEquals(31, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MARCH, calendar.get(Calendar.MONTH))
        assertEquals(2026, calendar.get(Calendar.YEAR))
    }

    @Test
    fun doesNotTreatManufacturingDateAsExpiry() {
        assertNull(ExpiryDateParser.findExpiryDate("Дата изготовления 01.02.2026"))
    }

    @Test
    fun monthYearExpiryUsesLastDayOfMonth() {
        val parsed = ExpiryDateParser.findExpiryDate("EXP 02.2028")
        val calendar = Calendar.getInstance().apply { timeInMillis = requireNotNull(parsed) }
        assertEquals(29, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH))
    }
}
