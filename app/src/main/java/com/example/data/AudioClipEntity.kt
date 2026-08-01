package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_clips")
data class AudioClipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val text: String,
    val filePath: String,
    val durationMs: Long,
    val dateAdded: Long = System.currentTimeMillis(),
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val voiceName: String
)
