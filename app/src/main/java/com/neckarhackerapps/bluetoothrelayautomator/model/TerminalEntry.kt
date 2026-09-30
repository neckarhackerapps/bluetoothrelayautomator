package com.neckarhackerapps.bluetoothrelayautomator.model

import com.neckarhackerapps.bluetoothrelayautomator.ble.BleGattConstants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TerminalEntryType {
    TX, // Transmitted data
    RX, // Received data
    INFO, // Status messages (Connected, Disconnected)
    ERROR // Error messages
}

data class TerminalEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val type: TerminalEntryType,
    val dataBytes: ByteArray? = null,
    val textMessage: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val hexString: String
        get() = dataBytes?.let { BleGattConstants.bytesToHex(it) } ?: ""

    val asciiString: String
        get() = dataBytes?.let { bytes ->
            String(bytes.map { b ->
                val c = b.toInt().toChar()
                if (c.code in 32..126) c else '.'
            }.toCharArray())
        } ?: ""

    val displayContent: String
        get() = when (type) {
            TerminalEntryType.INFO -> "ℹ️ ${textMessage ?: ""}"
            TerminalEntryType.ERROR -> "⚠️ ${textMessage ?: ""}"
            TerminalEntryType.TX -> "➔ TX: [${hexString}]  \"${asciiString}\""
            TerminalEntryType.RX -> "⬅ RX: [${hexString}]  \"${asciiString}\""
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TerminalEntry
        if (timestamp != other.timestamp) return false
        if (type != other.type) return false
        if (dataBytes != null) {
            if (other.dataBytes == null) return false
            if (!dataBytes.contentEquals(other.dataBytes)) return false
        } else if (other.dataBytes != null) return false
        if (textMessage != other.textMessage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (dataBytes?.contentHashCode() ?: 0)
        result = 31 * result + (textMessage?.hashCode() ?: 0)
        return result
    }
}
