package com.gabotapp

import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity(), SerialInterface.SerialListener {

    private var serialManager: SerialInterface? = null
    private lateinit var deviceSpinner: Spinner
    private lateinit var baudRateSpinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var refreshButton: Button
    private lateinit var sendButton: Button
    private lateinit var clearButton: Button
    private lateinit var messageInput: EditText
    private lateinit var logListView: ListView
    private lateinit var statusText: TextView
    private lateinit var simulationSwitch: SwitchMaterial

    private val logMessages = mutableListOf<String>()
    private lateinit var logAdapter: ArrayAdapter<String>
    private var availableDevices = listOf<SerialInterface.DeviceInfo>()

    private val baudRates = listOf(9600, 19200, 38400, 57600, 115200)

    private var isSimulationMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupAdapters()
        initSerialManager()

        handleIntent(intent)
    }

    private fun initViews() {
        deviceSpinner = findViewById(R.id.deviceSpinner)
        baudRateSpinner = findViewById(R.id.baudRateSpinner)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        refreshButton = findViewById(R.id.refreshButton)
        sendButton = findViewById(R.id.sendButton)
        clearButton = findViewById(R.id.clearButton)
        messageInput = findViewById(R.id.messageInput)
        logListView = findViewById(R.id.logListView)
        statusText = findViewById(R.id.statusText)
        simulationSwitch = findViewById(R.id.simulationSwitch)

        connectButton.setOnClickListener { connect() }
        disconnectButton.setOnClickListener { disconnect() }
        refreshButton.setOnClickListener { refreshDevices() }
        sendButton.setOnClickListener { sendMessage() }
        clearButton.setOnClickListener { clearLog() }

        simulationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (serialManager?.isConnected == true) {
                serialManager?.disconnect()
            }
            isSimulationMode = isChecked
            initSerialManager()
            addLog(if (isChecked) "Simulation mode enabled" else "Hardware mode enabled")
        }

        updateConnectionUI(false)
    }

    private fun initSerialManager() {
        serialManager?.destroy()
        serialManager = if (isSimulationMode) {
            MockSerialManager()
        } else {
            SerialManager(this)
        }
        serialManager?.listener = this
        refreshDevices()
    }

    private fun setupAdapters() {
        logAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logMessages)
        logListView.adapter = logAdapter

        val baudAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, baudRates)
        baudAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        baudRateSpinner.adapter = baudAdapter
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
            connectButton.isEnabled = true
        }
    }

    private fun connect() {
        val selectedIndex = deviceSpinner.selectedItemPosition
        if (selectedIndex < 0 || selectedIndex >= availableDevices.size) {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show()
            return
        }

        val baudRate = baudRates[baudRateSpinner.selectedItemPosition]
        addLog("Connecting at $baudRate baud...")
        serialManager?.connect(selectedIndex, baudRate)
    }

    private fun disconnect() {
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
            deviceSpinner.isEnabled = !connected
            baudRateSpinner.isEnabled = !connected
            refreshButton.isEnabled = !connected
            simulationSwitch.isEnabled = !connected
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
            if (!isSimulationMode) {
                refreshDevices()
            }
        }
    }

    override fun onDataReceived(data: String) {
        addLog("RX: $data")
    }

    override fun onConnectionStateChanged(connected: Boolean) {
        updateConnectionUI(connected)
        addLog(if (connected) "Connected successfully" else "Connection closed")
    }

    override fun onError(message: String) {
        addLog("Error: $message")
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        serialManager?.destroy()
        super.onDestroy()
    }
}
