package com.example.mqttlistener

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Broker::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun brokerDao(): BrokerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mqtt_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}