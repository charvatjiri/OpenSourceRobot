package com.gabotapp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SerialManager(private val context: Context) : SerialInterface, SerialInputOutputManager.Listener {

    companion object {
        private const val TAG = "GabotApp-Serial"
        private const val ACTION_USB_PERMISSION = "com.gabotapp.USB_PERMISSION"
        const val BAUD_RATE = 115200
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null
    private var ioManager: SerialInputOutputManager? = null
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var availableDrivers = listOf<UsbSerialDriver>()
    override var listener: SerialInterface.SerialListener? = null
    override var isConnected: Boolean = false
        private set
    private var isConnecting: Boolean = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    if (device == null) {
                        listener?.onError("USB permission response missing device")
                    } else if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        connectToDevice(device)
                    } else {
                        listener?.onError("USB permission denied")
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    override fun findDevices(): List<SerialInterface.DeviceInfo> {
        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        Log.d(TAG, "Found ${availableDrivers.size} USB serial driver(s)")
        return availableDrivers.mapIndexed { index, driver ->
            val device = driver.device
            SerialInterface.DeviceInfo(
                index = index,
                name = driver.javaClass.simpleName,
                description = "${device.deviceName} (VID:${device.vendorId} PID:${device.productId})"
            )
        }
    }

    override fun connect(deviceIndex: Int) {
        if (deviceIndex < 0 || deviceIndex >= availableDrivers.size) {
            listener?.onError("Invalid device index")
            return
        }
        if (isConnected || isConnecting) {
            listener?.onError("Connection already in progress")
            return
        }
        val driver = availableDrivers[deviceIndex]
        val device = driver.device
        if (usbManager.hasPermission(device)) {
            connectToDevice(device, driver)
        } else {
            requestPermission(device)
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntentValue = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, permissionIntentValue, flags
        )
        try {
            usbManager.requestPermission(device, permissionIntent)
        } catch (e: Exception) {
            isConnecting = false
            listener?.onError("Permission request failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun connectToDevice(device: UsbDevice, driver: UsbSerialDriver? = null) {
        isConnecting = true
        try {
            val actualDriver = driver ?: UsbSerialProber.getDefaultProber().probeDevice(device)
            if (actualDriver == null) {
                listener?.onError("No driver for device")
                return
            }
            if (actualDriver.ports.isEmpty()) {
                listener?.onError("Device has no serial ports")
                return
            }

            cleanupConnection(notifyListener = false)

            val openedConnection = usbManager.openDevice(device)
            if (openedConnection == null) {
                listener?.onError("Could not open connection")
                return
            }
            connection = openedConnection

            val openedPort = actualDriver.ports.first()
            openedPort.open(openedConnection)
            openedPort.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            serialPort = openedPort

            val newIoManager = SerialInputOutputManager(openedPort, this)
            ioManager = newIoManager
            ioExecutor.submit(newIoManager)

            isConnected = true
            Log.d(TAG, "Serial connected: ${device.deviceName}")
            listener?.onConnectionStateChanged(true)

        } catch (e: Exception) {
            listener?.onError("Connection failed: ${e.message ?: e.javaClass.simpleName}")
            cleanupConnection(notifyListener = false)
        } finally {
            isConnecting = false
        }
    }

    override fun send(data: String) {
        if (!isConnected) {
            listener?.onError("Not connected")
            return
        }
        try {
            Log.d(TAG, "Serial TX: $data")
            serialPort?.write(data.toByteArray(), 1000)
        } catch (e: Exception) {
            listener?.onError("Send failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun sendBytes(data: ByteArray) {
        if (!isConnected) {
            listener?.onError("Not connected")
            return
        }
        try {
            Log.d(TAG, "Serial TX bytes: ${data.size}")
            serialPort?.write(data, 1000)
        } catch (e: Exception) {
            listener?.onError("Send failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun disconnect() {
        cleanupConnection(notifyListener = true)
    }

    private fun cleanupConnection(notifyListener: Boolean) {
        ioManager?.listener = null
        ioManager?.stop()
        ioManager = null

        try {
            serialPort?.close()
        } catch (_: IOException) {}
        serialPort = null

        connection?.close()
        connection = null

        isConnecting = false
        isConnected = false
        if (notifyListener) {
            listener?.onConnectionStateChanged(false)
        }
    }

    override fun destroy() {
        disconnect()
        ioExecutor.shutdownNow()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: IllegalArgumentException) {}
    }

    override fun onNewData(data: ByteArray) {
        val received = String(data)
        Log.d(TAG, "Serial RX chunk: $received")
        listener?.onDataReceived(received)
    }

    override fun onRunError(e: Exception) {
        listener?.onError("Serial error: ${e.message}")
        disconnect()
    }
}
