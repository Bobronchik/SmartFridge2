package com.example.smartfridge.data

object DatabaseSeeder {

    fun products(): List<ProductCatalogEntity> {

        return listOf(

            ProductCatalogEntity(
                name = "Молоко",
                category = "Молочные продукты",
                defaultShelfLifeDays = 7,
                icon = "🥛"
            ),

            ProductCatalogEntity(
                name = "Сыр",
                category = "Молочные продукты",
                defaultShelfLifeDays = 30,
                icon = "🧀"
            ),

            ProductCatalogEntity(
                name = "Йогурт",
                category = "Молочные продукты",
                defaultShelfLifeDays = 14,
                icon = "🥛"
            ),

            ProductCatalogEntity(
                name = "Сметана",
                category = "Молочные продукты",
                defaultShelfLifeDays = 14,
                icon = "🥛"
            ),

            ProductCatalogEntity(
                name = "Масло",
                category = "Молочные продукты",
                defaultShelfLifeDays = 30,
                icon = "🧈"
            ),

            ProductCatalogEntity(
                name = "Яйца",
                category = "Яйца",
                defaultShelfLifeDays = 30,
                icon = "🥚"
            ),

            ProductCatalogEntity(
                name = "Яблоки",
                category = "Фрукты",
                defaultShelfLifeDays = 30,
                icon = "🍎"
            ),

            ProductCatalogEntity(
                name = "Бананы",
                category = "Фрукты",
                defaultShelfLifeDays = 7,
                icon = "🍌"
            ),

            ProductCatalogEntity(
                name = "Апельсины",
                category = "Фрукты",
                defaultShelfLifeDays = 14,
                icon = "🍊"
            ),

            ProductCatalogEntity(
                name = "Огурцы",
                category = "Овощи",
                defaultShelfLifeDays = 7,
                icon = "🥒"
            ),

            ProductCatalogEntity(
                name = "Помидоры",
                category = "Овощи",
                defaultShelfLifeDays = 7,
                icon = "🍅"
            ),

            ProductCatalogEntity(
                name = "Картофель",
                category = "Овощи",
                defaultShelfLifeDays = 30,
                icon = "🥔"
            ),

            ProductCatalogEntity(
                name = "Куриное филе",
                category = "Мясо",
                defaultShelfLifeDays = 3,
                icon = "🍗"
            ),

            ProductCatalogEntity(
                name = "Мясо",
                category = "Мясо",
                defaultShelfLifeDays = 3,
                icon = "🥩"
            ),

            ProductCatalogEntity(
                name = "Колбаса",
                category = "Мясные продукты",
                defaultShelfLifeDays = 10,
                icon = "🥩"
            ),

            ProductCatalogEntity(
                name = "Хлеб",
                category = "Выпечка",
                defaultShelfLifeDays = 5,
                icon = "🍞"
            ),

            ProductCatalogEntity(
                name = "Сок",
                category = "Напитки",
                defaultShelfLifeDays = 14,
                icon = "🧃"
            ),

            ProductCatalogEntity(
                name = "Вода",
                category = "Напитки",
                defaultShelfLifeDays = 365,
                icon = "💧"
            ),

            ProductCatalogEntity(
                name = "Шоколад",
                category = "Сладости",
                defaultShelfLifeDays = 180,
                icon = "🍫"
            ),

            ProductCatalogEntity(
                name = "Конфеты",
                category = "Сладости",
                defaultShelfLifeDays = 120,
                icon = "🍬"
            ),

            ProductCatalogEntity(
                name = "Консервы",
                category = "Бакалея",
                defaultShelfLifeDays = 365,
                icon = "🥫"
            ),

            ProductCatalogEntity(
                name = "Снеки",
                category = "Снеки",
                defaultShelfLifeDays = 90,
                icon = "🍿"
            ),

            ProductCatalogEntity(
                name = "Приправы",
                category = "Бакалея",
                defaultShelfLifeDays = 365,
                icon = "🧂"
            ),

            ProductCatalogEntity(
                name = "Сухофрукты",
                category = "Сухофрукты",
                defaultShelfLifeDays = 180,
                icon = "🍇"
            ),

            ProductCatalogEntity(
                name = "Лапша быстрого приготовления",
                category = "Бакалея",
                defaultShelfLifeDays = 180,
                icon = "🍜"
            ),

            ProductCatalogEntity(
                name = "Жвачка",
                category = "Сладости",
                defaultShelfLifeDays = 365,
                icon = "🍬"
            ),

            ProductCatalogEntity(
                name = "Десерт",
                category = "Десерты",
                defaultShelfLifeDays = 7,
                icon = "🍰"
            )
        )
    }
}