package com.example.smartfridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.smartfridge.data.ProductCatalogEntity
import java.util.Calendar

@Composable
fun AddProductDialog(
    suggestions: List<ProductCatalogEntity>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Float, Long, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var category by remember { mutableStateOf("Другое") }
    var icon by remember { mutableStateOf("📦") }
    var expiryDays by remember { mutableStateOf("7") }
    var exact by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<String?>(null) }

    val parsedQuantity = quantity.replace(',', '.').toFloatOrNull()
    val parsedDays = expiryDays.toIntOrNull()
    val canAdd = name.isNotBlank() &&
        parsedQuantity != null && parsedQuantity > 0f &&
        parsedDays != null && parsedDays >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Добавить продукт", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Выберите продукт и укажите количество",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        selectedProduct = null
                        category = "Другое"
                        icon = "📦"
                        expiryDays = "7"
                        onSearch(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text("Поиск продукта") },
                    placeholder = { Text("Например, молоко или яблоки") }
                )

                if (suggestions.isNotEmpty()) {
                    Text(
                        "Подходящие продукты",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    suggestions.take(6).forEach { product ->
                        val selected = selectedProduct == product.name
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    name = product.name
                                    category = product.category
                                    icon = product.icon
                                    expiryDays = product.defaultShelfLifeDays.toString()
                                    selectedProduct = product.name
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (selected)
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary
                                )
                            else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(product.icon, style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${product.category} · примерно ${product.defaultShelfLifeDays} дн.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else if (name.isNotBlank()) {
                    Text(
                        "Не нашли в каталоге? Можно добавить «$name» как свой продукт.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                Text("Количество", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val current = parsedQuantity ?: 1f
                            quantity = (current - 1f).coerceAtLeast(0.1f)
                                .let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }
                        },
                        modifier = Modifier.size(52.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("−", style = MaterialTheme.typography.titleLarge) }

                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { value ->
                            if (value.matches(Regex("^\\d*([.,]\\d*)?$"))) quantity = value
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Шт. или кг") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    OutlinedButton(
                        onClick = {
                            val next = (parsedQuantity ?: 0f) + 1f
                            quantity = if (next % 1f == 0f) next.toInt().toString() else next.toString()
                        },
                        modifier = Modifier.size(52.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }

                Text("Срок годности", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            expiryDays = ((parsedDays ?: 7) - 1).coerceAtLeast(0).toString()
                        },
                        modifier = Modifier.size(52.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("−", style = MaterialTheme.typography.titleLarge) }

                    OutlinedTextField(
                        value = expiryDays,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit)) expiryDays = value
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Дней от сегодня") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedButton(
                        onClick = { expiryDays = ((parsedDays ?: 7) + 1).toString() },
                        modifier = Modifier.size(52.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = exact, onCheckedChange = { exact = it })
                    Text(
                        "Срок указан точно",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, parsedDays ?: return@Button)
                    onAdd(
                        name.trim(),
                        category,
                        icon,
                        parsedQuantity ?: return@Button,
                        calendar.timeInMillis,
                        exact
                    )
                },
                enabled = canAdd
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
