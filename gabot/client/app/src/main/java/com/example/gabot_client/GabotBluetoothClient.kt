package com.example.gabot_client

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

class GabotBluetoothClient(context: Context) {

    interface Listener {
        fun onConnectionStateChanged(connected: Boolean)
        fun onLineReceived(line: String)
        fun onError(message: String)
    }

    data class DeviceInfo(
        val name: String,
        val address: String
    ) {
        val displayName: String
            get() = "$name ($address)"
    }

    companion object {
        private val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    @Volatile
    private var socket: BluetoothSocket? = null

    private var connectThread: Thread? = null
    private var readThread: Thread? = null
    private val writeLock = Any()

    var listener: Listener? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasRequiredPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun listBondedDevices(): List<DeviceInfo> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!hasRequiredPermission()) {
            listener?.onError("Bluetooth permission is missing")
            return emptyList()
        }
        return try {
            adapter.bondedDevices
                .map { device ->
                    DeviceInfo(
                        name = device.name ?: "Unknown device",
                        address = device.address
                    )
                }
                .sortedWith(compareBy<DeviceInfo> { it.name }.thenBy { it.address })
        } catch (e: SecurityException) {
            listener?.onError("Bluetooth paired-device read denied: ${e.message}")
            emptyList()
        }
    }

    fun connect(address: String) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            listener?.onError("Bluetooth is not supported on this device")
            return
        }
        if (!hasRequiredPermission()) {
            listener?.onError("Bluetooth permission is missing")
            return
        }
        if (!adapter.isEnabled) {
            listener?.onError("Bluetooth is disabled")
            return
        }

        disconnect(notify = false)
        connectThread = thread(name = "gabot-bt-connect", start = true) {
            try {
                adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(address)
                val newSocket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                newSocket.connect()
                socket = newSocket
                listener?.onConnectionStateChanged(true)
                startReadLoop(newSocket)
            } catch (e: IOException) {
                closeSocket()
                listener?.onError("Bluetooth connect failed: ${e.message}")
                listener?.onConnectionStateChanged(false)
            } catch (e: IllegalArgumentException) {
                closeSocket()
                listener?.onError("Invalid Bluetooth address: ${e.message}")
                listener?.onConnectionStateChanged(false)
            } catch (e: SecurityException) {
                closeSocket()
                listener?.onError("Bluetooth connect denied: ${e.message}")
                listener?.onConnectionStateChanged(false)
            }
        }
    }

    fun sendLine(command: String): Boolean {
        val activeSocket = socket
        if (activeSocket == null || !activeSocket.isConnected) {
            listener?.onError("Bluetooth is not connected")
            return false
        }

        val line = command.trimEnd('\r', '\n')
        if (line.isBlank()) {
            listener?.onError("Command is empty")
            return false
        }

        return try {
            synchronized(writeLock) {
                activeSocket.outputStream.write("$line\n".toByteArray(Charsets.UTF_8))
                activeSocket.outputStream.flush()
            }
            true
        } catch (e: IOException) {
            listener?.onError("Bluetooth write failed: ${e.message}")
            disconnect(notify = true)
            false
        } catch (e: SecurityException) {
            listener?.onError("Bluetooth write denied: ${e.message}")
            disconnect(notify = true)
            false
        }
    }

    fun disconnect() {
        disconnect(notify = true)
    }

    fun destroy() {
        disconnect(notify = false)
    }

    private fun startReadLoop(activeSocket: BluetoothSocket) {
        readThread = thread(name = "gabot-bt-read", start = true) {
            try {
                activeSocket.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (activeSocket == socket && activeSocket.isConnected) {
                        val line = reader.readLine() ?: break
                        listener?.onLineReceived(line)
                    }
                }
            } catch (e: IOException) {
                if (activeSocket == socket) {
                    listener?.onError("Bluetooth read failed: ${e.message}")
                }
            } catch (e: SecurityException) {
                if (activeSocket == socket) {
                    listener?.onError("Bluetooth read denied: ${e.message}")
                }
            } finally {
                if (activeSocket == socket) {
                    disconnect(notify = true)
                } else {
                    try {
                        activeSocket.close()
                    } catch (_: IOException) {
                    }
                }
            }
        }
    }

    private fun disconnect(notify: Boolean) {
        connectThread?.interrupt()
        connectThread = null

        readThread?.interrupt()
        readThread = null

        val wasConnected = socket != null
        closeSocket()
        if (notify && wasConnected) {
            listener?.onConnectionStateChanged(false)
        }
    }

    private fun closeSocket() {
        val activeSocket = socket
        socket = null
        try {
            activeSocket?.close()
        } catch (_: IOException) {
        }
    }
}
