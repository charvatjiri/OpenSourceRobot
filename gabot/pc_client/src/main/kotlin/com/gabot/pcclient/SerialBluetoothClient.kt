package com.gabot.pcclient

import com.fazecast.jSerialComm.SerialPort
import java.awt.EventQueue
import java.io.IOException
import kotlin.concurrent.thread

class SerialBluetoothClient {
    interface Listener {
        fun onConnectionStateChanged(connected: Boolean)
        fun onLineReceived(line: String)
        fun onError(message: String)
    }

    data class PortInfo(
        val systemName: String,
        val descriptiveName: String
    ) {
        val displayName: String
            get() = "$descriptiveName ($systemName)"
    }

    @Volatile
    private var port: SerialPort? = null
    private var readThread: Thread? = null
    private val writeLock = Any()

    var listener: Listener? = null

    val isConnected: Boolean
        get() = port?.isOpen == true

    fun listPorts(): List<PortInfo> = SerialPort.getCommPorts()
        .map { PortInfo(it.systemPortName, it.descriptivePortName) }
        .sortedWith(compareBy<PortInfo> { it.descriptiveName }.thenBy { it.systemName })

    fun connect(systemName: String) {
        disconnect(notify = false)
        val selectedPort = SerialPort.getCommPort(systemName).apply {
            setComPortParameters(BAUD_RATE, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
            setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MS, 0)
        }

        if (!selectedPort.openPort()) {
            dispatch { listener?.onError("Cannot open Bluetooth serial port $systemName") }
            dispatch { listener?.onConnectionStateChanged(false) }
            return
        }

        port = selectedPort
        dispatch { listener?.onConnectionStateChanged(true) }
        startReadLoop(selectedPort)
    }

    fun sendLine(command: String): Boolean {
        val activePort = port
        if (activePort == null || !activePort.isOpen) {
            dispatch { listener?.onError("Bluetooth serial port is not connected") }
            return false
        }

        val framedCommand = frameCommand(command)
        if (framedCommand == null) {
            dispatch { listener?.onError("Command is empty") }
            return false
        }

        return try {
            val bytes = framedCommand.toByteArray(Charsets.UTF_8)
            val written = synchronized(writeLock) {
                activePort.writeBytes(bytes, bytes.size)
            }
            if (written != bytes.size) {
                throw IOException("Only $written of ${bytes.size} bytes were written")
            }
            true
        } catch (exception: Exception) {
            dispatch { listener?.onError("Bluetooth write failed: ${exception.message}") }
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

    private fun startReadLoop(activePort: SerialPort) {
        readThread = thread(name = "gabot-pc-bt-read", isDaemon = true) {
            try {
                activePort.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (activePort == port && activePort.isOpen) {
                        val line = reader.readLine() ?: break
                        dispatch { listener?.onLineReceived(line) }
                    }
                }
            } catch (exception: Exception) {
                if (activePort == port) {
                    dispatch { listener?.onError("Bluetooth read failed: ${exception.message}") }
                }
            } finally {
                if (activePort == port) {
                    disconnect(notify = true)
                } else if (activePort.isOpen) {
                    activePort.closePort()
                }
            }
        }
    }

    private fun disconnect(notify: Boolean) {
        val activePort = port
        val wasConnected = activePort?.isOpen == true
        port = null
        if (activePort?.isOpen == true) {
            activePort.closePort()
        }
        readThread?.interrupt()
        readThread = null
        if (notify && wasConnected) {
            dispatch { listener?.onConnectionStateChanged(false) }
        }
    }

    private fun dispatch(block: () -> Unit) {
        if (EventQueue.isDispatchThread()) {
            block()
        } else {
            EventQueue.invokeLater(block)
        }
    }

    companion object {
        const val BAUD_RATE = 115200
        private const val READ_TIMEOUT_MS = 250

        fun frameCommand(command: String): String? {
            val line = command.trimEnd('\r', '\n')
            return if (line.isBlank()) null else "$line\n"
        }
    }
}
