package com.neckarhackerapps.bluetoothrelayautomator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.neckarhackerapps.bluetoothrelayautomator.ble.BleRelayManager
import com.neckarhackerapps.bluetoothrelayautomator.ble.BleScanner
import com.neckarhackerapps.bluetoothrelayautomator.engine.MacroExecutor
import com.neckarhackerapps.bluetoothrelayautomator.repository.ExecutionLogRepository
import com.neckarhackerapps.bluetoothrelayautomator.repository.MacroRepository
import com.neckarhackerapps.bluetoothrelayautomator.repository.RelayRepository

class RelayAutomatorApp : Application() {

    lateinit var relayRepository: RelayRepository
        private set
    lateinit var macroRepository: MacroRepository
        private set
    lateinit var logRepository: ExecutionLogRepository
        private set
    lateinit var bleManager: BleRelayManager
        private set
    lateinit var bleScanner: BleScanner
        private set
    lateinit var macroExecutor: MacroExecutor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannel()

        relayRepository = RelayRepository(this)
        macroRepository = MacroRepository(this)
        logRepository = ExecutionLogRepository(this)
        bleManager = BleRelayManager(this)
        bleScanner = BleScanner(this)
        macroExecutor = MacroExecutor(this, bleManager, relayRepository, logRepository, bleScanner)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "macro_execution_channel"
        lateinit var instance: RelayAutomatorApp
            private set
    }
}
