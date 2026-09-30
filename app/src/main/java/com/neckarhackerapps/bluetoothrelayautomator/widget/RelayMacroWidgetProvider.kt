package com.neckarhackerapps.bluetoothrelayautomator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.neckarhackerapps.bluetoothrelayautomator.R
import com.neckarhackerapps.bluetoothrelayautomator.RelayAutomatorApp
import com.neckarhackerapps.bluetoothrelayautomator.service.RelayMacroForegroundService

class RelayMacroWidgetProvider : AppWidgetProvider() {

    enum class WidgetState {
        READY,
        RUNNING,
        SUCCESS,
        ERROR
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? RelayAutomatorApp
        val macroRepo = app?.macroRepository

        for (appWidgetId in appWidgetIds) {
            val macroId = WidgetConfigManager.getMacroIdForWidget(context, appWidgetId)
            val macro = if (macroId != null) macroRepo?.getMacroById(macroId) else null
            val macroName = macro?.name ?: context.getString(R.string.widget_name)

            updateWidgetView(context, appWidgetManager, appWidgetId, macroName, context.getString(R.string.status_ready), WidgetState.READY, macroId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_EXECUTE_MACRO) {
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

    companion object {
        const val ACTION_EXECUTE_MACRO = "com.neckarhackerapps.bluetoothrelayautomator.ACTION_EXECUTE_MACRO"

        fun updateWidgetStatus(
            context: Context,
            appWidgetId: Int,
            macroName: String,
            statusText: String,
            state: WidgetState
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val macroId = WidgetConfigManager.getMacroIdForWidget(context, appWidgetId)
            updateWidgetView(context, appWidgetManager, appWidgetId, macroName, statusText, state, macroId)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val standardIds = appWidgetManager.getAppWidgetIds(ComponentName(context, RelayMacroWidgetProvider::class.java))
            val compactIds = appWidgetManager.getAppWidgetIds(ComponentName(context, RelayMacroCompactWidgetProvider::class.java))
            val allIds = standardIds + compactIds

            val app = context.applicationContext as? RelayAutomatorApp
            val macroRepo = app?.macroRepository

            for (id in allIds) {
                val macroId = WidgetConfigManager.getMacroIdForWidget(context, id)
                val macro = if (macroId != null) macroRepo?.getMacroById(macroId) else null
                val macroName = macro?.name ?: context.getString(R.string.widget_name)
                updateWidgetView(context, appWidgetManager, id, macroName, context.getString(R.string.status_ready), WidgetState.READY, macroId)
            }
        }

        private fun updateWidgetView(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            macroName: String,
            statusText: String,
            state: WidgetState,
            macroId: String?
        ) {
            val info = try {
                appWidgetManager.getAppWidgetInfo(appWidgetId)
            } catch (e: Exception) {
                null
            }

            val isCompact = info?.provider?.className?.contains("Compact") == true ||
                    info?.initialLayout == R.layout.widget_relay_macro_1x1

            val layoutId = if (isCompact) R.layout.widget_relay_macro_1x1 else R.layout.widget_relay_macro
            val views = RemoteViews(context.packageName, layoutId)

            val iconRes = when (state) {
                WidgetState.READY -> R.drawable.ic_widget_play
                WidgetState.RUNNING -> R.drawable.ic_widget_hourglass
                WidgetState.SUCCESS -> R.drawable.ic_widget_check
                WidgetState.ERROR -> R.drawable.ic_widget_error
            }

            if (isCompact) {
                views.setTextViewText(R.id.widget_compact_name, macroName)
                views.setTextViewText(R.id.widget_compact_status, statusText)
                views.setImageViewResource(R.id.widget_compact_icon, iconRes)
            } else {
                views.setTextViewText(R.id.widget_macro_name, macroName)
                views.setTextViewText(R.id.widget_status_text, statusText)
                views.setImageViewResource(R.id.widget_action_icon, iconRes)
            }

            if (!macroId.isNullOrBlank()) {
                val targetClass = if (isCompact) {
                    RelayMacroCompactWidgetProvider::class.java
                } else {
                    RelayMacroWidgetProvider::class.java
                }

                val intent = Intent(context, targetClass).apply {
                    action = ACTION_EXECUTE_MACRO
                    putExtra(RelayMacroForegroundService.EXTRA_MACRO_ID, macroId)
                    putExtra(RelayMacroForegroundService.EXTRA_APP_WIDGET_ID, appWidgetId)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    intent,
                    flags
                )

                if (isCompact) {
                    views.setOnClickPendingIntent(R.id.widget_compact_button, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_compact_root, pendingIntent)
                } else {
                    views.setOnClickPendingIntent(R.id.widget_action_button, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
