package com.example.smartfridge.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.smartfridge.ml.RecognizedProduct
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
import java.util.TimeZone

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
    onUpdateProduct: (FridgeProduct) -> Unit = {},
    darkTheme: Boolean = false,
    onThemeChange: (Boolean) -> Unit = {},
    cameraAddress: String = "",
    onCameraAddressChange: (String) -> Unit = {},
    onSaveSettings: () -> Unit = {},
    onFindCamera: () -> Unit = {},
    onConfirmScan: () -> Unit = {},
    onDismissScan: () -> Unit = {},
    onUpdatePendingProduct: (Int, String, Long, Boolean) -> Unit = { _, _, _, _ -> },
    onRemovePendingProduct: (Int) -> Unit = {},
    cameraScanIntervalMinutes: Int = 1,
    onCameraScanIntervalChange: (Int) -> Unit = {},
    shoppingItems: List<String> = emptyList(),
    onAddShoppingItem: (String) -> Unit = {},
    onRemoveShoppingItem: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(MainTab.FRIDGE) }
    var productToDelete by remember { mutableStateOf<FridgeProduct?>(null) }
    var productToEdit by remember { mutableStateOf<FridgeProduct?>(null) }
    var statusFilter by remember { mutableStateOf<Freshness?>(null) }
    var showShoppingList by remember { mutableStateOf(false) }

    val expiringCount = state.allProducts.count {
        getFreshness(it.expiresAt) == Freshness.EXPIRING_SOON
    }
    val expiredCount = state.allProducts.count {
        getFreshness(it.expiresAt) == Freshness.EXPIRED
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                tonalElevation = 10.dp
            ) {
                MainTab.entries.take(2).forEach { tab ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.icon, fontSize = if (selectedTab == tab) 21.sp else 19.sp) },
                        label = { Text(tab.title, fontSize = 9.sp, maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    FloatingActionButton(
                        onClick = onScan,
                        modifier = Modifier.size(54.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 5.dp, pressedElevation = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("📷", fontSize = 17.sp)
                            Text("Скан", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                MainTab.entries.drop(2).forEach { tab ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.icon, fontSize = if (selectedTab == tab) 21.sp else 19.sp) },
                        label = { Text(tab.title, fontSize = 9.sp, maxLines = 1) },
                        alwaysShowLabel = true
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
                onDelete = { productToDelete = it },
                onEdit = { productToEdit = it },
                onUpdateProduct = onUpdateProduct,
                statusFilter = statusFilter,
                onStatusFilterChange = { statusFilter = it },
                shoppingItems = shoppingItems,
                onOpenShoppingList = { showShoppingList = true }
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
                onFindCamera = onFindCamera,
                cameraSearching = state.cameraSearching,
                cameraScanIntervalMinutes = cameraScanIntervalMinutes,
                onCameraScanIntervalChange = onCameraScanIntervalChange
            )
            MainTab.DEBUG -> DebugPanel(
                padding = scaffoldPadding,
                state = state,
                expiringCount = expiringCount,
                expiredCount = expiredCount
            )
        }
    }

    if (state.pendingProducts.isNotEmpty()) {
        ScanReviewDialog(
            products = state.pendingProducts,
            onConfirm = onConfirmScan,
            onDismiss = onDismissScan,
            onUpdate = onUpdatePendingProduct,
            onRemove = onRemovePendingProduct
        )
    }

    if (showShoppingList) {
        ShoppingListDialog(items = shoppingItems, onRemove = onRemoveShoppingItem, onDismiss = { showShoppingList = false })
    }

    productToEdit?.let { product ->
        EditProductDialog(
            product = product,
            onDismiss = { productToEdit = null },
            onSave = { updated ->
                onUpdateProduct(updated)
                productToEdit = null
            }
        )
    }

    productToDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Удалить продукт?") },
            text = { Text("«${product.name}» будет удалён из холодильника.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        onDelete(product)
                        productToDelete = null
                    }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = {
                        onAddShoppingItem(product.name)
                        onDelete(product)
                        productToDelete = null
                    }) { Text("И в покупки", color = MaterialTheme.colorScheme.primary) }
                }
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
    onDelete: (FridgeProduct) -> Unit,
    onEdit: (FridgeProduct) -> Unit,
    onUpdateProduct: (FridgeProduct) -> Unit,
    statusFilter: Freshness?,
    onStatusFilterChange: (Freshness?) -> Unit,
    shoppingItems: List<String>,
    onOpenShoppingList: () -> Unit
) {
    val visibleProducts = remember(state.products, statusFilter) {
        state.products.filter { statusFilter == null || getFreshness(it.expiresAt) == statusFilter }
    }
    val visibleCategories = visibleProducts
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
                        Text(productWord(state.allProducts.size), color = Color.White.copy(alpha = .9f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
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
                    valueColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    selected = statusFilter == Freshness.EXPIRING_SOON,
                    onClick = { onStatusFilterChange(if (statusFilter == Freshness.EXPIRING_SOON) null else Freshness.EXPIRING_SOON) }
                )
                SmallMetric(
                    modifier = Modifier.weight(1f),
                    value = expiredCount.toString(),
                    label = "Просрочено",
                    icon = "⚠️",
                    tint = MaterialTheme.colorScheme.errorContainer,
                    valueColor = MaterialTheme.colorScheme.error,
                    selected = statusFilter == Freshness.EXPIRED,
                    onClick = { onStatusFilterChange(if (statusFilter == Freshness.EXPIRED) null else Freshness.EXPIRED) }
                )
            }
        }

        if (state.lastImage != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Последний кадр камеры", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp))
                    ) {
                        val frame = requireNotNull(state.lastImage)
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = "Предпросмотр камеры с рамками распознанных продуктов",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        val scale = minOf(maxWidth.value / frame.width, maxHeight.value / frame.height)
                        val renderedWidth = frame.width * scale
                        val renderedHeight = frame.height * scale
                        val offsetX = (maxWidth.value - renderedWidth) / 2f
                        val offsetY = (maxHeight.value - renderedHeight) / 2f
                        state.previewDetections.forEach { detection ->
                            val left = offsetX + detection.left * scale
                            val top = offsetY + detection.top * scale
                            val width = (detection.right - detection.left) * scale
                            val height = (detection.bottom - detection.top) * scale
                            if (width > 2f && height > 2f) {
                                Box(
                                    Modifier.offset(x = left.dp, y = top.dp)
                                        .size(width = width.dp, height = height.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
                                ) {
                                    Text(
                                        "${detection.name} · ${(detection.confidence * 100).toInt()}%",
                                        modifier = Modifier.align(Alignment.TopStart)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Продукты", color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenShoppingList, contentPadding = PaddingValues(horizontal = 5.dp)) {
                    Text("🛒 ${shoppingItems.size}", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("+ Добавить", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(4.dp))
            Text("Свайп влево — удалить · вправо — изменить", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
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

        if (visibleProducts.isEmpty()) {
            item {
                if (statusFilter == null) EmptyFridge(onAdd)
                else EmptyFilterState(
                    title = if (statusFilter == Freshness.EXPIRED) "Просроченных продуктов нет" else "Всё пока свежее",
                    onClear = { onStatusFilterChange(null) }
                )
            }
        } else {
            items(visibleProducts, key = { it.id }) { product ->
                ProductCard(
                    product = product,
                    onDelete = { onDelete(product) },
                    onEdit = { onEdit(product) },
                    onQuantityChange = { next -> onUpdateProduct(product.copy(quantity = next.coerceAtLeast(0.1f))) }
                )
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
private fun ShoppingListDialog(items: List<String>, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Список покупок") },
        text = {
            if (items.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("🛒", fontSize = 34.sp)
                    Text("Пока ничего не нужно купить", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                    items.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(item, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onRemove(item) }) { Text("Куплено", fontSize = 11.sp) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun EmptyFilterState(title: String, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✨", fontSize = 30.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onClear) { Text("Сбросить фильтр") }
        }
    }
}

private fun productWord(count: Int): String = when {
    count % 100 in 11..14 -> "продуктов"
    count % 10 == 1 -> "продукт"
    count % 10 in 2..4 -> "продукта"
    else -> "продуктов"
}

@Composable
private fun SmallMetric(
    modifier: Modifier,
    value: String,
    label: String,
    icon: String,
    tint: Color,
    valueColor: Color,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick).border(
            width = if (selected) 1.dp else 0.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = RoundedCornerShape(20.dp)
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    ) {
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
private fun ProductCard(
    product: FridgeProduct,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onQuantityChange: (Float) -> Unit
) {
    var horizontalDrag by remember(product.id) { mutableFloatStateOf(0f) }
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
            progress = freshnessProgress(product)
        }
        Freshness.EXPIRING_SOON -> {
            status = "Скоро испортится"
            color = MaterialTheme.colorScheme.tertiary
            background = MaterialTheme.colorScheme.tertiaryContainer
            progress = freshnessProgress(product)
        }
        Freshness.EXPIRED -> {
            status = "Срок истёк"
            color = MaterialTheme.colorScheme.error
            background = MaterialTheme.colorScheme.errorContainer
            progress = freshnessProgress(product)
        }
    }
    val expiry = SimpleDateFormat("dd MMM yyyy", Locale("ru")).format(Date(product.expiresAt))
    val added = SimpleDateFormat("dd MMM, HH:mm", Locale("ru")).format(Date(product.addedAt))
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 650),
        label = "freshness-progress"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().pointerInput(product.id) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    horizontalDrag += amount
                },
                onDragEnd = {
                    when {
                        horizontalDrag < -72f -> onDelete()
                        horizontalDrag > 72f -> onEdit()
                    }
                    horizontalDrag = 0f
                },
                onDragCancel = { horizontalDrag = 0f }
            )
        }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { Text(product.icon, fontSize = 29.sp) }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${product.quantity} шт. · ${product.category}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Text("Добавлен: $added", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Spacer(Modifier.height(3.dp))
                Text("Годен до: $expiry", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF62B889), Color(0xFFE0B35C), Color(0xFFD87568))))
                ) {
                    Box(
                        Modifier.align(Alignment.CenterEnd)
                            .fillMaxWidth(1f - animatedProgress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val step = if (product.quantity >= 1f && product.quantity % 1f == 0f) 1f else .1f
                        onQuantityChange((product.quantity - step).coerceAtLeast(.1f))
                    }, modifier = Modifier.size(30.dp)) { Text("−", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
                    Text(
                        if (product.quantity % 1f == 0f) product.quantity.toInt().toString()
                        else String.format(Locale.getDefault(), "%.1f", product.quantity),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        val step = if (product.quantity >= 1f && product.quantity % 1f == 0f) 1f else .1f
                        onQuantityChange(product.quantity + step)
                    }, modifier = Modifier.size(30.dp)) { Text("+", fontSize = 17.sp, color = MaterialTheme.colorScheme.primary) }
                }
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                    Text("Изменить", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                }
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                }
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


private fun freshnessProgress(product: FridgeProduct, now: Long = System.currentTimeMillis()): Float {
    val total = (product.expiresAt - product.addedAt).coerceAtLeast(1L)
    return ((product.expiresAt - now).toFloat() / total.toFloat()).coerceIn(0f, 1f)
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
    val sortedProducts = remember(products) { products.sortedBy { it.expiresAt } }
    val selectedIds = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    var showResults by remember { mutableStateOf(false) }
    val selectedProducts = sortedProducts.filter { it.id in selectedIds }
    val recipes = remember(selectedProducts, showResults) {
        if (showResults) buildExpiringRecipes(selectedProducts) else emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("УМНЫЙ ПОДБОР", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            Text("Рецепты", color = MaterialTheme.colorScheme.onSurface, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Выбери продукты из холодильника — подберём блюда с ними.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        if (sortedProducts.isEmpty()) {
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🍲", fontSize = 42.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Пока нет ингредиентов", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Добавь продукты в холодильник — и здесь появятся идеи блюд с учётом сроков годности.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ТВОИ ПРОДУКТЫ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("Сначала ближайшие сроки", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                val expiringIds = sortedProducts.filter { getFreshness(it.expiresAt) == Freshness.EXPIRING_SOON }.map { it.id }
                if (expiringIds.isNotEmpty()) {
                    TextButton(onClick = {
                        selectedIds.clear()
                        selectedIds.addAll(expiringIds)
                        showResults = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать всё, что скоро испортится") }
                }
            }
            items(sortedProducts, key = { it.id }) { product ->
                val selected = product.id in selectedIds
                val freshness = getFreshness(product.expiresAt)
                val expiryLabel = SimpleDateFormat("dd MMM", Locale("ru")).format(Date(product.expiresAt))
                val status = when (freshness) {
                    Freshness.EXPIRED -> "Просрочен"
                    Freshness.EXPIRING_SOON -> "Скоро истекает"
                    Freshness.FRESH -> "Свежий"
                }
                val tint = when (freshness) {
                    Freshness.EXPIRED -> MaterialTheme.colorScheme.error
                    Freshness.EXPIRING_SOON -> MaterialTheme.colorScheme.tertiary
                    Freshness.FRESH -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = freshness != Freshness.EXPIRED) {
                        if (selected) selectedIds.remove(product.id) else selectedIds.add(product.id)
                        showResults = false
                    }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selected, enabled = freshness != Freshness.EXPIRED, onCheckedChange = {
                            if (it) selectedIds.add(product.id) else selectedIds.remove(product.id)
                            showResults = false
                        })
                        Text(product.icon, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(product.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("$status · до $expiryLabel", color = tint, fontSize = 10.sp)
                        }
                        if (!product.isExactDate) Text("≈", color = MaterialTheme.colorScheme.tertiary, fontSize = 17.sp)
                    }
                }
            }
            item {
                Button(
                    onClick = { showResults = true },
                    enabled = selectedProducts.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("Найти рецепты (${selectedProducts.size})", fontWeight = FontWeight.SemiBold)
                }
            }
            if (showResults && recipes.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Подходящих рецептов не найдено. Попробуй выбрать другие продукты.", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            if (showResults) {
                item { Text("НАЙДЕННЫЕ РЕЦЕПТЫ", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp) }
                items(recipes, key = { it.title }) { recipe ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(recipe.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(recipe.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                                Text("${recipe.durationMinutes} мин", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            val matchCount = recipe.matchedProducts.size.coerceAtMost(recipe.ingredients.size)
                            val matchPercent = matchCount * 100 / recipe.ingredients.size.coerceAtLeast(1)
                            Text("Совпадение ~$matchPercent% · есть $matchCount из ${recipe.ingredients.size} ингредиентов", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Используем: " + recipe.matchedProducts.joinToString { it.name }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            Text("Ингредиенты: " + recipe.ingredients.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            recipe.instructions.forEachIndexed { index, instruction ->
                                Text("${index + 1}. $instruction", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
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
    onFindCamera: () -> Unit,
    cameraSearching: Boolean,
    cameraScanIntervalMinutes: Int,
    onCameraScanIntervalChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    OutlinedButton(
                        onClick = onFindCamera,
                        enabled = !cameraSearching,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        if (cameraSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(7.dp))
                            Text("Поиск…", fontSize = 11.sp)
                        } else {
                            Text("Найти камеру", fontSize = 11.sp)
                        }
                    }
                    Button(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Сохранить") }
                }
                Text("Адрес сохраняется на устройстве и используется центральной кнопкой сканирования.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        var intervalInput by remember(cameraScanIntervalMinutes) { mutableStateOf(cameraScanIntervalMinutes.toString()) }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Интервал между снимками", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Укажи паузу в минутах между кадрами при сканировании камеры.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                OutlinedTextField(
                    value = intervalInput,
                    onValueChange = { value ->
                        val digits = value.filter { it.isDigit() }.take(4)
                        intervalInput = digits
                        digits.toIntOrNull()?.takeIf { it in 1..1440 }?.let(onCameraScanIntervalChange)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Интервал, минут") },
                    placeholder = { Text("Например, 5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Допустимое значение: от 1 до 1440 минут") },
                    isError = intervalInput.toIntOrNull()?.let { it !in 1..1440 } ?: true,
                    shape = RoundedCornerShape(14.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Сейчас: $cameraScanIntervalMinutes мин.", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Интервал сохраняется отдельно от адреса камеры.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                    Button(
                        onClick = onSave,
                        enabled = intervalInput.toIntOrNull()?.let { it in 1..1440 } == true,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Сохранить") }
                }
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanReviewDialog(
    products: List<RecognizedProduct>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onUpdate: (Int, String, Long, Boolean) -> Unit,
    onRemove: (Int) -> Unit
) {
    var editingDateIndex by remember { mutableStateOf<Int?>(null) }
    val currentIndex = editingDateIndex
    val currentProduct = currentIndex?.let { products.getOrNull(it) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentProduct?.expiresAt?.let(::toDatePickerMillis) ?: toDatePickerMillis(System.currentTimeMillis())
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Проверь распознавание")
                Text("Перед сохранением можно исправить название и дату.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 430.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(products.size) { index ->
                        val product = products[index]
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(product.icon, fontSize = 22.sp)
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = product.name,
                                        onValueChange = { onUpdate(index, it, product.expiresAt, product.isExactDate) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        label = { Text("Название") }
                                    )
                                    TextButton(onClick = { onRemove(index) }) {
                                        Text("Убрать", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        datePickerState.selectedDateMillis = toDatePickerMillis(product.expiresAt)
                                        editingDateIndex = index
                                    }) {
                                        Text("📅 " + SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(product.expiresAt)))
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(if (product.isExactDate) "С упаковки" else "Примерный срок", color = if (product.isExactDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = product.isExactDate,
                                        onCheckedChange = { onUpdate(index, product.name, product.expiresAt, it) }
                                    )
                                    Text("Дата точно считана с упаковки", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (products.isEmpty()) Text("Нет продуктов для добавления.")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = products.isNotEmpty() && products.all { it.name.isNotBlank() }) { Text("Добавить в холодильник") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (currentIndex != null && currentProduct != null) {
        DatePickerDialog(
            onDismissRequest = { editingDateIndex = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onUpdate(currentIndex, currentProduct.name, fromDatePickerMillis(it), true)
                    }
                    editingDateIndex = null
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { editingDateIndex = null }) { Text("Отмена") } }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProductDialog(
    product: FridgeProduct,
    onDismiss: () -> Unit,
    onSave: (FridgeProduct) -> Unit
) {
    var name by remember(product.id) { mutableStateOf(product.name) }
    var quantity by remember(product.id) { mutableStateOf(product.quantity.toString()) }
    var expiryDate by remember(product.id) { mutableStateOf(product.expiresAt) }
    var exact by remember(product.id) { mutableStateOf(product.isExactDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = toDatePickerMillis(expiryDate))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить продукт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { value -> quantity = value.filter { it.isDigit() || it == '.' || it == ',' } },
                    label = { Text("Количество") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedButton(onClick = {
                    datePickerState.selectedDateMillis = toDatePickerMillis(expiryDate)
                    showDatePicker = true
                }) {
                    Text("📅 " + SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(expiryDate)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = exact, onCheckedChange = { exact = it })
                    Text("Дата точная (с упаковки)", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedQuantity = quantity.replace(',', '.').toFloatOrNull()
                    if (name.isNotBlank() && parsedQuantity != null && parsedQuantity.isFinite() && parsedQuantity > 0f) {
                        onSave(product.copy(name = name.trim(), quantity = parsedQuantity, expiresAt = expiryDate, isExactDate = exact))
                    }
                },
                enabled = name.isNotBlank() && (quantity.replace(',', '.').toFloatOrNull()?.let { it.isFinite() && it > 0f } == true)
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        expiryDate = fromDatePickerMillis(it)
                        exact = true
                    }
                    showDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = datePickerState, showModeToggle = false) }
    }
}


private fun toDatePickerMillis(localDateMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localDateMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

private fun fromDatePickerMillis(utcDateMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDateMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
