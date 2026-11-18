package com.example.smartfridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfridge.domain.Freshness
import com.example.smartfridge.domain.FridgeProduct
import com.example.smartfridge.domain.getFreshness
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FridgeScreen(
    state: FridgeUiState,
    onSearch: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onScan: () -> Unit,
    onAdd: () -> Unit
) {

    val categories =
        state.products
            .map { it.category }
            .distinct()

    Scaffold(

        floatingActionButton = {

            FloatingActionButton(
                onClick = onAdd
            ) {

                Text(
                    text = "+",
                    fontSize = 28.sp
                )
            }
        }

    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            Text(
                text = "SmartFridge",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
            )

            Text(
                text = "Ваш холодильник",
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                Modifier.height(16.dp)
            )

            if (
                state.message != null
            ) {

                AssistChip(

                    onClick = {},

                    label = {
                        Text(
                            state.message
                        )
                    }
                )

                Spacer(
                    Modifier.height(12.dp)
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            "${state.products.size} продуктов",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    if (
                        state.cameraConnected
                    ) {

                        Text(
                            text =
                                "Камера готова",

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }

                Button(
                    onClick = onScan,
                    enabled =
                        !state.scanning
                ) {

                    Text(
                        if (state.scanning)
                            "Проверяем…"
                        else
                            "📷 Обновить"
                    )
                }
            }

            Spacer(
                Modifier.height(16.dp)
            )

            OutlinedTextField(

                value = state.search,

                onValueChange = onSearch,

                modifier =
                    Modifier.fillMaxWidth(),

                singleLine = true,

                label = {
                    Text(
                        "Поиск продуктов"
                    )
                }
            )

            Spacer(
                Modifier.height(12.dp)
            )

            LazyRow(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                item {

                    FilterChip(
                        selected =
                            state.category == null,

                        onClick = {
                            onCategory(null)
                        },

                        label = {
                            Text("Все")
                        }
                    )
                }

                items(categories) {
                        category ->

                    FilterChip(
                        selected =
                            state.category ==
                                    category,

                        onClick = {
                            onCategory(category)
                        },

                        label = {
                            Text(category)
                        }
                    )
                }
            }

            Spacer(
                Modifier.height(12.dp)
            )

            LazyColumn(
                modifier =
                    Modifier.fillMaxSize(),

                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                items(
                    state.products,
                    key = {
                        it.id
                    }
                ) {

                    ProductCard(it)
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: FridgeProduct
) {

    val freshness =
        getFreshness(
            product.expiresAt
        )

    val color =
        when (freshness) {

            Freshness.FRESH ->
                MaterialTheme
                    .colorScheme
                    .primary

            Freshness.EXPIRING_SOON ->
                MaterialTheme
                    .colorScheme
                    .tertiary

            Freshness.EXPIRED ->
                MaterialTheme
                    .colorScheme
                    .error
        }

    val status =
        when (freshness) {

            Freshness.FRESH ->
                "Свежее"

            Freshness.EXPIRING_SOON ->
                "Скоро испортится"

            Freshness.EXPIRED ->
                "Просрочено"
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(18.dp)
    ) {

        Row(
            modifier =
                Modifier.padding(16.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = product.icon,
                fontSize = 34.sp
            )

            Spacer(
                Modifier.width(14.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text = product.name,

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                if (
                    product.brand != null
                ) {

                    Text(
                        text =
                            product.brand,

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }

                Text(
                    text =
                        "${product.quantity} шт.",

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,

                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )

                Spacer(
                    Modifier.height(6.dp)
                )

                Text(
                    text =
                        "$status • ${
                            SimpleDateFormat(
                                "dd.MM.yyyy",
                                Locale.getDefault()
                            ).format(
                                Date(
                                    product.expiresAt
                                )
                            )
                        }",

                    color = color,

                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )

                if (
                    !product.isExactDate
                ) {

                    Text(
                        text =
                            "Срок рассчитан примерно",

                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }
            }
        }
    }
}