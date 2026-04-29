package com.gabotapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), SerialInterface.SerialListener, BluetoothServerManager.Listener {

    companion object {
        val MAJOR_VER = BuildConfig.MAJOR_VER
        val MINOR_VER = BuildConfig.MINOR_VER
        val MICRO_VER = BuildConfig.MICRO_VER
    }

    private var serialManager: SerialInterface? = null
    private lateinit var bluetoothServerManager: BluetoothServerManager
    private lateinit var deviceSpinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var refreshButton: Button
    private lateinit var sendButton: Button
    private lateinit var clearButton: Button
    private lateinit var messageInput: EditText
    private lateinit var logListView: ListView
    private lateinit var statusText: TextView

    private val logMessages = mutableListOf<String>()
    private lateinit var logAdapter: ArrayAdapter<String>
    private var availableDevices = listOf<SerialInterface.DeviceInfo>()
    private var isConnecting = false
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
        setContentView(R.layout.activity_main)

        initViews()
        setupAdapters()
        initSerialManager()
        initBluetoothServer()

        handleIntent(intent)
    }

    private fun initViews() {
        deviceSpinner = findViewById(R.id.deviceSpinner)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        refreshButton = findViewById(R.id.refreshButton)
        sendButton = findViewById(R.id.sendButton)
        clearButton = findViewById(R.id.clearButton)
        messageInput = findViewById(R.id.messageInput)
        logListView = findViewById(R.id.logListView)
        statusText = findViewById(R.id.statusText)

        connectButton.setOnClickListener { connect() }
        disconnectButton.setOnClickListener { disconnect() }
        refreshButton.setOnClickListener { refreshDevices() }
        sendButton.setOnClickListener { sendMessage() }
        clearButton.setOnClickListener { clearLog() }

        updateConnectionUI(false)
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
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupAdapters() {
        logAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logMessages)
        logListView.adapter = logAdapter
    }

    private fun refreshDevices() {
        availableDevices = serialManager?.findDevices() ?: emptyList()
        val deviceNames = availableDevices.map { "${it.name} - ${it.description}" }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        deviceSpinner.adapter = adapter

        if (availableDevices.isEmpty()) {
            addLog("No devices found")
            connectButton.isEnabled = false
        } else {
            addLog("Found ${availableDevices.size} device(s)")
            connectButton.isEnabled = !isConnecting
        }
    }

    private fun connect() {
        val selectedIndex = deviceSpinner.selectedItemPosition
        if (selectedIndex < 0 || selectedIndex >= availableDevices.size) {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show()
            return
        }

        addLog("Connecting at ${SerialManager.BAUD_RATE} baud...")
        isConnecting = true
        connectButton.isEnabled = false
        serialManager?.connect(selectedIndex)
    }

    private fun disconnect() {
        isConnecting = false
        serialManager?.disconnect()
        addLog("Disconnected")
    }

    private fun sendMessage() {
        val message = messageInput.text.toString()
        if (message.isEmpty()) {
            Toast.makeText(this, "Enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        serialManager?.send(message + "\n")
        addLog("TX: $message")
        messageInput.text.clear()
    }

    private fun clearLog() {
        logMessages.clear()
        logAdapter.notifyDataSetChanged()
    }

    private fun addLog(message: String) {
        runOnUiThread {
            logMessages.add(message)
            logAdapter.notifyDataSetChanged()
            logListView.setSelection(logMessages.size - 1)
        }
    }

    private fun updateConnectionUI(connected: Boolean) {
        runOnUiThread {
            connectButton.visibility = if (connected) View.GONE else View.VISIBLE
            disconnectButton.visibility = if (connected) View.VISIBLE else View.GONE
            connectButton.isEnabled = !isConnecting && availableDevices.isNotEmpty()
            deviceSpinner.isEnabled = !connected && !isConnecting
            refreshButton.isEnabled = !connected && !isConnecting
            sendButton.isEnabled = connected
            messageInput.isEnabled = connected

            statusText.text = if (connected) getString(R.string.status_connected) else getString(R.string.status_disconnected)
            statusText.setTextColor(
                if (connected) getColor(android.R.color.holo_green_dark)
                else getColor(android.R.color.holo_red_dark)
            )
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
        updateConnectionUI(connected)
        if (!connected) {
            serialReceiveBuffer.setLength(0)
        }
        sendBluetoothInfo(if (connected) "serial connected" else "serial disconnected")
        addLog(if (connected) "Connected successfully" else "Connection closed")
    }

    override fun onError(message: String) {
        isConnecting = false
        updateConnectionUI(serialManager?.isConnected == true)
        addLog("Error: $message")
        bluetoothServerManager.sendLine("ERR: $message")
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

        val manager = serialManager
        if (manager == null || !manager.isConnected) {
            addLog("BT message ignored, serial is disconnected")
            bluetoothServerManager.sendLine("ERR: serial disconnected")
            return
        }

        manager.send("$message\n")
        addLog("BT→Serial: $message")
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
            addLog("Serial→BT: $line")
            newlineIndex = serialReceiveBuffer.indexOf("\n")
        }
    }

    private fun sendBluetoothInfo(message: String) {
        bluetoothServerManager.sendLine("INFO: $message")
    }
}
