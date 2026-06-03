package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Query("SELECT * FROM step_entries ORDER BY date DESC")
    fun getAllEntriesFlow(): Flow<List<StepEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: StepEntry)

    @Delete
    suspend fun delete(entry: StepEntry)

    @Query("DELETE FROM step_entries WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("SELECT * FROM step_entries WHERE date = :date LIMIT 1")
    suspend fun getEntryByDate(date: String): StepEntry?
}
