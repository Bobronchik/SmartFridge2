package com.example.smartfridge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "fridge_items"
)
data class FridgeItemEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val productId: Long?,

    val name: String,

    val category: String,

    val icon: String,

    val quantity: Float,

    val addedAt: Long,

    val expiresAt: Long,

    /*
     * true  = дата найдена на упаковке
     * false = дата рассчитана приблизительно
     */
    val isExactDate: Boolean,

    val brand: String? = null
)