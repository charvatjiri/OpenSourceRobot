package com.gabotapp

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

class MockSerialManager : SerialInterface {

    override var listener: SerialInterface.SerialListener? = null
    override var isConnected: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var simulationRunnable: Runnable? = null

    private val mockDevices = listOf(
        SerialInterface.DeviceInfo(0, "Mock Arduino Mega 2560", "CH340 - /dev/ttyUSB0 (VID:6790 PID:29987)"),
        SerialInterface.DeviceInfo(1, "Mock Arduino Uno", "ATmega16U2 - /dev/ttyUSB1 (VID:9025 PID:67)"),
        SerialInterface.DeviceInfo(2, "Mock ESP32", "CP2102 - /dev/ttyUSB2 (VID:4292 PID:60000)")
    )

    private val mockResponses = mapOf(
        "ping" to "pong",
        "hello" to "Hello from Arduino!",
        "status" to "OK: Temp=25.3C, Humidity=45%",
        "led on" to "LED turned ON",
        "led off" to "LED turned OFF",
        "help" to "Commands: ping, hello, status, led on, led off, sensor, version",
        "sensor" to "Sensor reading: ${Random.nextInt(0, 1024)}",
        "version" to "Arduino Mega 2560 v1.0.0 (Mock)"
    )

    override fun findDevices(): List<SerialInterface.DeviceInfo> {
        return mockDevices
    }

    override fun connect(deviceIndex: Int, baudRate: Int) {
        if (deviceIndex < 0 || deviceIndex >= mockDevices.size) {
            listener?.onError("Invalid device index")
            return
        }

        handler.postDelayed({
            isConnected = true
            listener?.onConnectionStateChanged(true)
            
            handler.postDelayed({
                listener?.onDataReceived("Arduino ready! (Simulation mode)")
            }, 500)
            
            startPeriodicSimulation()
        }, 300)
    }

    override fun send(data: String) {
        if (!isConnected) {
            listener?.onError("Not connected")
            return
        }

        val command = data.trim().lowercase()
        
        handler.postDelayed({
            val response = mockResponses[command] 
                ?: if (command.isNotEmpty()) "Unknown command: $command" else ""
            
            if (response.isNotEmpty()) {
                listener?.onDataReceived(response)
            }
        }, Random.nextLong(50, 200))
    }

    override fun sendBytes(data: ByteArray) {
        send(String(data))
    }

    override fun disconnect() {
        stopPeriodicSimulation()
        isConnected = false
        listener?.onConnectionStateChanged(false)
    }

    override fun destroy() {
        disconnect()
    }

    private fun startPeriodicSimulation() {
        simulationRunnable = object : Runnable {
            override fun run() {
                if (isConnected && Random.nextFloat() < 0.1f) {
                    val randomData = listOf(
                        "Heartbeat: ${System.currentTimeMillis() % 10000}",
                        "Sensor update: ${Random.nextInt(0, 100)}",
                        "Status: OK"
                    ).random()
                    listener?.onDataReceived(randomData)
                }
                if (isConnected) {
                    handler.postDelayed(this, 3000)
                }
            }
        }
        handler.postDelayed(simulationRunnable!!, 5000)
    }

    private fun stopPeriodicSimulation() {
        simulationRunnable?.let { handler.removeCallbacks(it) }
        simulationRunnable = null
    }
}
