package com.example.smartfridge.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query(
        """
        SELECT * FROM product_catalog
        ORDER BY name ASC
        """
    )
    fun observeAll(): Flow<List<ProductCatalogEntity>>

    @Query(
        """
        SELECT * FROM product_catalog
        WHERE instr(LOWER(name), LOWER(:query)) > 0
        ORDER BY name ASC
        """
    )
    suspend fun search(
        query: String
    ): List<ProductCatalogEntity>

    @Query(
        """
        SELECT * FROM product_catalog
        WHERE id = :id
        LIMIT 1
        """
    )
    suspend fun getById(
        id: Long
    ): ProductCatalogEntity?

    @Query(
        """
        SELECT * FROM product_catalog
        WHERE LOWER(name) = LOWER(:name)
        LIMIT 1
        """
    )
    suspend fun findByName(
        name: String
    ): ProductCatalogEntity?

    @Insert
    suspend fun insert(
        product: ProductCatalogEntity
    ): Long

    @Insert
    suspend fun insertAll(
        products: List<ProductCatalogEntity>
    )

    @Query("DELETE FROM product_catalog WHERE isCustom = 0 AND name NOT IN (:allowedNames)")
    suspend fun deleteNonStandardDefaults(allowedNames: List<String>)

    @Query(
        """
        SELECT COUNT(*) FROM product_catalog
        """
    )
    suspend fun count(): Int
}