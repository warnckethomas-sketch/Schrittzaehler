package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "step_entries")
data class StepEntry(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val steps: Int,
    val remark: String = ""
)
