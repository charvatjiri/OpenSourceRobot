package com.gabotapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothServerManager(context: Context) {

    interface Listener {
        fun onServerStarted()
        fun onClientConnected(name: String)
        fun onClientDisconnected()
        fun onMessageReceived(message: String)
        fun onError(message: String)
    }

    companion object {
        const val SERVICE_NAME = "GabotAppServer"
        val SERVICE_UUID: UUID = UUID.fromString("6F0F3F9A-89E1-4B2D-9D0E-EC7E98DB58B3")
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    @Volatile
    private var serverRunning = false

    @Volatile
    private var serverSocket: BluetoothServerSocket? = null

    @Volatile
    private var clientSocket: BluetoothSocket? = null

    private var acceptThread: Thread? = null
    private var readThread: Thread? = null
    private val writeLock = Any()

    var listener: Listener? = null

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

    fun start() {
        if (serverRunning) {
            return
        }

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

        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
            serverRunning = true
            acceptThread = thread(name = "bt-server-accept", start = true) {
                acceptLoop()
            }
            listener?.onServerStarted()
        } catch (e: IOException) {
            listener?.onError("Bluetooth server start failed: ${e.message}")
            stop()
        } catch (e: SecurityException) {
            listener?.onError("Bluetooth server start denied: ${e.message}")
            stop()
        }
    }

    fun stop() {
        serverRunning = false
        closeClient(notify = false)

        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null

        acceptThread?.interrupt()
        acceptThread = null

        readThread?.interrupt()
        readThread = null
    }

    fun hasClientConnection(): Boolean = clientSocket?.isConnected == true

    fun sendLine(message: String): Boolean {
        return sendRaw("$message\n")
    }

    fun sendRaw(message: String): Boolean {
        val socket = clientSocket ?: return false
        return try {
            synchronized(writeLock) {
                socket.outputStream.write(message.toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
            }
            true
        } catch (e: IOException) {
            closeClient(notify = true)
            listener?.onError("Bluetooth write failed: ${e.message}")
            false
        } catch (e: SecurityException) {
            closeClient(notify = true)
            listener?.onError("Bluetooth write denied: ${e.message}")
            false
        }
    }

    private fun acceptLoop() {
        while (serverRunning) {
            try {
                val acceptedSocket = serverSocket?.accept() ?: break
                if (!serverRunning) {
                    acceptedSocket.close()
                    break
                }

                closeClient(notify = false)
                clientSocket = acceptedSocket

                val clientName = runCatching {
                    acceptedSocket.remoteDevice?.name?.ifBlank { null }
                        ?: acceptedSocket.remoteDevice?.address
                        ?: "unknown device"
                }.getOrElse { "unknown device" }
                listener?.onClientConnected(clientName)

                readThread = thread(name = "bt-server-read", start = true) {
                    readLoop(acceptedSocket)
                }
            } catch (e: IOException) {
                if (serverRunning) {
                    listener?.onError("Bluetooth accept failed: ${e.message}")
                }
                break
            } catch (e: SecurityException) {
                if (serverRunning) {
                    listener?.onError("Bluetooth accept denied: ${e.message}")
                }
                break
            }
        }
    }

    private fun readLoop(socket: BluetoothSocket) {
        try {
            socket.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (serverRunning && socket == clientSocket) {
                    val line = reader.readLine() ?: break
                    listener?.onMessageReceived(line)
                }
            }
        } catch (e: IOException) {
            if (serverRunning && socket == clientSocket) {
                listener?.onError("Bluetooth read failed: ${e.message}")
            }
        } catch (e: SecurityException) {
            if (serverRunning && socket == clientSocket) {
                listener?.onError("Bluetooth read denied: ${e.message}")
            }
        } finally {
            if (socket == clientSocket) {
                closeClient(notify = true)
            } else {
                try {
                    socket.close()
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun closeClient(notify: Boolean) {
        val activeSocket = clientSocket
        clientSocket = null

        try {
            activeSocket?.close()
        } catch (_: IOException) {
        }

        val activeReadThread = readThread
        if (activeReadThread != Thread.currentThread()) {
            activeReadThread?.interrupt()
        }
        readThread = null

        if (notify && activeSocket != null) {
            listener?.onClientDisconnected()
        }
    }
}
