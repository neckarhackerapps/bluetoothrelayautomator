package com.neckarhackerapps.bluetoothrelayautomator.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.neckarhackerapps.bluetoothrelayautomator.R
import com.neckarhackerapps.bluetoothrelayautomator.RelayAutomatorApp
import com.neckarhackerapps.bluetoothrelayautomator.widget.RelayMacroWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RelayMacroForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val macroId = intent?.getStringExtra(EXTRA_MACRO_ID)
        val appWidgetId = intent?.getIntExtra(EXTRA_APP_WIDGET_ID, -1) ?: -1

        if (macroId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val app = application as RelayAutomatorApp
        val macro = app.macroRepository.getMacroById(macroId)

        if (macro == null) {
            triggerVibration(isSuccess = false)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification(macro.name, getString(R.string.status_starting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Update widget state to running
        if (appWidgetId != -1) {
            RelayMacroWidgetProvider.updateWidgetStatus(this, appWidgetId, macro.name, getString(R.string.status_running), RelayMacroWidgetProvider.WidgetState.RUNNING)
        }

        serviceScope.launch {
            val result = app.macroExecutor.execute(macro, source = "Homescreen Widget")
            if (result.isSuccess) {
                triggerVibration(isSuccess = true)
                if (appWidgetId != -1) {
                    RelayMacroWidgetProvider.updateWidgetStatus(this@RelayMacroForegroundService, appWidgetId, macro.name, getString(R.string.status_success), RelayMacroWidgetProvider.WidgetState.SUCCESS)
                }
            } else {
                triggerVibration(isSuccess = false)
                val errorMsg = result.exceptionOrNull()?.message ?: getString(R.string.status_error)
                if (appWidgetId != -1) {
                    RelayMacroWidgetProvider.updateWidgetStatus(this@RelayMacroForegroundService, appWidgetId, macro.name, errorMsg, RelayMacroWidgetProvider.WidgetState.ERROR)
                }
            }

            // Brief delay so widget feedback remains visible, then reset to ready
            delay(2500)
            if (appWidgetId != -1) {
                RelayMacroWidgetProvider.updateWidgetStatus(this@RelayMacroForegroundService, appWidgetId, macro.name, getString(R.string.status_ready), RelayMacroWidgetProvider.WidgetState.READY)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun triggerVibration(isSuccess: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (!v.hasVibrator()) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (isSuccess) {
                        // Short haptic pulse
                        v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        // Double vibration on error
                        val timings = longArrayOf(0, 150, 100, 150)
                        val amplitudes = intArrayOf(0, 200, 0, 200)
                        v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (isSuccess) {
                        v.vibrate(120)
                    } else {
                        v.vibrate(longArrayOf(0, 150, 100, 150), -1)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotification(macroName: String, statusText: String): Notification {
        return NotificationCompat.Builder(this, RelayAutomatorApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_running_title))
            .setContentText(getString(R.string.notification_running_content, macroName))
            .setSubText(statusText)
            .setSmallIcon(R.drawable.ic_widget_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val EXTRA_MACRO_ID = "extra_macro_id"
        const val EXTRA_APP_WIDGET_ID = "extra_app_widget_id"
        private const val NOTIFICATION_ID = 1001
    }
}
