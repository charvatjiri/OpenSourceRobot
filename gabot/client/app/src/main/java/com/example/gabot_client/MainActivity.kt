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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gabot_client.ui.theme.GabotClientTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                        onControllerCommand = ::sendDirectCommand,
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

    private fun sendDirectCommand(command: String) {
        if (bluetoothClient.sendLine(command)) {
            Log.d(TAG, "BT TX controller command='$command'")
            addLog("TX: $command")
        }
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

private const val WRIST_STEP_DEGREES = 1
private const val WRIST_STEP_INTERVAL_MS = 20L

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
    onControllerCommand: (String) -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxLogHeight = LocalConfiguration.current.screenHeightDp.dp / 2
    val contentScrollState = rememberScrollState()
    val controllerScrollState = rememberScrollState()
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    if (selectedTab == 1) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(controllerScrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClientHeader(statusText = statusText)
            TabSelector(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
            DirectController(
                enabled = connected,
                onCommand = onControllerCommand,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
        return
    }

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
            ClientHeader(statusText = statusText)
            TabSelector(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

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
private fun ClientHeader(statusText: String) {
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
}

@Composable
private fun TabSelector(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    TabRow(selectedTabIndex = selectedTab) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            text = { Text("Console") }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            text = { Text("Controller") }
        )
    }
}

@Composable
private fun DirectController(
    enabled: Boolean,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    if (!isLandscape) {
        Column(
            modifier = modifier
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            GrabReleaseControls(
                enabled = enabled,
                onCommand = onCommand,
                horizontal = true,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
            )
            WristPad(
                enabled = enabled,
                onCommand = onCommand,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
            )
            ArmPad(
                enabled = enabled,
                onCommand = onCommand,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
            )
            WheelsPad(
                enabled = enabled,
                onCommand = onCommand,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        return
    }

    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GrabReleaseControls(
            enabled = enabled,
            onCommand = onCommand,
            modifier = Modifier.width(96.dp)
        )
        WristPad(
            enabled = enabled,
            onCommand = onCommand,
            modifier = Modifier.weight(1f)
        )
        ArmPad(
            enabled = enabled,
            onCommand = onCommand,
            modifier = Modifier.weight(1f)
        )
        WheelsPad(
            enabled = enabled,
            onCommand = onCommand,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GrabReleaseControls(
    enabled: Boolean,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false
) {
    if (horizontal) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HoldCommandButton(
                label = "GRAB",
                enabled = enabled,
                pressCommand = "grab 1",
                releaseCommand = "grab 0",
                onCommand = onCommand
            )
            HoldCommandButton(
                label = "RELEASE",
                enabled = enabled,
                pressCommand = "release 1",
                releaseCommand = "release 0",
                onCommand = onCommand
            )
        }
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HoldCommandButton(
            label = "GRAB",
            enabled = enabled,
            pressCommand = "grab 1",
            releaseCommand = "grab 0",
            onCommand = onCommand
        )
        HoldCommandButton(
            label = "RELEASE",
            enabled = enabled,
            pressCommand = "release 1",
            releaseCommand = "release 0",
            onCommand = onCommand
        )
    }
}

@Composable
private fun WristPad(
    enabled: Boolean,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp > configuration.screenHeightDp
    val buttonWidth = if (compact) 60.dp else 78.dp
    val buttonHeight = if (compact) 48.dp else 52.dp
    val centerSize = if (compact) 38.dp else 46.dp
    val buttonSpacing = if (compact) 4.dp else 6.dp
    var horizontalPosition by rememberSaveable { mutableStateOf(80) }
    var verticalPosition by rememberSaveable { mutableStateOf(100) }

    fun moveHorizontal(delta: Int) {
        val nextPosition = (horizontalPosition + delta).coerceIn(10, 150)
        if (nextPosition != horizontalPosition) {
            horizontalPosition = nextPosition
            onCommand("wrist horizontal $nextPosition")
        }
    }

    fun moveVertical(delta: Int) {
        val nextPosition = (verticalPosition + delta).coerceIn(50, 150)
        if (nextPosition != verticalPosition) {
            verticalPosition = nextPosition
            onCommand("wrist vertical $nextPosition")
        }
    }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("WRIST", style = MaterialTheme.typography.titleSmall)
            RepeatingCommandButton(
                label = "UP",
                enabled = enabled,
                onStep = { moveVertical(-WRIST_STEP_DEGREES) },
                width = buttonWidth,
                height = buttonHeight
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    buttonSpacing,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RepeatingCommandButton(
                    label = "L",
                    enabled = enabled,
                    onStep = { moveHorizontal(WRIST_STEP_DEGREES) },
                    width = buttonWidth,
                    height = buttonHeight
                )
                Box(modifier = Modifier.size(centerSize), contentAlignment = Alignment.Center) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
                RepeatingCommandButton(
                    label = "R",
                    enabled = enabled,
                    onStep = { moveHorizontal(-WRIST_STEP_DEGREES) },
                    width = buttonWidth,
                    height = buttonHeight
                )
            }
            RepeatingCommandButton(
                label = "DOWN",
                enabled = enabled,
                onStep = { moveVertical(WRIST_STEP_DEGREES) },
                width = buttonWidth,
                height = buttonHeight
            )
        }
    }
}

@Composable
private fun ArmPad(
    enabled: Boolean,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ControllerPad(
        title = "ARM",
        upLabel = "UP",
        downLabel = "DOWN",
        leftLabel = "L",
        rightLabel = "R",
        enabled = enabled,
        upCommand = "shoulder vertical -50",
        downCommand = "shoulder vertical 30",
        leftCommand = "shoulder horizontal -40",
        rightCommand = "shoulder horizontal 40",
        verticalStopCommand = "shoulder vertical 0",
        horizontalStopCommand = "shoulder horizontal 0",
        onCommand = onCommand,
        modifier = modifier
    )
}

@Composable
private fun WheelsPad(
    enabled: Boolean,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ControllerPad(
        title = "WHEELS",
        upLabel = "FORWARD",
        downLabel = "BACK",
        leftLabel = "L",
        rightLabel = "R",
        enabled = enabled,
        upCommand = "wheels fb 15",
        downCommand = "wheels fb -15",
        leftCommand = "wheels rl -15",
        rightCommand = "wheels rl 15",
        verticalStopCommand = "wheels fb 0",
        horizontalStopCommand = "wheels rl 0",
        onCommand = onCommand,
        modifier = modifier
    )
}

@Composable
private fun ControllerPad(
    title: String,
    upLabel: String,
    downLabel: String,
    leftLabel: String,
    rightLabel: String,
    enabled: Boolean,
    upCommand: String,
    downCommand: String,
    leftCommand: String,
    rightCommand: String,
    verticalStopCommand: String?,
    horizontalStopCommand: String?,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp > configuration.screenHeightDp
    val buttonWidth = if (compact) 60.dp else 78.dp
    val buttonHeight = if (compact) 48.dp else 52.dp
    val centerSize = if (compact) 38.dp else 46.dp
    val buttonSpacing = if (compact) 4.dp else 6.dp

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            HoldCommandButton(
                label = upLabel,
                enabled = enabled,
                pressCommand = upCommand,
                releaseCommand = verticalStopCommand,
                onCommand = onCommand,
                width = buttonWidth,
                height = buttonHeight
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    buttonSpacing,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HoldCommandButton(
                    label = leftLabel,
                    enabled = enabled,
                    pressCommand = leftCommand,
                    releaseCommand = horizontalStopCommand,
                    onCommand = onCommand,
                    width = buttonWidth,
                    height = buttonHeight
                )
                Box(modifier = Modifier.size(centerSize), contentAlignment = Alignment.Center) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
                HoldCommandButton(
                    label = rightLabel,
                    enabled = enabled,
                    pressCommand = rightCommand,
                    releaseCommand = horizontalStopCommand,
                    onCommand = onCommand,
                    width = buttonWidth,
                    height = buttonHeight
                )
            }
            HoldCommandButton(
                label = downLabel,
                enabled = enabled,
                pressCommand = downCommand,
                releaseCommand = verticalStopCommand,
                onCommand = onCommand,
                width = buttonWidth,
                height = buttonHeight
            )
        }
    }
}

@Composable
private fun HoldCommandButton(
    label: String,
    enabled: Boolean,
    pressCommand: String,
    releaseCommand: String?,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 78.dp,
    height: androidx.compose.ui.unit.Dp = 52.dp
) {
    Surface(
        modifier = modifier
            .size(width = width, height = height)
            .pointerInput(enabled, pressCommand, releaseCommand) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) {
                            return@detectTapGestures
                        }
                        onCommand(pressCommand)
                        tryAwaitRelease()
                        releaseCommand?.let(onCommand)
                    }
                )
            },
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RepeatingCommandButton(
    label: String,
    enabled: Boolean,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 78.dp,
    height: androidx.compose.ui.unit.Dp = 52.dp
) {
    val currentOnStep by rememberUpdatedState(onStep)

    Surface(
        modifier = modifier
            .size(width = width, height = height)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) {
                            return@detectTapGestures
                        }
                        coroutineScope {
                            val movementJob = launch {
                                while (true) {
                                    currentOnStep()
                                    delay(WRIST_STEP_INTERVAL_MS)
                                }
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                movementJob.cancel()
                            }
                        }
                    }
                )
            },
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
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
