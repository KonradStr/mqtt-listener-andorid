package com.example.mqttlistener

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mqttlistener.ui.theme.MQTTListenerTheme
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MQTTListenerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrokerListScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerListScreen() {
    val context = LocalContext.current
    val brokerDao = remember { AppDatabase.getDatabase(context).brokerDao() }
    val brokers by brokerDao.getAllBrokers().collectAsState(initial = emptyList())
    var showAddBrokerDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(
                    context,
                    "Zezwolenie na powiadomienia przyznane.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, "Zezwolenie na powiadomienia odrzucone.", Toast.LENGTH_LONG)
                    .show()
            }
        }

    LaunchedEffect(brokers) {
        brokers.forEach {
                broker -> startMqttService(context, broker)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Moi Brokerzy MQTT") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                showAddBrokerDialog = true
            }) {
                Icon(Icons.Filled.Add, "Dodaj nowego brokera")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(8.dp)
        ) {
            items(brokers) { broker ->
                BrokerListItem(broker = broker) { brokerToDelete ->
                    coroutineScope.launch {
                        brokerDao.delete(brokerToDelete)
                        stopMqttService(context, brokerToDelete.id)
                        Toast.makeText(
                            context,
                            "Broker usuniety: ${brokerToDelete.address}:${brokerToDelete.port}/${brokerToDelete.topic}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            if (brokers.isEmpty()) {
                item {
                    Text(
                        "Brak brokerów. Kliknij '+', aby dodać!",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        if (showAddBrokerDialog) {
            AddBrokerDialog(
                onDismiss = { showAddBrokerDialog = false },
                onAddBroker = { address, port, topic ->
                    coroutineScope.launch {
                        val newBroker =
                            Broker(UUID.randomUUID().toString(), address, port, topic)
                        brokerDao.insert(newBroker)
                        showAddBrokerDialog = false
                        startMqttService(context, newBroker)
                        Toast.makeText(
                            context,
                            "Broker dodany: ${address}:${port}/${topic}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }
}

fun startMqttService(context: Context, broker: Broker) {
    val serviceIntent = Intent(context, MqttService::class.java).apply {
        action = MqttService.ACTION_CONNECT_BROKER
        putExtra("BROKER_ID", broker.id)
        putExtra("BROKER_ADDRESS", broker.address)
        putExtra("BROKER_PORT", broker.port)
        putExtra("BROKER_TOPIC", broker.topic)
    }
    context.startService(serviceIntent)
}

fun stopMqttService(context: Context, brokerId: String) {
    val serviceIntent = Intent(context, MqttService::class.java).apply {
        action = "com.example.mqttlistener.ACTION_DISCONNECT_BROKER"
        putExtra("BROKER_ID_TO_DISCONNECT", brokerId)
    }
    context.startService(serviceIntent)
}

@Composable
fun BrokerListItem(broker: Broker, onDeleteClick: (Broker) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${broker.address}:${broker.port}",
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = "Topic: ${broker.topic}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { onDeleteClick(broker) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Usuń broker",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddBrokerDialog(onDismiss: () -> Unit, onAddBroker: (String, Int, String) -> Unit) {
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("1883") }
    var topic by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Dodaj nowego brokera MQTT",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adres brokera") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = port,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            port = newValue
                        }
                    },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Anuluj")
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (address.isNotBlank() && port.isNotBlank() && topic.isNotBlank()) {
                                try {
                                    val portInt = port.toInt()
                                    onAddBroker(address, portInt, topic)
                                } catch (e: NumberFormatException) {
                                    Toast.makeText(
                                        context,
                                        "Nieprawidłowy numer portu",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Pola nie mogą być puste",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Text("Dodaj")
                    }
                }
            }
        }
    }
}
