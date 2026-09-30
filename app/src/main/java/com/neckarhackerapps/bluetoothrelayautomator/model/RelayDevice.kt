package com.neckarhackerapps.bluetoothrelayautomator.model

import org.json.JSONObject

data class RelayDevice(
    val address: String, // MAC address, e.g. "00:1A:7D:DA:71:13"
    val name: String? = null, // Broadcast name from BLE advertisement
    val alias: String? = null, // User-defined alias, e.g. "Garage Door"
    val rssi: Int = 0, // Signal strength in dBm
    val isSaved: Boolean = false, // Whether the device has been saved in the app
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = alias?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: "Unknown BLE Device ($address)"

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("address", address)
            put("name", name ?: "")
            put("alias", alias ?: "")
            put("isSaved", isSaved)
            put("lastSeenTimestamp", lastSeenTimestamp)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): RelayDevice {
            return RelayDevice(
                address = json.getString("address"),
                name = json.optString("name").takeIf { it.isNotBlank() },
                alias = json.optString("alias").takeIf { it.isNotBlank() },
                isSaved = json.optBoolean("isSaved", true),
                lastSeenTimestamp = json.optLong("lastSeenTimestamp", System.currentTimeMillis())
            )
        }
    }
}
