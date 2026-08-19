package com.gabot.pcclient

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.gabot.shared.ControllerCommands
import com.gabot.shared.WristPosition
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val APP_VERSION = "0.1.0"

fun main() = application {
    val appState = remember { GabotPcState() }
    DisposableEffect(Unit) {
        appState.refreshPorts()
        onDispose(appState::destroy)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "GabotPcClient $APP_VERSION",
        state = WindowState(size = DpSize(1180.dp, 760.dp))
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFFF2B84B),
                onPrimary = Color(0xFF2B210F),
                primaryContainer = Color(0xFF405F75),
                onPrimaryContainer = Color(0xFFF2F7FA),
                secondary = Color(0xFF84B9C9),
                background = Color(0xFF101A20),
                surface = Color(0xFF17252D),
                surfaceVariant = Color(0xFF263943)
            )
        ) {
            Surface(Modifier.fillMaxSize()) {
                ClientScreen(appState)
            }
        }
    }
}

private class GabotPcState : SerialBluetoothClient.Listener {
    private val client = SerialBluetoothClient().also { it.listener = this }

    val ports = mutableStateListOf<SerialBluetoothClient.PortInfo>()
    val logMessages = mutableStateListOf<String>()
    var selectedPort by mutableStateOf<SerialBluetoothClient.PortInfo?>(null)
    var connected by mutableStateOf(false)
    var statusText by mutableStateOf("Disconnected")
    var commandText by mutableStateOf("version")

    fun refreshPorts() {
        val previousName = selectedPort?.systemName
        ports.clear()
        ports.addAll(client.listPorts())
        selectedPort = ports.firstOrNull { it.systemName == previousName } ?: ports.firstOrNull()
        addLog("Found ${ports.size} serial port(s)")
    }

    fun connect() {
        val selected = selectedPort ?: run {
            addLog("ERR: No Bluetooth serial port selected")
            return
        }
        statusText = "Connecting to ${selected.systemName}"
        addLog("Connecting: ${selected.displayName}")
        client.connect(selected.systemName)
    }

    fun disconnect() = client.disconnect()

    fun sendCommand() = sendLine(commandText)

    fun sendLine(command: String) {
        if (client.sendLine(command)) {
            addLog("TX: ${command.trim()}")
        }
    }

    fun clearLog() = logMessages.clear()

    fun destroy() = client.destroy()

    override fun onConnectionStateChanged(connected: Boolean) {
        this.connected = connected
        statusText = if (connected) "Connected" else "Disconnected"
        addLog(statusText)
    }

    override fun onLineReceived(line: String) {
        if (line.isNotBlank()) addLog("RX: ${line.trim()}")
    }

    override fun onError(message: String) = addLog("ERR: $message")

    private fun addLog(message: String) {
        logMessages.add(message)
        if (logMessages.size > 1000) logMessages.removeRange(0, 100)
    }
}

@Composable
private fun ClientScreen(state: GabotPcState) {
    var selectedTab by remember { mutableStateOf(0) }
    val pageScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(pageScrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ClientHeader(state.statusText)
        TabSelector(selectedTab) { selectedTab = it }
        if (selectedTab == 0) {
            ConsoleView(state)
        } else {
            DirectController(
                enabled = state.connected,
                onCommand = state::sendLine,
                modifier = Modifier.fillMaxWidth()
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
        Column {
            Text("GabotPcClient", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Bluetooth serial controller for GabotApp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Text("v$APP_VERSION", style = MaterialTheme.typography.titleMedium)
    }
    Text("Status: $statusText", style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun TabSelector(selectedTab: Int, onTabSelected: (Int) -> Unit) {
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
private fun ConsoleView(state: GabotPcState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Pair the GabotApp phone in Windows or Linux first, then select its Bluetooth serial port.",
            color = MaterialTheme.colorScheme.secondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = state::refreshPorts, enabled = !state.connected) {
                Text("Refresh ports")
            }
            if (state.connected) {
                Button(onClick = state::disconnect) { Text("Disconnect") }
            } else {
                Button(onClick = state::connect, enabled = state.selectedPort != null) {
                    Text("Connect")
                }
            }
        }

        Text("Bluetooth serial ports", style = MaterialTheme.typography.titleMedium)
        if (state.ports.isEmpty()) {
            Text("No serial ports found. Check Bluetooth pairing and the system RFCOMM/COM port.")
        } else {
            state.ports.forEach { port ->
                PortCard(
                    port = port,
                    selected = port.systemName == state.selectedPort?.systemName,
                    enabled = !state.connected,
                    onClick = { state.selectedPort = port }
                )
            }
        }

        OutlinedTextField(
            value = state.commandText,
            onValueChange = { state.commandText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Serial command") },
            singleLine = true,
            enabled = state.connected
        )
        Button(
            onClick = state::sendCommand,
            enabled = state.connected && state.commandText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send command")
        }
        LogView(state.logMessages, state::clearLog)
    }
}

@Composable
private fun PortCard(
    port: SerialBluetoothClient.PortInfo,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Text(port.displayName, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun LogView(messages: List<String>, onClear: () -> Unit) {
    val scrollState = rememberScrollState()
    LaunchedEffect(messages.size) { scrollState.scrollTo(scrollState.maxValue) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Log", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text("Clear")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp, max = 320.dp)
            .background(Color(0xFF081116), RoundedCornerShape(10.dp))
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        messages.forEach { message ->
            Text(message, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DirectController(
    enabled: Boolean,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val horizontalLayout = maxWidth >= 850.dp
        if (horizontalLayout) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GrabReleaseControls(enabled, onCommand, Modifier.width(100.dp))
                WristPad(enabled, onCommand, Modifier.weight(1f), compact = true)
                ArmPad(enabled, onCommand, Modifier.weight(1f), compact = true)
                WheelsPad(enabled, onCommand, Modifier.weight(1f), compact = true)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                GrabReleaseControls(
                    enabled,
                    onCommand,
                    Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    horizontal = true
                )
                WristPad(enabled, onCommand, Modifier.fillMaxWidth().widthIn(max = 420.dp))
                ArmPad(enabled, onCommand, Modifier.fillMaxWidth().widthIn(max = 420.dp))
                WheelsPad(enabled, onCommand, Modifier.fillMaxWidth().widthIn(max = 420.dp))
                Spacer(Modifier.height(8.dp))
            }
        }
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
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            HoldCommandButton("GRAB", enabled, ControllerCommands.GRAB_START, ControllerCommands.GRAB_STOP, onCommand)
            HoldCommandButton("RELEASE", enabled, ControllerCommands.RELEASE_START, ControllerCommands.RELEASE_STOP, onCommand)
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HoldCommandButton("GRAB", enabled, ControllerCommands.GRAB_START, ControllerCommands.GRAB_STOP, onCommand)
            HoldCommandButton("RELEASE", enabled, ControllerCommands.RELEASE_START, ControllerCommands.RELEASE_STOP, onCommand)
        }
    }
}

@Composable
private fun WristPad(
    enabled: Boolean,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var wristPosition by remember { mutableStateOf(WristPosition()) }

    fun moveHorizontal(delta: Int) {
        val next = wristPosition.moveHorizontal(delta)
        if (next != wristPosition) {
            wristPosition = next
            onCommand(ControllerCommands.wristHorizontal(next.horizontal))
        }
    }

    fun moveVertical(delta: Int) {
        val next = wristPosition.moveVertical(delta)
        if (next != wristPosition) {
            wristPosition = next
            onCommand(ControllerCommands.wristVertical(next.vertical))
        }
    }

    ControllerCard("WRIST", modifier, compact) { buttonWidth, buttonHeight, centerSize ->
        RepeatingCommandButton("UP", enabled, { moveVertical(-WristPosition.STEP_DEGREES) }, width = buttonWidth, height = buttonHeight)
        DirectionRow(centerSize) {
            RepeatingCommandButton("L", enabled, { moveHorizontal(WristPosition.STEP_DEGREES) }, width = buttonWidth, height = buttonHeight)
            DirectionCenter(centerSize)
            RepeatingCommandButton("R", enabled, { moveHorizontal(-WristPosition.STEP_DEGREES) }, width = buttonWidth, height = buttonHeight)
        }
        RepeatingCommandButton("DOWN", enabled, { moveVertical(WristPosition.STEP_DEGREES) }, width = buttonWidth, height = buttonHeight)
    }
}

@Composable
private fun ArmPad(enabled: Boolean, onCommand: (String) -> Unit, modifier: Modifier, compact: Boolean = false) {
    ControllerPad(
        "ARM", "UP", "DOWN", "L", "R", enabled,
        ControllerCommands.ARM_UP, ControllerCommands.ARM_DOWN,
        ControllerCommands.ARM_LEFT, ControllerCommands.ARM_RIGHT,
        ControllerCommands.ARM_VERTICAL_STOP, ControllerCommands.ARM_HORIZONTAL_STOP,
        onCommand, modifier, compact
    )
}

@Composable
private fun WheelsPad(enabled: Boolean, onCommand: (String) -> Unit, modifier: Modifier, compact: Boolean = false) {
    ControllerPad(
        "WHEELS", "FORWARD", "BACK", "L", "R", enabled,
        ControllerCommands.WHEELS_FORWARD, ControllerCommands.WHEELS_BACK,
        ControllerCommands.WHEELS_LEFT, ControllerCommands.WHEELS_RIGHT,
        ControllerCommands.WHEELS_FB_STOP, ControllerCommands.WHEELS_RL_STOP,
        onCommand, modifier, compact
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
    verticalStopCommand: String,
    horizontalStopCommand: String,
    onCommand: (String) -> Unit,
    modifier: Modifier,
    compact: Boolean
) {
    ControllerCard(title, modifier, compact) { buttonWidth, buttonHeight, centerSize ->
        HoldCommandButton(upLabel, enabled, upCommand, verticalStopCommand, onCommand, width = buttonWidth, height = buttonHeight)
        DirectionRow(centerSize) {
            HoldCommandButton(leftLabel, enabled, leftCommand, horizontalStopCommand, onCommand, width = buttonWidth, height = buttonHeight)
            DirectionCenter(centerSize)
            HoldCommandButton(rightLabel, enabled, rightCommand, horizontalStopCommand, onCommand, width = buttonWidth, height = buttonHeight)
        }
        HoldCommandButton(downLabel, enabled, downCommand, verticalStopCommand, onCommand, width = buttonWidth, height = buttonHeight)
    }
}

@Composable
private fun ControllerCard(
    title: String,
    modifier: Modifier,
    compact: Boolean,
    content: @Composable (buttonWidth: Dp, buttonHeight: Dp, centerSize: Dp) -> Unit
) {
    val buttonWidth = if (compact) 60.dp else 78.dp
    val buttonHeight = if (compact) 48.dp else 52.dp
    val centerSize = if (compact) 38.dp else 46.dp
    Card(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content(buttonWidth, buttonHeight, centerSize)
        }
    }
}

@Composable
private fun DirectionRow(centerSize: Dp, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            if (centerSize < 40.dp) 4.dp else 6.dp,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun DirectionCenter(size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Text("+", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun HoldCommandButton(
    label: String,
    enabled: Boolean,
    pressCommand: String,
    releaseCommand: String,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 78.dp,
    height: Dp = 52.dp
) {
    ControlSurface(label, enabled, modifier, width, height) {
        onCommand(pressCommand)
        try {
            tryAwaitRelease()
        } finally {
            onCommand(releaseCommand)
        }
    }
}

@Composable
private fun RepeatingCommandButton(
    label: String,
    enabled: Boolean,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 78.dp,
    height: Dp = 52.dp
) {
    val currentOnStep by rememberUpdatedState(onStep)
    ControlSurface(label, enabled, modifier, width, height) {
        coroutineScope {
            val movementJob = launch {
                while (true) {
                    currentOnStep()
                    delay(WristPosition.STEP_INTERVAL_MS)
                }
            }
            try {
                tryAwaitRelease()
            } finally {
                movementJob.cancel()
            }
        }
    }
}

@Composable
private fun ControlSurface(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    width: Dp,
    height: Dp,
    onPress: suspend androidx.compose.foundation.gestures.PressGestureScope.() -> Unit
) {
    val currentOnPress by rememberUpdatedState(onPress)
    Surface(
        modifier = modifier
            .size(width, height)
            .pointerInput(enabled) {
                detectTapGestures(onPress = {
                    if (enabled) currentOnPress()
                })
            },
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
