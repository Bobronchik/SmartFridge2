package com.example.smartfridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfridge.domain.Freshness
import com.example.smartfridge.domain.FridgeProduct
import com.example.smartfridge.domain.getFreshness
import com.example.smartfridge.domain.buildExpiringRecipes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

private val Canvas = Color(0xFFF7F8F4)
private val CardWhite = Color(0xFFFFFFFF)

private enum class MainTab(val title: String, val icon: String) {
    FRIDGE("Холодильник", "🥬"),
    RECIPES("Рецепты", "🍲"),
    SETTINGS("Настройки", "⚙"),
    DEBUG("Dev", "⌘")
}

@Composable
fun FridgeScreen(
    state: FridgeUiState,
    onSearch: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onScan: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (FridgeProduct) -> Unit = {},
    darkTheme: Boolean = false,
    onThemeChange: (Boolean) -> Unit = {},
    cameraAddress: String = "",
    onCameraAddressChange: (String) -> Unit = {},
    onSaveSettings: () -> Unit = {},
    onFindCamera: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(MainTab.FRIDGE) }
    var productToDelete by remember { mutableStateOf<FridgeProduct?>(null) }

    val expiringCount = state.allProducts.count {
        getFreshness(it.expiresAt) == Freshness.EXPIRING_SOON
    }
    val expiredCount = state.allProducts.count {
        getFreshness(it.expiresAt) == Freshness.EXPIRED
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScan,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(62.dp)
            ) {
                Text("⌗", fontSize = 27.sp, fontWeight = FontWeight.Light)
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                tonalElevation = 8.dp
            ) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Text(tab.icon, fontSize = if (selectedTab == tab) 21.sp else 19.sp)
                        },
                        label = {
                            Text(tab.title, fontSize = 10.sp, maxLines = 1)
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { scaffoldPadding ->
        when (selectedTab) {
            MainTab.FRIDGE -> FridgeHome(
                state = state,
                expiringCount = expiringCount,
                expiredCount = expiredCount,
                padding = scaffoldPadding,
                onSearch = onSearch,
                onCategory = onCategory,
                onAdd = onAdd,
                onDelete = { productToDelete = it }
            )
            MainTab.RECIPES -> ExpiringRecipesPanel(
                padding = scaffoldPadding,
                products = state.allProducts
            )
            MainTab.SETTINGS -> SettingsPanel(
                padding = scaffoldPadding,
                darkTheme = darkTheme,
                onThemeChange = onThemeChange,
                cameraAddress = cameraAddress,
                onCameraAddressChange = onCameraAddressChange,
                onSave = onSaveSettings,
                onFindCamera = onFindCamera
            )
            MainTab.DEBUG -> DebugPanel(
                padding = scaffoldPadding,
                state = state,
                expiringCount = expiringCount,
                expiredCount = expiredCount
            )
        }
    }

    productToDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Удалить продукт?") },
            text = { Text("«${product.name}» будет удалён из холодильника.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(product)
                    productToDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) { Text("Отмена") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun FridgeHome(
    state: FridgeUiState,
    expiringCount: Int,
    expiredCount: Int,
    padding: PaddingValues,
    onSearch: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onAdd: () -> Unit,
    onDelete: (FridgeProduct) -> Unit
) {
    val visibleCategories = state.products
        .map { it.category }
        .distinct()
        .sorted()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SMARTFRIDGE", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Твой холодильник", color = MaterialTheme.colorScheme.onSurface, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("Всё свежее — под контролем", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Text("🌿", fontSize = 23.sp) }
            }
        }

        item {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            if (MaterialTheme.colorScheme.background == Color(0xFF121316)) {
                                listOf(Color(0xFF282B32), Color(0xFF3A3E47))
                            } else {
                                listOf(Color(0xFF2D8D6D), Color(0xFF67B58E))
                            }
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Text("МОЙ ЗАПАС", color = Color.White.copy(alpha = .78f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(state.allProducts.size.toString(), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("продуктов", color = Color.White.copy(alpha = .9f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatPill("⏳", "$expiringCount скоро", Color.White.copy(alpha = .18f))
                        StatPill("✓", "${state.allProducts.size - expiringCount - expiredCount} свежих", Color.White.copy(alpha = .18f))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallMetric(
                    modifier = Modifier.weight(1f),
                    value = expiringCount.toString(),
                    label = "Скоро истекает",
                    icon = "⏰",
                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                    valueColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                SmallMetric(
                    modifier = Modifier.weight(1f),
                    value = expiredCount.toString(),
                    label = "Просрочено",
                    icon = "⚠️",
                    tint = MaterialTheme.colorScheme.errorContainer,
                    valueColor = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Продукты", color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd) { Text("+ Добавить", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Найти продукт", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Text("⌕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        item {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.category == null,
                        onClick = { onCategory(null) },
                        label = { Text("Все") },
                        shape = RoundedCornerShape(50.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant, selectedLabelColor = MaterialTheme.colorScheme.primary)
                    )
                }
                items(visibleCategories) { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { onCategory(category) },
                        label = { Text(category) },
                        shape = RoundedCornerShape(50.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant, selectedLabelColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        if (state.message != null) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(state.message, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }

        if (state.products.isEmpty()) {
            item {
                EmptyFridge(onAdd)
            }
        } else {
            items(state.products, key = { it.id }) { product ->
                ProductCard(product = product, onDelete = { onDelete(product) })
            }
        }

        item {
            if (state.scanning) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Анализируем снимок…", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(icon: String, text: String, background: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50.dp)).background(background).padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SmallMetric(
    modifier: Modifier,
    value: String,
    label: String,
    icon: String,
    tint: Color,
    valueColor: Color
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(15.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(tint), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProductCard(product: FridgeProduct, onDelete: () -> Unit) {
    val freshness = getFreshness(product.expiresAt)
    val status: String
    val color: Color
    val background: Color
    val progress: Float
    when (freshness) {
        Freshness.FRESH -> {
            status = "Свежий продукт"
            color = MaterialTheme.colorScheme.primary
            background = MaterialTheme.colorScheme.surfaceVariant
            progress = .82f
        }
        Freshness.EXPIRING_SOON -> {
            status = "Скоро испортится"
            color = MaterialTheme.colorScheme.tertiary
            background = MaterialTheme.colorScheme.tertiaryContainer
            progress = .35f
        }
        Freshness.EXPIRED -> {
            status = "Срок истёк"
            color = MaterialTheme.colorScheme.error
            background = MaterialTheme.colorScheme.errorContainer
            progress = .08f
        }
    }
    val expiry = SimpleDateFormat("dd MMM", Locale("ru")).format(Date(product.expiresAt))

    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { Text(product.icon, fontSize = 29.sp) }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${product.quantity} шт. · ${product.category}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(color))
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(expiry, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(5.dp))
                    Text(status, color = color, fontSize = 10.sp)
                    if (!product.isExactDate) {
                        Text(" · дата примерная", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
            TextButton(onClick = onDelete, contentPadding = PaddingValues(4.dp)) {
                Text("⋯", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 21.sp)
            }
        }
    }
}

@Composable
private fun EmptyFridge(onAdd: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🥑", fontSize = 42.sp)
            Spacer(Modifier.height(10.dp))
            Text("Пока здесь пусто", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Добавь первый продукт вручную или отсканируй холодильник", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(15.dp))
            Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(14.dp)) {
                Text("Добавить продукт")
            }
        }
    }
}


private fun isExpiryToday(expiresAt: Long): Boolean {
    val today = Calendar.getInstance()
    val expiry = Calendar.getInstance().apply { timeInMillis = expiresAt }
    return today.get(Calendar.YEAR) == expiry.get(Calendar.YEAR) &&
        today.get(Calendar.DAY_OF_YEAR) == expiry.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun ExpiringRecipesPanel(
    padding: PaddingValues,
    products: List<FridgeProduct>
) {
    val recipes = remember(products) { buildExpiringRecipes(products) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "РЕЦЕПТЫ ПО СРОКАМ",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Успеть приготовить",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Сначала используем продукты, срок которых скоро истекает.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        if (recipes.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🥗", fontSize = 38.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Пока всё свежее",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Когда у продуктов останется не больше двух дней, здесь появятся подходящие рецепты.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ПРИОРИТЕТНЫЕ БЛЮДА",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${recipes.size} идеи",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            items(recipes, key = { it.title }) { recipe ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    recipe.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    recipe.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "${recipe.durationMinutes} мин",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "ИСПОЛЬЗУЕМ СКОРОПОРТЯЩИЕСЯ ПРОДУКТЫ",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            recipe.matchedProducts.forEach { product ->
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(product.icon, fontSize = 17.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            product.name,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            if (isExpiryToday(product.expiresAt)) "сегодня" else "скоро",
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            "Что понадобится",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            recipe.ingredients.joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        recipe.instructions.forEachIndexed { index, instruction ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "${index + 1}.",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    instruction,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturePlaceholder(
    padding: PaddingValues,
    icon: String,
    title: String,
    description: String,
    detail: String
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 38.sp)
        }
        Spacer(Modifier.height(22.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
            Text(detail, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
        }
    }
}

@Composable
private fun SettingsPanel(
    padding: PaddingValues,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    cameraAddress: String,
    onCameraAddressChange: (String) -> Unit,
    onSave: () -> Unit,
    onFindCamera: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("ПЕРСОНАЛИЗАЦИЯ", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Text("Настройки", color = MaterialTheme.colorScheme.onSurface, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Настрой приложение под себя", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(if (darkTheme) "🌙" else "☀️", fontSize = 21.sp) }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) { Text("Тёмная тема", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(if (darkTheme) "Включена" else "Выключена", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                Switch(checked = darkTheme, onCheckedChange = onThemeChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary))
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("📷", fontSize = 21.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Камера холодильника", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text("IP-адрес устройства в локальной сети", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                }
                OutlinedTextField(value = cameraAddress, onValueChange = onCameraAddressChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Адрес камеры") }, placeholder = { Text("Например, 192.168.1.100:8080") }, shape = RoundedCornerShape(15.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(onClick = onFindCamera, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Найти камеру", fontSize = 11.sp) }
                    Button(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Сохранить") }
                }
                Text("Адрес сохраняется на устройстве и используется центральной кнопкой сканирования.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
    }
}
@Composable
private fun DebugPanel(
    padding: PaddingValues,
    state: FridgeUiState,
    expiringCount: Int,
    expiredCount: Int
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Инструменты", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        Text("Диагностика", color = MaterialTheme.colorScheme.onSurface, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Состояние подключённых компонентов", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        DebugRow("База Room", "Подключена", true)
        DebugRow("YOLO", "Инициализируется в ML pipeline", true)
        DebugRow("PaddleOCR", "Инициализируется в ML pipeline", true)
        DebugRow("OpenCV", "Вызов initLocal() при запуске", true)
        DebugRow("Камера", if (state.cameraConnected) "Подключена" else "Не подключена", state.cameraConnected)
        DebugRow("Сканирование", if (state.scanning) "Выполняется" else "Ожидание", !state.scanning)
        HorizontalDivider(color = Color(0xFFE8ECE7))
        Text("Данные сейчас", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        DebugRow("Продуктов", state.products.size.toString(), true)
        DebugRow("Скоро испортятся", expiringCount.toString(), expiringCount == 0)
        DebugRow("Просрочено", expiredCount.toString(), expiredCount == 0)
        Text("Сброс базы и просмотр сырых JSON добавим после подключения отдельного слоя диагностики.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun DebugRow(label: String, value: String, ok: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary))
            Spacer(Modifier.width(10.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}
