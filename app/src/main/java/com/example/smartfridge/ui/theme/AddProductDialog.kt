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
import java.util.Calendar


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
    var expiryDays by remember { mutableStateOf("7") }
    var exact by remember { mutableStateOf(false) }

    val filtered = remember(suggestions, query) {
        if (query.isBlank()) suggestions
        else suggestions.filter { it.name.contains(query, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 690.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 18.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().background(Brush.linearGradient(if (MaterialTheme.colorScheme.background == Color(0xFF121316)) listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface) else listOf(Color(0xFF2E8968), Color(0xFF69B88E))))
                        .padding(horizontal = 22.dp, vertical = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("МОЙ ХОЛОДИЛЬНИК", color = Color.White.copy(.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                            Spacer(Modifier.height(5.dp))
                            Text("Добавить продукт", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Выбери из каталога или добавь свой", color = Color.White.copy(.88f), fontSize = 12.sp)
                        }
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(.18f)), contentAlignment = Alignment.Center) {
                            Text("🥑", fontSize = 25.sp)
                        }
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it; onSearch(it) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Поиск продукта…") },
                        leadingIcon = { Text("⌕", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFFE5EBE6))
                    )
                    Text(if (customMode) "Свой продукт" else "Выбери продукт", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (!customMode) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(filtered, key = { it.id }) { product ->
                                val active = selected?.id == product.id
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
                                        .clickable { selected = product; expiryDays = product.defaultShelfLifeDays.toString() }
                                        .padding(horizontal = 13.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                                        Text(product.icon, fontSize = 22.sp)
                                    }
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(product.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(product.category, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                    if (active) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                            if (filtered.isEmpty()) item {
                                Text("В каталоге ничего не найдено. Можно добавить свой продукт.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { customMode = true; selected = null; customName = query }
                                .padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                Text("＋", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
                            }
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text("Другое", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                        TextButton(onClick = { customMode = false }) { Text("← Вернуться к каталогу", color = MaterialTheme.colorScheme.primary) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = quantity, onValueChange = { quantity = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                            modifier = Modifier.weight(1f), singleLine = true, label = { Text("Количество") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp)
                        )
                        OutlinedTextField(
                            value = expiryDays, onValueChange = { expiryDays = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f), singleLine = true, label = { Text("Срок, дней") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = exact, onCheckedChange = { exact = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                        Text("Срок годности указан точно", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(15.dp)) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            val chosen = selected
                            val name = if (customMode) customName.trim() else chosen?.name.orEmpty()
                            val category = if (customMode) "Другое" else chosen?.category ?: "Другое"
                            val icon = if (customMode) "📦" else chosen?.icon ?: "📦"
                            val days = (expiryDays.toIntOrNull() ?: 7).coerceAtLeast(0)
                            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
                            onAdd(name, category, icon, quantity.replace(',', '.').toFloatOrNull() ?: 1f, calendar.timeInMillis, exact)
                        },
                        enabled = if (customMode) customName.isNotBlank() else selected != null,
                        modifier = Modifier.weight(1.25f).height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Добавить", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
