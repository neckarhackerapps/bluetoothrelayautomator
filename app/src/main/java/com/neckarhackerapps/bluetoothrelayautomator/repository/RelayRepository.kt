package com.neckarhackerapps.bluetoothrelayautomator.repository

import android.content.Context
import android.content.SharedPreferences
import com.neckarhackerapps.bluetoothrelayautomator.model.RelayDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class RelayRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _savedDevices = MutableStateFlow<List<RelayDevice>>(emptyList())
    val savedDevices: StateFlow<List<RelayDevice>> = _savedDevices.asStateFlow()

    init {
        loadSavedDevices()
    }

    private fun loadSavedDevices() {
        var jsonStr = prefs.getString(KEY_SAVED_DEVICES, null)
        if (jsonStr == null) {
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            jsonStr = legacyPrefs.getString(KEY_SAVED_DEVICES, null)
            if (jsonStr != null) {
                prefs.edit().putString(KEY_SAVED_DEVICES, jsonStr).apply()
            }
        }
        if (jsonStr == null) return
        val list = mutableListOf<RelayDevice>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(RelayDevice.fromJsonObject(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _savedDevices.value = list
    }

    private fun persist() {
        val array = JSONArray()
        for (device in _savedDevices.value) {
            array.put(device.toJsonObject())
        }
        prefs.edit().putString(KEY_SAVED_DEVICES, array.toString()).apply()
    }

    fun saveDevice(device: RelayDevice) {
        val current = _savedDevices.value.toMutableList()
        val index = current.indexOfFirst { it.address.equals(device.address, ignoreCase = true) }
        val toSave = device.copy(isSaved = true)
        if (index >= 0) {
            current[index] = toSave
        } else {
            current.add(toSave)
        }
        _savedDevices.value = current
        persist()
    }

    fun removeDevice(address: String) {
        val current = _savedDevices.value.toMutableList()
        current.removeAll { it.address.equals(address, ignoreCase = true) }
        _savedDevices.value = current
        persist()
    }

    fun findDeviceByIdentifier(identifier: String): RelayDevice? {
        val clean = identifier.trim()
        return _savedDevices.value.firstOrNull {
            it.address.equals(clean, ignoreCase = true) ||
            it.displayName.equals(clean, ignoreCase = true) ||
            it.name.equals(clean, ignoreCase = true) ||
            it.alias.equals(clean, ignoreCase = true)
        }
    }

    fun getDeviceByAddress(address: String): RelayDevice? {
        return _savedDevices.value.firstOrNull { it.address.equals(address, ignoreCase = true) }
    }

    companion object {
        private const val PREFS_NAME = "relay_automator_devices"
        private const val LEGACY_PREFS_NAME = "relais_automator_devices"
        private const val KEY_SAVED_DEVICES = "saved_devices"
    }
}
