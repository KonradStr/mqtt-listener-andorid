package com.example.mqttlistener

data class Broker (
    val id: String,
    val address: String,
    val port: Int = 1883,
    val topic: String
)