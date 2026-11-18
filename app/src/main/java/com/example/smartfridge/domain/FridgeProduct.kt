package com.example.smartfridge.domain

import com.example.smartfridge.data.FridgeItemEntity

data class FridgeProduct(
    val id: Long,
    val name: String,
    val category: String,
    val icon: String,
    val quantity: Float,
    val addedAt: Long,
    val expiresAt: Long,
    val isExactDate: Boolean,
    val brand: String?
)

fun FridgeItemEntity.toDomain(): FridgeProduct {

    return FridgeProduct(
        id = id,
        name = name,
        category = category,
        icon = icon,
        quantity = quantity,
        addedAt = addedAt,
        expiresAt = expiresAt,
        isExactDate = isExactDate,
        brand = brand
    )
}