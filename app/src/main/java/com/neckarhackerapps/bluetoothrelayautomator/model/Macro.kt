package com.neckarhackerapps.bluetoothrelayautomator.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val defaultDevice: String? = null, // MAC address or name of the target device
    val steps: List<MacroStep> = emptyList(),
    val colorHex: String = "#2563EB",
    val createdAt: Long = System.currentTimeMillis()
) {
    val stepCount: Int
        get() = steps.size

    val summary: String
        get() = if (steps.isEmpty()) "-"
        else steps.joinToString(" ➔ ") { it.type.name }

    fun resolveTargetIdentifier(): String? {
        val connectStep = steps.firstOrNull { it.type == StepType.CONNECT && it.parameter.isNotBlank() }
        return connectStep?.parameter?.trim() ?: defaultDevice?.takeIf { it.isNotBlank() }?.trim()
    }

    fun toJsonObject(): JSONObject {
        val stepsArray = JSONArray()
        for (step in steps) {
            stepsArray.put(step.toJsonObject())
        }

        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("defaultDevice", defaultDevice ?: "")
            put("colorHex", colorHex)
            put("createdAt", createdAt)
            put("steps", stepsArray)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): Macro {
            val stepsList = mutableListOf<MacroStep>()
            val stepsArray = json.optJSONArray("steps")
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    val stepObj = stepsArray.getJSONObject(i)
                    stepsList.add(MacroStep.fromJsonObject(stepObj))
                }
            }

            return Macro(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.getString("name"),
                description = json.optString("description", ""),
                defaultDevice = json.optString("defaultDevice").takeIf { it.isNotBlank() },
                steps = stepsList,
                colorHex = json.optString("colorHex", "#2563EB"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
