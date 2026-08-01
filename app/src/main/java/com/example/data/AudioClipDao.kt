package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioClipDao {
    @Query("SELECT * FROM audio_clips ORDER BY dateAdded DESC")
    fun getAllClips(): Flow<List<AudioClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: AudioClipEntity): Long

    @Delete
    suspend fun deleteClip(clip: AudioClipEntity)

    @Query("SELECT * FROM audio_clips WHERE id = :id")
    suspend fun getClipById(id: Long): AudioClipEntity?

    @Query("DELETE FROM audio_clips")
    suspend fun clearAll()
}
