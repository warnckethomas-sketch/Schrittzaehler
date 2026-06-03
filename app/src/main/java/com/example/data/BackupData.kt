package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupData(
    val stepLengthCm: Int,
    val entries: List<StepEntry>
)
