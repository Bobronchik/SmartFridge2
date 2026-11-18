package com.example.smartfridge.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FridgeDao {

    @Query(
        """
        SELECT * FROM fridge_items
        ORDER BY expiresAt ASC
        """
    )
    fun observeAll(): Flow<List<FridgeItemEntity>>

    @Query("SELECT * FROM fridge_items WHERE expiresAt > :now AND expiresAt <= :limit ORDER BY expiresAt ASC")
    suspend fun getExpiringBetween(now: Long, limit: Long): List<FridgeItemEntity>

    @Query("SELECT * FROM fridge_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FridgeItemEntity?

    @Insert
    suspend fun insert(
        item: FridgeItemEntity
    ): Long

    @Insert
    suspend fun insertAll(
        items: List<FridgeItemEntity>
    )

    @Update
    suspend fun update(
        item: FridgeItemEntity
    )

    @Delete
    suspend fun delete(
        item: FridgeItemEntity
    )

    @Query(
        """
        DELETE FROM fridge_items
        """
    )
    suspend fun deleteAll()
}