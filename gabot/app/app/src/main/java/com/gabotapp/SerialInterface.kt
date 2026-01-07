package com.gabotapp

interface SerialInterface {
    var listener: SerialListener?
    val isConnected: Boolean

    fun findDevices(): List<DeviceInfo>
    fun connect(deviceIndex: Int, baudRate: Int)
    fun send(data: String)
    fun sendBytes(data: ByteArray)
    fun disconnect()
    fun destroy()

    interface SerialListener {
        fun onDataReceived(data: String)
        fun onConnectionStateChanged(connected: Boolean)
        fun onError(message: String)
    }

    data class DeviceInfo(
        val index: Int,
        val name: String,
        val description: String
    )
}
