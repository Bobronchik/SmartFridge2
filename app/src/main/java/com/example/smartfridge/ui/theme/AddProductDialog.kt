package com.example.smartfridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartfridge.data.ProductCatalogEntity
import java.util.Calendar

@Composable
fun AddProductDialog(
    suggestions: List<ProductCatalogEntity>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (
        String,
        String,
        String,
        Float,
        Long,
        Boolean
    ) -> Unit
) {

    var name by remember {
        mutableStateOf("")
    }

    var quantity by remember {
        mutableStateOf("1")
    }

    var category by remember {
        mutableStateOf("Другое")
    }

    var icon by remember {
        mutableStateOf("📦")
    }

    var expiryDays by remember {
        mutableStateOf("7")
    }

    var exact by remember {
        mutableStateOf(false)
    }

    AlertDialog(

        onDismissRequest =
            onDismiss,

        title = {
            Text(
                "Добавить продукт"
            )
        },

        text = {

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                OutlinedTextField(

                    value = name,

                    onValueChange = {
                        name = it
                        onSearch(it)
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    singleLine = true,

                    label = {
                        Text("Продукт")
                    }
                )

                suggestions
                    .take(4)
                    .forEach { product ->

                        TextButton(

                            onClick = {

                                name =
                                    product.name

                                category =
                                    product.category

                                icon =
                                    product.icon

                                expiryDays =
                                    product
                                        .defaultShelfLifeDays
                                        .toString()
                            },

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                "${product.icon} ${product.name}"
                            )
                        }
                    }

                OutlinedTextField(

                    value = quantity,

                    onValueChange = {
                        quantity = it
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    singleLine = true,

                    label = {
                        Text("Количество")
                    }
                )

                OutlinedTextField(

                    value = expiryDays,

                    onValueChange = {
                        expiryDays = it
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    singleLine = true,

                    label = {
                        Text(
                            "Срок в днях"
                        )
                    }
                )

                Row {

                    Checkbox(
                        checked = exact,
                        onCheckedChange = {
                            exact = it
                        }
                    )

                    Text(
                        text =
                            "Дата указана точно"
                    )
                }
            }
        },

        confirmButton = {

            Button(

                onClick = {

                    val days =
                        expiryDays
                            .toIntOrNull()
                            ?: 7

                    val calendar =
                        Calendar.getInstance()

                    calendar.add(
                        Calendar.DAY_OF_YEAR,
                        days
                    )

                    onAdd(

                        name.ifBlank {
                            "Новый продукт"
                        },

                        category,

                        icon,

                        quantity
                            .toFloatOrNull()
                            ?: 1f,

                        calendar.timeInMillis,

                        exact
                    )
                },

                enabled =
                    name.isNotBlank()
            ) {

                Text(
                    "Добавить"
                )
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text(
                    "Отмена"
                )
            }
        }
    )
}