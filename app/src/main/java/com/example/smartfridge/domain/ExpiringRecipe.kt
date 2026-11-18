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
        aliases = listOf("банан", "яблок", "груш", "апельсин", "ягод", "йогурт"),
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
    ),
    RecipeDefinition(
        title = "Куриный суп с овощами",
        description = "Лёгкий суп, чтобы использовать курицу вовремя",
        durationMinutes = 35,
        aliases = listOf("куриц", "мяс"),
        ingredients = listOf("Курица или мясо", "Картофель", "Морковь", "Лук", "Зелень"),
        instructions = listOf(
            "Нарежьте мясо небольшими кусочками и отварите до готовности.",
            "Добавьте нарезанные овощи и варите до мягкости.",
            "Посолите, добавьте зелень и дайте супу настояться."
        )
    ),
    RecipeDefinition(
        title = "Домашние тефтели",
        description = "Сытное блюдо из фарша или свежего мяса",
        durationMinutes = 30,
        aliases = listOf("мяс"),
        ingredients = listOf("Мясо или фарш", "Рис", "Лук", "Томатный соус"),
        instructions = listOf(
            "Подготовьте фарш, смешайте с рисом и мелко нарезанным луком.",
            "Сформируйте небольшие тефтели и выложите в глубокую сковороду.",
            "Добавьте томатный соус и тушите до полной готовности мяса."
        )
    ),
    RecipeDefinition(
        title = "Курица с рисом",
        description = "Простой горячий обед из куриного филе",
        durationMinutes = 30,
        aliases = listOf("куриц"),
        ingredients = listOf("Куриное филе", "Рис", "Лук", "Специи"),
        instructions = listOf(
            "Отварите рис почти до готовности.",
            "Обжарьте кусочки курицы с луком до полной готовности.",
            "Смешайте с рисом, добавьте немного воды и прогрейте под крышкой."
        )
    ),
    RecipeDefinition(
        title = "Гренки с яйцом и молоком",
        description = "Быстрый завтрак для хлеба, который нужно использовать",
        durationMinutes = 10,
        aliases = listOf("хлеб", "яйц", "молок"),
        ingredients = listOf("Хлеб", "Яйцо", "Молоко", "Немного масла"),
        instructions = listOf(
            "Взбейте яйцо с небольшим количеством молока.",
            "Обмакните ломтики хлеба в смесь с обеих сторон.",
            "Обжарьте на сковороде до золотистой корочки."
        )
    ),
    RecipeDefinition(
        title = "Горячие бутерброды",
        description = "Удобный способ использовать хлеб, сыр и колбасу",
        durationMinutes = 12,
        aliases = listOf("хлеб", "сыр", "колбас"),
        ingredients = listOf("Хлеб", "Сыр", "Колбаса или овощи"),
        instructions = listOf(
            "Выложите на хлеб ломтики сыра и колбасы или овощей.",
            "Поместите бутерброды в духовку или под крышку на сковороду.",
            "Готовьте, пока сыр не расплавится."
        )
    ),
    RecipeDefinition(
        title = "Огуречный салат с йогуртом",
        description = "Освежающий салат из огурцов и молочных продуктов",
        durationMinutes = 10,
        aliases = listOf("огур", "йогурт", "сметан"),
        ingredients = listOf("Огурцы", "Йогурт или сметана", "Зелень", "Соль"),
        instructions = listOf(
            "Нарежьте огурцы тонкими ломтиками.",
            "Смешайте йогурт или сметану с зеленью и солью.",
            "Заправьте огурцы непосредственно перед подачей."
        )
    ),
    RecipeDefinition(
        title = "Фруктовый йогурт",
        description = "Лёгкий десерт из спелых фруктов и йогурта",
        durationMinutes = 5,
        aliases = listOf("яблок", "банан", "апельсин", "йогурт", "десерт"),
        ingredients = listOf("Фрукты", "Йогурт", "Орехи или сухофрукты по желанию"),
        instructions = listOf(
            "Нарежьте фрукты небольшими кусочками.",
            "Выложите слоями фрукты и йогурт.",
            "Добавьте орехи или сухофрукты, если они есть."
        )
    ),
    RecipeDefinition(
        title = "Яблочная овсянка",
        description = "Тёплый завтрак с яблоком и молоком",
        durationMinutes = 12,
        aliases = listOf("яблок", "молок", "йогурт"),
        ingredients = listOf("Яблоко", "Овсяные хлопья", "Молоко или йогурт", "Корица"),
        instructions = listOf(
            "Нарежьте яблоко небольшими кубиками.",
            "Сварите овсяные хлопья на молоке до желаемой густоты.",
            "Добавьте яблоко и корицу, прогрейте ещё пару минут."
        )
    ),
    RecipeDefinition(
        title = "Банановая овсянка",
        description = "Сытный завтрак для спелых бананов",
        durationMinutes = 10,
        aliases = listOf("банан", "молок", "йогурт"),
        ingredients = listOf("Банан", "Овсяные хлопья", "Молоко или йогурт"),
        instructions = listOf(
            "Разомните половину банана вилкой.",
            "Сварите овсянку на молоке и вмешайте банановое пюре.",
            "Подавайте с оставшимися ломтиками банана."
        )
    ),
    RecipeDefinition(
        title = "Картофельные драники",
        description = "Хрустящие драники из картофеля, который пора использовать",
        durationMinutes = 25,
        aliases = listOf("картоф"),
        ingredients = listOf("Картофель", "Яйцо", "Мука", "Соль", "Масло"),
        instructions = listOf(
            "Натрите картофель на крупной тёрке и слегка отожмите лишнюю жидкость.",
            "Добавьте яйцо, немного муки и соль.",
            "Жарьте небольшие драники на разогретой сковороде до золотистой корочки."
        )
    ),
    RecipeDefinition(
        title = "Сосиски или колбаса с овощами",
        description = "Быстрый ужин из мясных продуктов и овощей",
        durationMinutes = 15,
        aliases = listOf("колбас", "огур", "помидор", "томат", "перец"),
        ingredients = listOf("Колбаса", "Овощи", "Масло", "Специи"),
        instructions = listOf(
            "Нарежьте колбасу и овощи.",
            "Обжарьте колбасу, затем добавьте плотные овощи.",
            "Доведите до готовности и подавайте горячим."
        )
    )
)

fun buildExpiringRecipes(
    products: List<FridgeProduct>,
    now: Long = System.currentTimeMillis()
): List<ExpiringRecipeSuggestion> {
    val startOfToday = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val limit = (startOfToday.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, 2)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    val expiring = products.filter {
        it.expiresAt >= startOfToday.timeInMillis && it.expiresAt <= limit
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
