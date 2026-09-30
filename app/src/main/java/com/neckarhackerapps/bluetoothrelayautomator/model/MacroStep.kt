package com.neckarhackerapps.bluetoothrelayautomator.model

import com.neckarhackerapps.bluetoothrelayautomator.ble.BleGattConstants
import org.json.JSONObject

enum class StepType {
    CONNECT,
    DISCONNECT,
    ON,
    OFF,
    DELAY,
    SEND_HEX,
    SEND_TEXT
}

data class MacroStep(
    val type: StepType,
    val parameter: String = "" // Target device for CONNECT, duration in ms for DELAY, hex string for SEND_HEX, etc.
) {
    val displayTitle: String
        get() = when (type) {
            StepType.CONNECT -> if (parameter.isBlank()) "CONNECT" else "CONNECT: $parameter"
            StepType.DISCONNECT -> "DISCONNECT"
            StepType.ON -> "ON (A0 01 01 A2)"
            StepType.OFF -> "OFF (A0 01 00 A1)"
            StepType.DELAY -> "DELAY: ${parameter} ms"
            StepType.SEND_HEX -> "HEX: $parameter"
            StepType.SEND_TEXT -> "TEXT: $parameter"
        }

    val displaySubtitle: String
        get() = when (type) {
            StepType.CONNECT -> if (parameter.isBlank()) "CONNECT (Target Relay)" else "CONNECT -> $parameter"
            StepType.DISCONNECT -> "DISCONNECT (BLE)"
            StepType.ON -> "CH1 ON (A0 01 01 A2)"
            StepType.OFF -> "CH1 OFF (A0 01 00 A1)"
            StepType.DELAY -> "WAIT $parameter ms"
            StepType.SEND_HEX -> "RAW HEX: $parameter"
            StepType.SEND_TEXT -> "ASCII: $parameter"
        }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("type", type.name)
            put("parameter", parameter)
        }
    }

    companion object {
        fun connect(targetDevice: String = "") = MacroStep(StepType.CONNECT, targetDevice)
        fun disconnect() = MacroStep(StepType.DISCONNECT)
        fun on() = MacroStep(StepType.ON)
        fun off() = MacroStep(StepType.OFF)
        fun delay(ms: Long) = MacroStep(StepType.DELAY, ms.toString())
        fun sendHex(hex: String) = MacroStep(StepType.SEND_HEX, hex)
        fun sendText(text: String) = MacroStep(StepType.SEND_TEXT, text)

        fun fromJsonObject(json: JSONObject): MacroStep {
            val typeStr = json.getString("type")
            val type = try {
                StepType.valueOf(typeStr)
            } catch (e: Exception) {
                StepType.ON
            }
            val param = json.optString("parameter", "")
            return MacroStep(type, param)
        }
    }
}
