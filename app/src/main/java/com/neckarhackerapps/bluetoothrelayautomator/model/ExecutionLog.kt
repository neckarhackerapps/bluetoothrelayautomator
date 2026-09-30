package com.neckarhackerapps.bluetoothrelayautomator.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ExecutionLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val macroName: String,
    val source: String, // "App UI" or "Home Screen Widget"
    val success: Boolean,
    val durationMs: Long,
    val message: String,
    val stepDetails: List<String> = emptyList()
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    fun toJsonObject(): JSONObject {
        val detailsArray = JSONArray()
        for (detail in stepDetails) {
            detailsArray.put(detail)
        }
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("macroName", macroName)
            put("source", source)
            put("success", success)
            put("durationMs", durationMs)
            put("message", message)
            put("stepDetails", detailsArray)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): ExecutionLog {
            val details = mutableListOf<String>()
            val array = json.optJSONArray("stepDetails")
            if (array != null) {
                for (i in 0 until array.length()) {
                    details.add(array.getString(i))
                }
            }
            return ExecutionLog(
                id = json.optString("id", UUID.randomUUID().toString()),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                macroName = json.getString("macroName"),
                source = json.optString("source", "App"),
                success = json.getBoolean("success"),
                durationMs = json.optLong("durationMs", 0),
                message = json.optString("message", ""),
                stepDetails = details
            )
        }
    }
}
