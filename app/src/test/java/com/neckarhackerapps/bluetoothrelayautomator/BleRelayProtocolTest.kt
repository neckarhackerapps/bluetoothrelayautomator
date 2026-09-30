package com.neckarhackerapps.bluetoothrelayautomator

import com.neckarhackerapps.bluetoothrelayautomator.ble.BleGattConstants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BleRelayProtocolTest {

    @Test
    fun testPredefinedRelayCommands() {
        // ON command must be exactly A0 01 01 A2
        val expectedOn = byteArrayOf(
            0xA0.toByte(),
            0x01.toByte(),
            0x01.toByte(),
            0xA2.toByte()
        )
        assertArrayEquals(expectedOn, BleGattConstants.CMD_ON)
        assertEquals("A0 01 01 A2", BleGattConstants.CMD_ON_HEX)

        // OFF command must be exactly A0 01 00 A1
        val expectedOff = byteArrayOf(
            0xA0.toByte(),
            0x01.toByte(),
            0x00.toByte(),
            0xA1.toByte()
        )
        assertArrayEquals(expectedOff, BleGattConstants.CMD_OFF)
        assertEquals("A0 01 00 A1", BleGattConstants.CMD_OFF_HEX)
    }

    @Test
    fun testBytesToHex() {
        val bytes = byteArrayOf(0xA0.toByte(), 0x01.toByte(), 0x01.toByte(), 0xA2.toByte())
        val hex = BleGattConstants.bytesToHex(bytes)
        assertEquals("A0 01 01 A2", hex)
    }

    @Test
    fun testHexToBytes() {
        val hex = "A0 01 01 A2"
        val bytes = BleGattConstants.hexToBytes(hex)
        assertArrayEquals(BleGattConstants.CMD_ON, bytes)

        // Test without spaces
        val bytesWithoutSpaces = BleGattConstants.hexToBytes("A00100A1")
        assertArrayEquals(BleGattConstants.CMD_OFF, bytesWithoutSpaces)

        // Test with 0x prefixes and commas
        val bytesWithPrefix = BleGattConstants.hexToBytes("0xA0, 0x01, 0x01, 0xA2")
        assertArrayEquals(BleGattConstants.CMD_ON, bytesWithPrefix)
    }
}
