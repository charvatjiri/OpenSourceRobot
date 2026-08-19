package com.gabot.pcclient

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
}
