package com.minhasofertas.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels ORDER BY addedAt ASC")
    fun getAllChannelsFlow(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE enabled = 1")
    suspend fun getEnabledChannels(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET enabled = :enabled WHERE username = :username")
    suspend fun setChannelEnabled(username: String, enabled: Boolean)

    @Query("DELETE FROM channels WHERE username = :username")
    suspend fun deleteChannel(username: String)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun getCount(): Int

    @Query("DELETE FROM channels")
    suspend fun clearAll()
}
