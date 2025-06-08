package com.example.mqttlistener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.ConcurrentHashMap

class MqttService : Service() {

    private val TAG = "MqttService"
    private val NOTIFICATION_CHANNEL_ID = "mqtt_notifications"
    private val NOTIFICATION_CHANNEL_NAME = "MQTT Messages"

    private val mqttClients = ConcurrentHashMap<String, MqttAsyncClient>()

    companion object {
        const val ACTION_DISCONNECT_BROKER = "com.example.mqttlistener.ACTION_DISCONNECT_BROKER"
        const val ACTION_CONNECT_BROKER = "com.example.mqttlistener.ACTION_CONNECT_BROKER"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Service will not communicate with UI components
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MqttService onStartCommand - Action: ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECT_BROKER -> {
                val brokerId = intent.getStringExtra("BROKER_ID")
                val brokerAddress = intent.getStringExtra("BROKER_ADDRESS")
                val brokerPort = intent.getIntExtra("BROKER_PORT", 1883)
                val brokerTopic = intent.getStringExtra("BROKER_TOPIC")

                if (brokerId != null && brokerAddress != null && brokerTopic != null) {
                    connectAndSubscribe(brokerId, brokerAddress, brokerPort, brokerTopic)
                } else {
                    Log.w(TAG, "Missing broker details for connection (ACTION_CONNECT_BROKER).")
                }
            }

            ACTION_DISCONNECT_BROKER -> {
                val brokerIdToDisconnect = intent.getStringExtra("BROKER_ID_TO_DISCONNECT")
                if (brokerIdToDisconnect != null) {
                    disconnectSpecificBroker(brokerIdToDisconnect)
                } else {
                    Log.w(TAG, "Missing broker ID for disconnection (ACTION_DISCONNECT_BROKER).")
                }
            }

            else -> {
                Log.d(TAG, "MqttService received unknown or null action: ${intent?.action}")
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MqttService destroyed. Disconnecting all clients.")

        mqttClients.values.forEach { client ->
            try {
                if (client.isConnected) {
                    client.disconnect()
                    Log.d(TAG, "Disconnected MQTT client: ${client.serverURI}")
                }
            } catch (e: MqttException) {
                Log.e(
                    TAG,
                    "Error disconnecting MQTT client during onDestroy: ${client.serverURI}",
                    e
                )
            }
        }

        mqttClients.clear()
    }

    private fun disconnectSpecificBroker(brokerId: String) {
        val client = mqttClients.remove(brokerId)

        if (client != null) {
            try {
                if (client.isConnected) {
                    client.disconnect(null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.d(
                                TAG,
                                "MQTT Client disconnected successfully for broker: $brokerId"
                            )
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?
                        ) {
                            Log.e(
                                TAG,
                                "MQTT Client disconnect failed for broker: $brokerId: ${exception?.message}"
                            )
                        }
                    })
                }
                client.close()

            } catch (e: MqttException) {
                Log.e(TAG, "Error disconnecting MQTT client for broker $brokerId: ${e.message}", e)
            }

        } else {
            Log.d(TAG, "No active MQTT client found for broker ID: $brokerId to disconnect.")
        }
    }

    private fun connectAndSubscribe(id: String, address: String, port: Int, topic: String) {
        val serverUri = "tcp://$address:$port"
        val clientId = MqttClient.generateClientId()
        val persistence = MemoryPersistence()

        if (mqttClients.containsKey(id) && mqttClients[id]?.isConnected == true) {
            Log.d(
                TAG,
                "Client for broker $id already connected to $serverUri. Updating subscription if needed."
            )

            mqttClients[id]?.let { client ->
                try {
                    client.subscribe(topic, 0)
                    Log.d(TAG, "Resubscribed to topic '$topic' for broker $id")
                } catch (e: MqttException) {
                    Log.e(TAG, "Error resubscribing to topic '$topic' for broker $id", e)
                }
            }

            return
        }

        try {
            val mqttClient = MqttAsyncClient(serverUri, clientId, persistence)
            mqttClients[id] = mqttClient

            val options = MqttConnectOptions()
            options.isCleanSession = true

            mqttClient.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d(
                        TAG,
                        "MQTT Connected to $serverURI (reconnect: $reconnect) for broker $id"
                    )

                    try {
                        mqttClient.subscribe(topic, 0)
                        Log.d(TAG, "Subscribed to topic '$topic' for broker $id")
                    } catch (e: MqttException) {
                        Log.e(TAG, "Error subscribing to topic '$topic' for broker $id", e)
                    }
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.e(TAG, "MQTT Connection lost for broker $id: ${cause?.message}", cause)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = String(message?.payload ?: ByteArray(0))
                    Log.d(TAG, "Message arrived from topic '$topic' for broker $id: $payload")
                    sendNotification("MQTT from $topic", payload, id.hashCode())
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "MQTT Connection successful for broker $id: $serverUri")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "MQTT Connection failed for broker $id: $serverUri", exception)
                    Toast.makeText(
                        applicationContext,
                        "Połączenie MQTT nieudane dla $address:$port",
                        Toast.LENGTH_LONG
                    ).show()
                    mqttClients.remove(id)
                }
            })

        } catch (e: MqttException) {
            Log.e(TAG, "Error creating MQTT client for $address:$port", e)
            mqttClients.remove(id)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Powiadomienia o wiadomościach MQTT"
        }

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun sendNotification(title: String, message: String, notificationId: Int) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}