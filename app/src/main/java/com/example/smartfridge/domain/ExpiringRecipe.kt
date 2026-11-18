package com.example.smartfridge.domain

import java.util.Calendar

data class ExpiringRecipeSuggestion(
    val title: String,
    val description: String,
    val durationMinutes: Int,
    val matchedProducts: List<FridgeProduct>,
    val ingredients: List<String>,
    val instructions: List<String>
)

private data class RecipeDefinition(
    val title: String,
    val description: String,
    val durationMinutes: Int,
    val aliases: List<String>,
    val ingredients: List<String>,
    val instructions: List<String>
)

private val recipeCatalog = listOf(
    RecipeDefinition(
        title = "Нежный омлет",
        description = "Быстрый завтрак с сыром и молоком",
        durationMinutes = 15,
        aliases = listOf("яйц", "молок", "сыр", "помидор", "томат"),
        ingredients = listOf("Яйца", "Молоко", "Сыр", "Зелень по вкусу"),
        instructions = listOf(
            "Взбейте яйца с небольшим количеством молока.",
            "Добавьте тёртый сыр и нарезанные овощи.",
            "Готовьте под крышкой на слабом огне до схватывания."
        )
    ),
    RecipeDefinition(
        title = "Тёплый салат с овощами",
        description = "Лёгкое блюдо, чтобы использовать свежие овощи",
        durationMinutes = 20,
        aliases = listOf("огур", "помидор", "томат", "перец", "кабач", "зелень"),
        ingredients = listOf("Овощи из холодильника", "Оливковое масло", "Соль и специи"),
        instructions = listOf(
            "Нарежьте овощи крупными кусочками.",
            "Быстро обжарьте плотные овощи на сковороде.",
            "Добавьте свежие овощи, масло и специи перед подачей."
        )
    ),
    RecipeDefinition(
        title = "Сливочная паста с курицей",
        description = "Сытный ужин из курицы, сливок и сыра",
        durationMinutes = 25,
        aliases = listOf("куриц", "слив", "сыр", "молок"),
        ingredients = listOf("Курица", "Сливки или молоко", "Сыр", "Макароны"),
        instructions = listOf(
            "Отварите макароны до готовности.",
            "Обжарьте курицу до полной готовности и добавьте сливки.",
            "Смешайте с макаронами, добавьте сыр и прогрейте."
        )
    ),
    RecipeDefinition(
        title = "Картофельная запеканка",
        description = "Домашняя запеканка с сырной корочкой",
        durationMinutes = 40,
        aliases = listOf("картоф", "сыр", "слив", "молок", "брокколи"),
        ingredients = listOf("Картофель", "Сыр", "Молоко или сливки", "Специи"),
        instructions = listOf(
            "Нарежьте картофель тонкими ломтиками.",
            "Выложите в форму, залейте молоком или сливками.",
            "Посыпьте сыром и запекайте до мягкости картофеля."
        )
    ),
    RecipeDefinition(
        title = "Банановые панкейки",
        description = "Мягкие панкейки для спелых бананов",
        durationMinutes = 20,
        aliases = listOf("банан", "яйц", "молок", "кефир"),
        ingredients = listOf("Банан", "Яйцо", "Молоко или кефир", "Мука"),
        instructions = listOf(
            "Разомните банан вилкой и смешайте с яйцом.",
            "Добавьте молоко или кефир и муку до густоты сметаны.",
            "Жарьте небольшие панкейки на среднем огне с двух сторон."
        )
    ),
    RecipeDefinition(
        title = "Фруктовый смузи",
        description = "Способ использовать мягкие и спелые фрукты",
        durationMinutes = 5,
        aliases = listOf("банан", "яблок", "груш", "ягод", "йогурт"),
        ingredients = listOf("Спелые фрукты или ягоды", "Йогурт или молоко"),
        instructions = listOf(
            "Очистите фрукты и нарежьте небольшими кусочками.",
            "Добавьте йогурт или молоко.",
            "Взбейте блендером до однородной консистенции."
        )
    ),
    RecipeDefinition(
        title = "Овощное рагу",
        description = "Горячее блюдо из овощей, которые нужно использовать",
        durationMinutes = 30,
        aliases = listOf("кабач", "перец", "помидор", "томат", "морков", "картоф"),
        ingredients = listOf("Овощи из холодильника", "Лук", "Масло", "Специи"),
        instructions = listOf(
            "Нарежьте овощи примерно одинаковыми кусочками.",
            "Сначала обжарьте плотные овощи, затем добавьте остальные.",
            "Тушите под крышкой до мягкости и приправьте."
        )
    ),
    RecipeDefinition(
        title = "Творожные сырники",
        description = "Нежные сырники для творога с близким сроком годности",
        durationMinutes = 20,
        aliases = listOf("творог", "яйц", "сметан"),
        ingredients = listOf("Творог", "Яйцо", "Мука", "Сметана для подачи"),
        instructions = listOf(
            "Разомните творог и смешайте с яйцом и мукой.",
            "Сформируйте небольшие сырники и слегка обваляйте в муке.",
            "Обжарьте на среднем огне до золотистой корочки с обеих сторон."
        )
    )
)

fun buildExpiringRecipes(
    products: List<FridgeProduct>,
    now: Long = System.currentTimeMillis()
): List<ExpiringRecipeSuggestion> {
    val limit = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 2)
    }.timeInMillis

    val expiring = products.filter {
        it.expiresAt > now && it.expiresAt <= limit
    }.sortedBy { it.expiresAt }

    if (expiring.isEmpty()) return emptyList()

    val ranked = recipeCatalog.mapNotNull { recipe ->
        val matched = expiring.filter { product ->
            val normalizedName = product.name.lowercase()
            recipe.aliases.any { alias -> normalizedName.contains(alias) }
        }
        if (matched.isEmpty()) {
            null
        } else {
            recipe to matched
        }
    }.sortedWith(
        compareByDescending<Pair<RecipeDefinition, List<FridgeProduct>>> { it.second.size }
            .thenBy { it.second.minOf(FridgeProduct::expiresAt) }
    )

    return ranked.take(3).map { (recipe, matched) ->
        ExpiringRecipeSuggestion(
            title = recipe.title,
            description = recipe.description,
            durationMinutes = recipe.durationMinutes,
            matchedProducts = matched,
            ingredients = recipe.ingredients,
            instructions = recipe.instructions
        )
    }
}
