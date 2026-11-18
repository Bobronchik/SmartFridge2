package com.example.smartfridge.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSeederTest {
    @Test
    fun catalogIncludesFoodClassesRecognizedByYolo() {
        val names = DatabaseSeeder.products().map { it.name }.toSet()

        assertTrue(
            names.containsAll(
                listOf(
                    "Конфеты",
                    "Консервы",
                    "Шоколад",
                    "Десерт",
                    "Сухофрукты",
                    "Напитки",
                    "Жвачка",
                    "Лапша быстрого приготовления",
                    "Молоко",
                    "Снеки",
                    "Приправы"
                )
            )
        )
    }

    @Test
    fun catalogDoesNotSeedNonFoodDetectorClasses() {
        val names = DatabaseSeeder.products().map { it.name }.toSet()

        assertFalse("Средства гигиены" in names)
        assertFalse("Канцелярия" in names)
        assertFalse("Бумажные салфетки" in names)
        assertFalse("Алкоголь" in names)
        assertFalse("Сушёные продукты" in names)
        assertFalse("Растворимые напитки" in names)
    }

    @Test
    fun catalogNamesAreUnique() {
        val names = DatabaseSeeder.products().map { it.name }
        assertTrue(names.size == names.toSet().size)
    }
}
