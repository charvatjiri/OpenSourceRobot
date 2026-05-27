package com.gabotapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity(), SerialInterface.SerialListener, BluetoothServerManager.Listener {

    companion object {
        val MAJOR_VER = BuildConfig.MAJOR_VER
        val MINOR_VER = BuildConfig.MINOR_VER
        val MICRO_VER = BuildConfig.MICRO_VER
        private const val SERIAL_COMMAND_TERMINATOR = "\n"
        private const val HIGH_LEVEL_COMMAND_PREFIX = "hl:"
    }

    private var serialManager: SerialInterface? = null
    private lateinit var bluetoothServerManager: BluetoothServerManager

    private var availableDevices by mutableStateOf<List<SerialInterface.DeviceInfo>>(emptyList())
    private var selectedDeviceIndex by mutableStateOf(0)
    private var commandText by mutableStateOf("version")
    private var serialConnected by mutableStateOf(false)
    private var isConnecting by mutableStateOf(false)
    private var statusText by mutableStateOf("Disconnected")
    private val logMessages = mutableStateListOf<String>()
    private val serialReceiveBuffer = StringBuilder()

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ensureBluetoothServerRunning()
        } else {
            addLog("Bluetooth permission denied")
        }
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bluetoothServerManager.isBluetoothEnabled()) {
            ensureBluetoothServerRunning()
        } else {
            addLog("Bluetooth enable request was declined")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initSerialManager()
        initBluetoothServer()

        enableEdgeToEdge()
        setContent {
            GabotAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ServerScreen(
                        devices = availableDevices,
                        selectedDeviceIndex = selectedDeviceIndex,
                        serialConnected = serialConnected,
                        isConnecting = isConnecting,
                        statusText = statusText,
                        commandText = commandText,
                        logMessages = logMessages,
                        onRefresh = ::refreshDevices,
                        onSelectDevice = { selectedDeviceIndex = it },
                        onConnect = ::connect,
                        onDisconnect = ::disconnect,
                        onCommandChange = { commandText = it },
                        onSend = ::sendMessage,
                        onClearLog = { logMessages.clear() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        handleIntent(intent)
    }

    private fun initSerialManager() {
        serialManager?.destroy()
        serialManager = SerialManager(this)
        serialManager?.listener = this
        refreshDevices()
    }

    private fun initBluetoothServer() {
        bluetoothServerManager = BluetoothServerManager(this)
        bluetoothServerManager.listener = this
        ensureBluetoothServerRunning()
    }

    private fun ensureBluetoothServerRunning() {
        if (!bluetoothServerManager.isBluetoothSupported()) {
            addLog("Bluetooth is not supported on this device")
            return
        }

        if (!hasBluetoothPermission()) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }

        if (!bluetoothServerManager.isBluetoothEnabled()) {
            addLog("Bluetooth is disabled, requesting enable")
            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        bluetoothServerManager.start()
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshDevices() {
        availableDevices = serialManager?.findDevices() ?: emptyList()
        if (selectedDeviceIndex >= availableDevices.size) {
            selectedDeviceIndex = 0
        }

        if (availableDevices.isEmpty()) {
            addLog("No devices found")
        } else {
            addLog("Found ${availableDevices.size} device(s)")
        }
    }

    private fun connect() {
        if (selectedDeviceIndex < 0 || selectedDeviceIndex >= availableDevices.size) {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show()
            return
        }

        addLog("Connecting at ${SerialManager.BAUD_RATE} baud...")
        isConnecting = true
        statusText = "Connecting"
        serialManager?.connect(selectedDeviceIndex)
    }

    private fun disconnect() {
        isConnecting = false
        serialManager?.disconnect()
        addLog("Disconnected")
    }

    private fun sendMessage() {
        val message = commandText
        if (message.isBlank()) {
            Toast.makeText(this, "Enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        sendSerialCommand(message, source = "UI")
    }

    private fun addLog(message: String) {
        runOnUiThread {
            logMessages.add(message)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
            addLog("USB device attached")
            refreshDevices()
        }
    }

    override fun onDataReceived(data: String) {
        addLog("RX: $data")
        forwardSerialDataToBluetooth(data)
    }

    override fun onConnectionStateChanged(connected: Boolean) {
        isConnecting = false
        serialConnected = connected
        statusText = if (connected) "Connected" else "Disconnected"
        if (!connected) {
            serialReceiveBuffer.setLength(0)
        }
        sendBluetoothInfo(if (connected) "serial connected" else "serial disconnected")
        addLog(if (connected) "Connected successfully" else "Connection closed")
    }

    override fun onError(message: String) {
        isConnecting = false
        serialConnected = serialManager?.isConnected == true
        statusText = if (serialConnected) "Connected" else "Disconnected"
        addLog("Error: $message")
        if (!message.startsWith("Bluetooth ")) {
            bluetoothServerManager.sendLine("ERR: $message")
        }
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        bluetoothServerManager.stop()
        serialManager?.destroy()
        super.onDestroy()
    }

    override fun onServerStarted() {
        addLog("Bluetooth server listening: ${BluetoothServerManager.SERVICE_NAME}")
    }

    override fun onClientConnected(name: String) {
        addLog("Bluetooth client connected: $name")
        val serialState = if (serialManager?.isConnected == true) "connected" else "disconnected"
        bluetoothServerManager.sendLine("INFO: bluetooth client connected")
        bluetoothServerManager.sendLine("INFO: serial $serialState")
    }

    override fun onClientDisconnected() {
        addLog("Bluetooth client disconnected")
    }

    override fun onMessageReceived(message: String) {
        addLog("BT RX: $message")
        handleBluetoothMessage(message)
    }

    private fun handleBluetoothMessage(message: String) {
        val normalizedMessage = message.trimEnd('\r', '\n')
        if (normalizedMessage.startsWith(HIGH_LEVEL_COMMAND_PREFIX)) {
            handleHighLevelCommand(normalizedMessage)
        } else {
            sendSerialCommand(normalizedMessage, source = "BT", notifyBluetoothOnError = true)
        }
    }

    private fun handleHighLevelCommand(command: String) {
        addLog("BT high-level command: $command")
        bluetoothServerManager.sendLine("ERR: high-level commands are not implemented yet")
    }

    private fun sendSerialCommand(
        command: String,
        source: String,
        notifyBluetoothOnError: Boolean = false
    ): Boolean {
        val serialCommand = command.trimEnd('\r', '\n')
        if (serialCommand.isBlank()) {
            addLog("$source message ignored, command is empty")
            if (notifyBluetoothOnError) {
                bluetoothServerManager.sendLine("ERR: empty command")
            }
            return false
        }

        val manager = serialManager
        if (manager == null || !manager.isConnected) {
            addLog("$source message ignored, serial is disconnected")
            if (notifyBluetoothOnError) {
                bluetoothServerManager.sendLine("ERR: serial disconnected")
            }
            return false
        }

        manager.send(serialCommand + SERIAL_COMMAND_TERMINATOR)
        addLog("$source->Serial: $serialCommand")
        return true
    }

    private fun forwardSerialDataToBluetooth(data: String) {
        if (!bluetoothServerManager.hasClientConnection()) {
            return
        }

        serialReceiveBuffer.append(data)
        var newlineIndex = serialReceiveBuffer.indexOf("\n")
        while (newlineIndex >= 0) {
            val line = serialReceiveBuffer.substring(0, newlineIndex).trimEnd('\r')
            serialReceiveBuffer.delete(0, newlineIndex + 1)
            bluetoothServerManager.sendLine(line)
            addLog("Serial->BT: $line")
            newlineIndex = serialReceiveBuffer.indexOf("\n")
        }
    }

    private fun sendBluetoothInfo(message: String) {
        bluetoothServerManager.sendLine("INFO: $message")
    }
}

@Composable
private fun ServerScreen(
    devices: List<SerialInterface.DeviceInfo>,
    selectedDeviceIndex: Int,
    serialConnected: Boolean,
    isConnecting: Boolean,
    statusText: String,
    commandText: String,
    logMessages: List<String>,
    onRefresh: () -> Unit,
    onSelectDevice: (Int) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCommandChange: (String) -> Unit,
    onSend: () -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxLogHeight = LocalConfiguration.current.screenHeightDp.dp / 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("GabotApp", style = MaterialTheme.typography.headlineMedium)
            Text(
                "v${MainActivity.MAJOR_VER}.${MainActivity.MINOR_VER}.${MainActivity.MICRO_VER}",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text("BT 4.2+ classic RFCOMM serial-command server", style = MaterialTheme.typography.bodyMedium)
        Text("Status: $statusText", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh, enabled = !serialConnected && !isConnecting) {
                Text("Refresh USB devices")
            }
            if (serialConnected) {
                Button(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = devices.isNotEmpty() && !isConnecting
                ) {
                    Text(if (isConnecting) "Connecting" else "Connect")
                }
            }
        }

        Text("USB serial devices", style = MaterialTheme.typography.titleMedium)
        if (devices.isEmpty()) {
            Text("No USB serial devices found. Connect Arduino over USB OTG and refresh.")
        } else {
            devices.forEachIndexed { index, device ->
                DeviceCard(
                    device = device,
                    selected = index == selectedDeviceIndex,
                    enabled = !serialConnected && !isConnecting,
                    onClick = { onSelectDevice(index) }
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
            enabled = serialConnected
        )
        Button(
            onClick = onSend,
            enabled = serialConnected && commandText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send")
        }

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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxLogHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (logMessages.isEmpty()) {
                    Text("No log entries", style = MaterialTheme.typography.bodyMedium)
                } else {
                    logMessages.takeLast(80).forEach { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: SerialInterface.DeviceInfo,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(device.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GabotAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
