package com.example.smartfridge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_catalog"
)
data class ProductCatalogEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val category: String,

    val defaultShelfLifeDays: Int,

    val icon: String = "📦",

    val isCustom: Boolean = false
)