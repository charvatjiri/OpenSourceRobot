package com.example.gabot_client

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gabot_client.ui.theme.GabotClientTheme

class MainActivity : ComponentActivity(), GabotBluetoothClient.Listener {

    companion object {
        private const val TAG = "GabotClient"
    }

    private lateinit var bluetoothClient: GabotBluetoothClient

    private var devices by mutableStateOf<List<GabotBluetoothClient.DeviceInfo>>(emptyList())
    private var selectedDevice by mutableStateOf<GabotBluetoothClient.DeviceInfo?>(null)
    private var commandText by mutableStateOf("version")
    private var connected by mutableStateOf(false)
    private var statusText by mutableStateOf("Disconnected")
    private val logMessages = mutableStateListOf<String>()

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (hasBluetoothPermission()) {
            refreshDevices()
        } else {
            val denied = grants.filterValues { !it }.keys.joinToString()
            addLog("Bluetooth permission denied: $denied")
        }
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bluetoothClient.isBluetoothEnabled()) {
            refreshDevices()
        } else {
            addLog("Bluetooth enable request was declined")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothClient = GabotBluetoothClient(this)
        bluetoothClient.listener = this

        enableEdgeToEdge()
        setContent {
            GabotClientTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ClientScreen(
                        devices = devices,
                        selectedDevice = selectedDevice,
                        connected = connected,
                        statusText = statusText,
                        commandText = commandText,
                        logMessages = logMessages,
                        onRefresh = ::refreshDevices,
                        onSelectDevice = { selectedDevice = it },
                        onConnect = ::connect,
                        onDisconnect = ::disconnect,
                        onCommandChange = { commandText = it },
                        onSend = ::sendCommand,
                        onClearLog = { logMessages.clear() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        refreshDevices()
    }

    private fun refreshDevices() {
        if (!bluetoothClient.isBluetoothSupported()) {
            statusText = "Bluetooth unsupported"
            addLog("Bluetooth is not supported on this device")
            return
        }

        if (!hasBluetoothPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermissionLauncher.launch(requiredBluetoothPermissions())
            }
            return
        }

        if (!bluetoothClient.isBluetoothEnabled()) {
            statusText = "Bluetooth disabled"
            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        devices = bluetoothClient.listBondedDevices()
        selectedDevice = selectedDevice?.let { current ->
            devices.firstOrNull { it.address == current.address }
        } ?: devices.firstOrNull()
        addLog("Found ${devices.size} paired Bluetooth device(s)")
    }

    private fun hasBluetoothPermission(): Boolean {
        return requiredBluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }
    }

    private fun connect() {
        val device = selectedDevice
        if (device == null) {
            addLog("No paired Bluetooth device selected")
            return
        }

        statusText = "Connecting to ${device.displayName}"
        addLog("Connecting: ${device.displayName}")
        bluetoothClient.connect(device.address)
    }

    private fun disconnect() {
        bluetoothClient.disconnect()
    }

    private fun sendCommand() {
        val command = commandText.trimEnd('\r', '\n')
        if (bluetoothClient.sendLine(command)) {
            Log.d(TAG, "BT TX command='$command'")
            addLog("TX: $command")
        }
    }

    private fun addLog(message: String) {
        Log.d(TAG, "UI LOG: $message")
        runOnUiThread {
            logMessages.add(message)
        }
    }

    override fun onConnectionStateChanged(connected: Boolean) {
        runOnUiThread {
            this.connected = connected
            statusText = if (connected) "Connected" else "Disconnected"
            addLog(statusText)
        }
    }

    override fun onLineReceived(line: String) {
        val normalizedLine = line.trim()
        Log.d(TAG, "BT RX raw='$line', normalized='$normalizedLine'")
        if (normalizedLine.isNotBlank()) {
            addLog("RX: $normalizedLine")
        }
    }

    override fun onError(message: String) {
        addLog("ERR: $message")
    }

    override fun onDestroy() {
        bluetoothClient.destroy()
        super.onDestroy()
    }

}

@Composable
private fun ClientScreen(
    devices: List<GabotBluetoothClient.DeviceInfo>,
    selectedDevice: GabotBluetoothClient.DeviceInfo?,
    connected: Boolean,
    statusText: String,
    commandText: String,
    logMessages: List<String>,
    onRefresh: () -> Unit,
    onSelectDevice: (GabotBluetoothClient.DeviceInfo) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCommandChange: (String) -> Unit,
    onSend: () -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxLogHeight = LocalConfiguration.current.screenHeightDp.dp / 2
    val contentScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(contentScrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GabotClient", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "v${BuildConfig.MAJOR_VER}.${BuildConfig.MINOR_VER}.${BuildConfig.MICRO_VER}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text("BT 4.2+ classic RFCOMM serial-command client", style = MaterialTheme.typography.bodyMedium)
            Text("Status: $statusText", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, enabled = !connected) {
                    Text("Refresh paired devices")
                }
                if (connected) {
                    Button(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                } else {
                    Button(onClick = onConnect, enabled = selectedDevice != null) {
                        Text("Connect")
                    }
                }
            }

            Text("Paired devices", style = MaterialTheme.typography.titleMedium)
            if (devices.isEmpty()) {
                Text("No paired devices. Pair this phone with the GabotApp phone in Android Bluetooth settings first.")
            } else {
                devices.forEach { device ->
                    DeviceCard(
                        device = device,
                        selected = device.address == selectedDevice?.address,
                        enabled = !connected,
                        onClick = { onSelectDevice(device) }
                    )
                }
            }

            OutlinedTextField(
                value = commandText,
                onValueChange = onCommandChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Serial command") },
                placeholder = { Text("version") },
                singleLine = true,
                enabled = connected
            )
            Button(
                onClick = onSend,
                enabled = connected && commandText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send command")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Log", style = MaterialTheme.typography.titleMedium)
                TextButton(
                    onClick = onClearLog,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Clear")
                }
            }
            LogView(
                logMessages = logMessages,
                modifier = Modifier.heightIn(max = maxLogHeight)
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: GabotBluetoothClient.DeviceInfo,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
                if (selected) {
                    Text("Selected", color = MaterialTheme.colorScheme.primary)
                }
            }
            Button(onClick = onClick, enabled = enabled && !selected) {
                Text(if (selected) "Selected" else "Select")
            }
        }
    }
}

@Composable
private fun LogView(logMessages: List<String>, modifier: Modifier = Modifier) {
    val logScrollState = rememberScrollState()

    LaunchedEffect(logMessages.size, logScrollState.maxValue) {
        logScrollState.animateScrollTo(logScrollState.maxValue)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(logScrollState)
                .padding(12.dp)
        ) {
            if (logMessages.isEmpty()) {
                Text("No log messages")
            } else {
                logMessages.takeLast(80).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}
