package com.neckarhackerapps.bluetoothrelayautomator.repository

import android.content.Context
import android.content.SharedPreferences
import com.neckarhackerapps.bluetoothrelayautomator.model.ExecutionLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class ExecutionLogRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _logs = MutableStateFlow<List<ExecutionLog>>(emptyList())
    val logs: StateFlow<List<ExecutionLog>> = _logs.asStateFlow()

    init {
        loadLogs()
    }

    private fun loadLogs() {
        var jsonStr = prefs.getString(KEY_LOGS, null)
        if (jsonStr == null) {
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            jsonStr = legacyPrefs.getString(KEY_LOGS, null)
            if (jsonStr != null) {
                prefs.edit().putString(KEY_LOGS, jsonStr).apply()
            }
        }
        if (jsonStr == null) return
        val list = mutableListOf<ExecutionLog>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(ExecutionLog.fromJsonObject(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _logs.value = list
    }

    private fun persist() {
        val array = JSONArray()
        // Store at most 100 entries
        val sublist = _logs.value.take(MAX_LOGS)
        for (log in sublist) {
            array.put(log.toJsonObject())
        }
        prefs.edit().putString(KEY_LOGS, array.toString()).apply()
    }

    fun addLog(log: ExecutionLog) {
        val current = _logs.value.toMutableList()
        current.add(0, log) // Newest first
        _logs.value = current.take(MAX_LOGS)
        persist()
    }

    fun clearLogs() {
        _logs.value = emptyList()
        prefs.edit().remove(KEY_LOGS).apply()
    }

    companion object {
        private const val PREFS_NAME = "relay_automator_logs"
        private const val LEGACY_PREFS_NAME = "relais_automator_logs"
        private const val KEY_LOGS = "execution_logs"
        private const val MAX_LOGS = 100
    }
}
