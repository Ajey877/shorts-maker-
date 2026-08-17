package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
  @Query("SELECT * FROM saved_clips ORDER BY createdAt DESC")
  fun getAllClips(): Flow<List<ClipEntity>>

  @Query("SELECT * FROM saved_clips WHERE isPosted = 1 ORDER BY createdAt DESC")
  fun getPostedClips(): Flow<List<ClipEntity>>

  @Query("SELECT * FROM saved_clips WHERE id = :clipId LIMIT 1")
  suspend fun getClipById(clipId: String): ClipEntity?

  @Query("SELECT EXISTS(SELECT 1 FROM saved_clips WHERE id = :clipId)")
  fun isClipSavedFlow(clipId: String): Flow<Boolean>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertClip(clip: ClipEntity)

  @Query("DELETE FROM saved_clips WHERE id = :clipId")
  suspend fun deleteClipById(clipId: String)

  @Query("UPDATE saved_clips SET isPosted = :isPosted WHERE id = :clipId")
  suspend fun updatePostedStatus(clipId: String, isPosted: Boolean)
}
