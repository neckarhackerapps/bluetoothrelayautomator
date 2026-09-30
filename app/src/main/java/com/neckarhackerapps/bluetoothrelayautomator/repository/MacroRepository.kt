package com.neckarhackerapps.bluetoothrelayautomator.repository

import android.content.Context
import android.content.SharedPreferences
import com.neckarhackerapps.bluetoothrelayautomator.R
import com.neckarhackerapps.bluetoothrelayautomator.model.Macro
import com.neckarhackerapps.bluetoothrelayautomator.model.MacroStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class MacroRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    init {
        loadMacros()
        val version = prefs.getInt(KEY_DEFAULTS_VERSION, 0)
        if (_macros.value.isEmpty()) {
            seedDefaultMacros()
            prefs.edit().putInt(KEY_DEFAULTS_VERSION, CURRENT_DEFAULTS_VERSION).apply()
        } else if (version < CURRENT_DEFAULTS_VERSION) {
            upgradeDefaultMacros()
            prefs.edit().putInt(KEY_DEFAULTS_VERSION, CURRENT_DEFAULTS_VERSION).apply()
        }
    }

    private fun loadMacros() {
        var jsonStr = prefs.getString(KEY_MACROS, null)
        if (jsonStr == null) {
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            jsonStr = legacyPrefs.getString(KEY_MACROS, null)
            if (jsonStr != null) {
                prefs.edit().putString(KEY_MACROS, jsonStr).apply()
            }
        }
        if (jsonStr == null) return
        val list = mutableListOf<Macro>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Macro.fromJsonObject(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _macros.value = list
    }

    private fun createDefaultMacros(): List<Macro> {
        return listOf(
            Macro(
                name = context.getString(R.string.default_macro_on_name),
                description = context.getString(R.string.default_macro_on_desc),
                steps = listOf(
                    MacroStep.on()
                ),
                colorHex = "#10B981"
            ),
            Macro(
                name = context.getString(R.string.default_macro_off_name),
                description = context.getString(R.string.default_macro_off_desc),
                steps = listOf(
                    MacroStep.off()
                ),
                colorHex = "#EF4444"
            ),
            Macro(
                name = context.getString(R.string.default_macro_pulse_name),
                description = context.getString(R.string.default_macro_pulse_desc),
                steps = listOf(
                    MacroStep.on(),
                    MacroStep.delay(500),
                    MacroStep.off()
                ),
                colorHex = "#2563EB"
            ),
            Macro(
                name = context.getString(R.string.default_macro_connect_pulse_name),
                description = context.getString(R.string.default_macro_connect_pulse_desc),
                steps = listOf(
                    MacroStep.connect(),
                    MacroStep.on(),
                    MacroStep.delay(500),
                    MacroStep.off(),
                    MacroStep.delay(500),
                    MacroStep.disconnect()
                ),
                colorHex = "#8B5CF6"
            )
        )
    }

    private fun seedDefaultMacros() {
        _macros.value = createDefaultMacros()
        persist()
    }

    private fun upgradeDefaultMacros() {
        val oldNames = setOf(
            "Pulse 500 ms (Toggle)",
            "Impuls 500 ms (Toggle)",
            "Turn Relay ON",
            "Relais Einschalten",
            "Turn Relay OFF",
            "Relais Ausschalten"
        )
        val customMacros = _macros.value.filter { it.name !in oldNames }
        _macros.value = createDefaultMacros() + customMacros
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        for (macro in _macros.value) {
            array.put(macro.toJsonObject())
        }
        prefs.edit().putString(KEY_MACROS, array.toString()).apply()
    }

    fun saveMacro(macro: Macro) {
        val current = _macros.value.toMutableList()
        val index = current.indexOfFirst { it.id == macro.id }
        if (index >= 0) {
            current[index] = macro
        } else {
            current.add(macro)
        }
        _macros.value = current
        persist()
    }

    fun deleteMacro(macroId: String) {
        val current = _macros.value.toMutableList()
        current.removeAll { it.id == macroId }
        _macros.value = current
        persist()
    }

    fun getMacroById(id: String): Macro? {
        return _macros.value.firstOrNull { it.id == id }
    }

    companion object {
        private const val PREFS_NAME = "relay_automator_macros"
        private const val LEGACY_PREFS_NAME = "relais_automator_macros"
        private const val KEY_MACROS = "saved_macros"
        private const val KEY_DEFAULTS_VERSION = "defaults_version"
        private const val CURRENT_DEFAULTS_VERSION = 2
    }
}
