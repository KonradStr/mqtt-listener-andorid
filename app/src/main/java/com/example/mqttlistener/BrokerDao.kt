package com.example.mqttlistener

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrokerDao {
    @Query("SELECT * FROM brokers ORDER BY address ASC")
    fun getAllBrokers(): Flow<List<Broker>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(broker: Broker)

    @Delete
    suspend fun delete(broker: Broker)

    @Query("DELETE FROM brokers WHERE id = :brokerId")
    suspend fun deleteById(brokerId: String)
}