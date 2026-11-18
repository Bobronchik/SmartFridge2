package com.example.smartfridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.smartfridge.data.ProductCatalogEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductDialog(
    suggestions: List<ProductCatalogEntity>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Float, Long, Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ProductCatalogEntity?>(null) }
    var customMode by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var expiryDate by remember {
        mutableStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis)
    }
    var exact by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = expiryDate)
    val filtered = remember(suggestions, query) {
        if (query.isBlank()) suggestions
        else suggestions.filter { it.name.contains(query, ignoreCase = true) }
    }
    val dateLabel = remember(expiryDate) {
        SimpleDateFormat("dd MMMM yyyy", Locale("ru")).format(Date(expiryDate))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 720.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 18.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(
                            if (MaterialTheme.colorScheme.background == Color(0xFF121316))
                                listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                            else listOf(Color(0xFF2E8968), Color(0xFF69B88E))
                        ))
                        .padding(horizontal = 22.dp, vertical = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("МОЙ ХОЛОДИЛЬНИК", color = Color.White.copy(.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                            Spacer(Modifier.height(5.dp))
                            Text("Добавить продукт", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Выбери продукт и проверь срок годности", color = Color.White.copy(.88f), fontSize = 12.sp)
                        }
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(.18f)), contentAlignment = Alignment.Center) {
                            Text("🥑", fontSize = 25.sp)
                        }
                    }
                }

                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    if (!customMode) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it; onSearch(it) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("Поиск продукта…") },
                            leadingIcon = { Text("⌕", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            shape = RoundedCornerShape(16.dp)
                        )
                        Text("Каталог продуктов", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 210.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(filtered, key = { it.id }) { product ->
                                val active = selected?.id == product.id
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
                                        .clickable {
                                            selected = product
                                            expiryDate = Calendar.getInstance().apply {
                                                add(Calendar.DAY_OF_YEAR, product.defaultShelfLifeDays.coerceAtLeast(1))
                                            }.timeInMillis
                                            datePickerState.selectedDateMillis = expiryDate
                                            exact = false
                                        }
                                        .padding(horizontal = 13.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                                        Text(product.icon, fontSize = 22.sp)
                                    }
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(product.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${product.category} · примерно ${product.defaultShelfLifeDays} дн.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                    if (active) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                            if (filtered.isEmpty()) item {
                                Text("Ничего не найдено. Можно добавить название вручную.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { customMode = true; selected = null; customName = query }
                                .padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("＋", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text("Другой продукт", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Вписать название вручную", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = customName, onValueChange = { customName = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Название продукта") }, placeholder = { Text("Например, тофу") },
                            shape = RoundedCornerShape(15.dp)
                        )
                        TextButton(onClick = { customMode = false }) { Text("← Вернуться к каталогу") }
                    }

                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { value -> quantity = value.filter { it.isDigit() || it == '.' || it == ',' }.take(8) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Количество (шт. или кг)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().clickable { datePickerState.selectedDateMillis = expiryDate; showDatePicker = true }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Срок годности", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                Text(dateLabel, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(if (exact) "Дата указана на упаковке" else "Примерная дата по сроку хранения", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                            Text("Изменить", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exact, onCheckedChange = { exact = it })
                        Text("Это точная дата с упаковки", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }

                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(15.dp)) { Text("Отмена") }
                    Button(
                        onClick = {
                            val name = if (customMode) customName.trim() else selected?.name.orEmpty()
                            val q = quantity.replace(',', '.').toFloatOrNull()
                            when {
                                name.isBlank() -> validationError = "Выбери продукт или введи его название."
                                q == null || !q.isFinite() || q <= 0f -> validationError = "Количество должно быть больше нуля."
                                expiryDate < startOfTodayMillis() -> validationError = "Дата срока годности не может быть в прошлом."
                                else -> {
                                    val product = selected
                                    onAdd(
                                        name,
                                        if (customMode) "Другое" else product?.category ?: "Другое",
                                        if (customMode) "📦" else product?.icon ?: "📦",
                                        q,
                                        endOfSelectedDay(expiryDate),
                                        exact
                                    )
                                }
                            }
                        },
                        enabled = customMode || selected != null,
                        modifier = Modifier.weight(1.25f).height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Добавить", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        expiryDate = it
                        exact = true
                    }
                    showDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfSelectedDay(value: Long): Long = Calendar.getInstance().apply {
    timeInMillis = value
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
}.timeInMillis
