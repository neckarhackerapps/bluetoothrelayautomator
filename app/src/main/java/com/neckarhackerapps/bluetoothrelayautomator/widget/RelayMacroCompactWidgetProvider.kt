package com.neckarhackerapps.bluetoothrelayautomator.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.neckarhackerapps.bluetoothrelayautomator.R
import com.neckarhackerapps.bluetoothrelayautomator.RelayAutomatorApp
import com.neckarhackerapps.bluetoothrelayautomator.service.RelayMacroForegroundService

class RelayMacroCompactWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? RelayAutomatorApp
        val macroRepo = app?.macroRepository

        for (appWidgetId in appWidgetIds) {
            val macroId = WidgetConfigManager.getMacroIdForWidget(context, appWidgetId)
            val macro = if (macroId != null) macroRepo?.getMacroById(macroId) else null
            val macroName = macro?.name ?: context.getString(R.string.widget_name)

            RelayMacroWidgetProvider.updateWidgetStatus(
                context,
                appWidgetId,
                macroName,
                context.getString(R.string.status_ready),
                RelayMacroWidgetProvider.WidgetState.READY
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == RelayMacroWidgetProvider.ACTION_EXECUTE_MACRO) {
            val macroId = intent.getStringExtra(RelayMacroForegroundService.EXTRA_MACRO_ID)
            val appWidgetId = intent.getIntExtra(RelayMacroForegroundService.EXTRA_APP_WIDGET_ID, -1)

            if (!macroId.isNullOrBlank()) {
                val serviceIntent = Intent(context, RelayMacroForegroundService::class.java).apply {
                    putExtra(RelayMacroForegroundService.EXTRA_MACRO_ID, macroId)
                    putExtra(RelayMacroForegroundService.EXTRA_APP_WIDGET_ID, appWidgetId)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetConfigManager.deleteWidgetConfig(context, appWidgetId)
        }
    }
}
