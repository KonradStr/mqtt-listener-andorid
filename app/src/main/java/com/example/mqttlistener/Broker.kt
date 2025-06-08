package com.example.mqttlistener

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "brokers")
data class Broker (
    @PrimaryKey val id: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "port") val port: Int = 1883,
    @ColumnInfo(name = "topic") val topic: String
)