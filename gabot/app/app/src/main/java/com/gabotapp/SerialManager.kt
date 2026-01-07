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
import androidx.core.content.IntentCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.concurrent.Executors

class SerialManager(private val context: Context) : SerialInterface, SerialInputOutputManager.Listener {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.gabotapp.USB_PERMISSION"
        const val DEFAULT_BAUD_RATE = 9600
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null
    private var ioManager: SerialInputOutputManager? = null
    private var availableDrivers = listOf<UsbSerialDriver>()
    private var pendingBaudRate: Int = DEFAULT_BAUD_RATE

    override var listener: SerialInterface.SerialListener? = null
    override var isConnected: Boolean = false
        private set

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { connectToDevice(it, null, pendingBaudRate) }
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
        return availableDrivers.mapIndexed { index, driver ->
            val device = driver.device
            SerialInterface.DeviceInfo(
                index = index,
                name = driver.javaClass.simpleName,
                description = "${device.deviceName} (VID:${device.vendorId} PID:${device.productId})"
            )
        }
    }

    override fun connect(deviceIndex: Int, baudRate: Int) {
        if (deviceIndex < 0 || deviceIndex >= availableDrivers.size) {
            listener?.onError("Invalid device index")
            return
        }
        val driver = availableDrivers[deviceIndex]
        val device = driver.device
        pendingBaudRate = baudRate
        if (usbManager.hasPermission(device)) {
            connectToDevice(device, driver, baudRate)
        } else {
            requestPermission(device)
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun connectToDevice(device: UsbDevice, driver: UsbSerialDriver? = null, baudRate: Int = DEFAULT_BAUD_RATE) {
        try {
            val actualDriver = driver ?: UsbSerialProber.getDefaultProber().probeDevice(device)
            if (actualDriver == null) {
                listener?.onError("No driver for device")
                return
            }

            connection = usbManager.openDevice(device)
            if (connection == null) {
                listener?.onError("Could not open connection")
                return
            }

            serialPort = actualDriver.ports[0]
            serialPort?.open(connection)
            serialPort?.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            ioManager = SerialInputOutputManager(serialPort, this)
            Executors.newSingleThreadExecutor().submit(ioManager)

            isConnected = true
            listener?.onConnectionStateChanged(true)

        } catch (e: IOException) {
            listener?.onError("Connection failed: ${e.message}")
            disconnect()
        }
    }

    override fun send(data: String) {
        if (!isConnected) {
            listener?.onError("Not connected")
            return
        }
        try {
            serialPort?.write(data.toByteArray(), 1000)
        } catch (e: IOException) {
            listener?.onError("Send failed: ${e.message}")
        }
    }

    override fun sendBytes(data: ByteArray) {
        if (!isConnected) {
            listener?.onError("Not connected")
            return
        }
        try {
            serialPort?.write(data, 1000)
        } catch (e: IOException) {
            listener?.onError("Send failed: ${e.message}")
        }
    }

    override fun disconnect() {
        ioManager?.listener = null
        ioManager?.stop()
        ioManager = null

        try {
            serialPort?.close()
        } catch (_: IOException) {}
        serialPort = null

        connection?.close()
        connection = null

        isConnected = false
        listener?.onConnectionStateChanged(false)
    }

    override fun destroy() {
        disconnect()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: IllegalArgumentException) {}
    }

    override fun onNewData(data: ByteArray) {
        val received = String(data)
        listener?.onDataReceived(received)
    }

    override fun onRunError(e: Exception) {
        listener?.onError("Serial error: ${e.message}")
        disconnect()
    }
}
