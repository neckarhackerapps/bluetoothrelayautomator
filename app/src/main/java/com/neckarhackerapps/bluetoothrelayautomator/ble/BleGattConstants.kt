package com.neckarhackerapps.bluetoothrelayautomator.ble

import java.util.UUID

object BleGattConstants {

    // Predefined 1-channel BLE Relay Hex Commands
    // Format: [0xA0, Channel(0x01), Command(0x01=ON/0x00=OFF), Checksum]
    val CMD_ON: ByteArray = byteArrayOf(0xA0.toByte(), 0x01, 0x01, 0xA2.toByte())
    val CMD_OFF: ByteArray = byteArrayOf(0xA0.toByte(), 0x01, 0x00, 0xA1.toByte())

    const val CMD_ON_HEX = "A0 01 01 A2"
    const val CMD_OFF_HEX = "A0 01 00 A1"

    // Common BLE UART Services & Characteristics (HM-10, CC2541, JDY-08, Telink, etc.)
    val UUID_SERVICE_HM10: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val UUID_CHAR_HM10: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    // Nordic UART Service (NUS)
    val UUID_SERVICE_NUS: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val UUID_CHAR_NUS_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // Write
    val UUID_CHAR_NUS_TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // Notify

    // Alternative UART Service (JDY-10 / Telink / LCUS)
    val UUID_SERVICE_ALT: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val UUID_CHAR_ALT_WRITE: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    // Standard Client Characteristic Configuration Descriptor (CCCD)
    val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * Converts a ByteArray to a clean uppercase hex string with spaces: "A0 01 01 A2"
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    /**
     * Parses a hex string into a ByteArray.
     * Supports formats: "A0 01 01 A2", "A00101A2", "0xA0 0x01", etc.
     */
    fun hexToBytes(hexString: String): ByteArray {
        val clean = hexString.replace("0x", "")
            .replace("0X", "")
            .replace(" ", "")
            .replace(":", "")
            .replace(",", "")
            .trim()

        if (clean.isEmpty() || clean.length % 2 != 0) {
            return ByteArray(0)
        }

        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
