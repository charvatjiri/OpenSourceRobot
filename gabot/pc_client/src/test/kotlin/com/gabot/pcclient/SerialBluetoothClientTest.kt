package com.gabot.pcclient

import com.fazecast.jSerialComm.SerialPortTimeoutException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SerialBluetoothClientTest {
    @Test
    fun `command is terminated by one newline`() {
        assertEquals("version\n", SerialBluetoothClient.frameCommand("version"))
        assertEquals("version\n", SerialBluetoothClient.frameCommand("version\r\n"))
    }

    @Test
    fun `empty command is rejected`() {
        assertNull(SerialBluetoothClient.frameCommand(""))
        assertNull(SerialBluetoothClient.frameCommand("   \r\n"))
    }

    @Test
    fun `read timeout does not end connection`() {
        val input = TimeoutThenDataInputStream("INFO: connected\r\nversion 0.1\n")
        val lines = mutableListOf<String>()

        readSerialLines(input, shouldContinue = { true }, onLine = lines::add)

        assertEquals(listOf("INFO: connected", "version 0.1"), lines)
    }

    private class TimeoutThenDataInputStream(data: String) : InputStream() {
        private val bytes = data.toByteArray(Charsets.UTF_8)
        private var position = 0
        private var timeoutSent = false

        override fun read(): Int {
            if (!timeoutSent) {
                timeoutSent = true
                throw SerialPortTimeoutException("expected test timeout")
            }
            return if (position < bytes.size) bytes[position++].toInt() and 0xff else -1
        }
    }
}
