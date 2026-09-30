package com.neckarhackerapps.bluetoothrelayautomator.widget

import android.content.Context
import android.content.SharedPreferences

object WidgetConfigManager {

    private const val PREFS_NAME = "relay_automator_widget_configs"
    private const val LEGACY_PREFS_NAME = "relais_automator_widget_configs"
    private const val PREF_PREFIX_KEY = "appwidget_macro_"

    fun saveMacroForWidget(context: Context, appWidgetId: Int, macroId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_PREFIX_KEY + appWidgetId, macroId).apply()
    }

    fun getMacroIdForWidget(context: Context, appWidgetId: Int): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        if (value != null) return value
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyValue = legacyPrefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        if (legacyValue != null) {
            prefs.edit().putString(PREF_PREFIX_KEY + appWidgetId, legacyValue).apply()
        }
        return legacyValue
    }

    fun deleteWidgetConfig(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(PREF_PREFIX_KEY + appWidgetId).apply()
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        legacyPrefs.edit().remove(PREF_PREFIX_KEY + appWidgetId).apply()
    }
}
