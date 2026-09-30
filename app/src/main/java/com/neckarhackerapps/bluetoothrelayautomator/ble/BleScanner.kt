package com.neckarhackerapps.bluetoothrelayautomator.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.neckarhackerapps.bluetoothrelayautomator.model.RelayDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleScanner(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<RelayDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<RelayDevice>> = _discoveredDevices.asStateFlow()

    private val _scanTick = MutableStateFlow(0L)
    val scanTick: StateFlow<Long> = _scanTick.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var stopScanRunnable: Runnable? = null
    private var periodicCleanupRunnable: Runnable? = null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun extractDeviceName(device: BluetoothDevice, scanResult: ScanResult?): String? {
        // 1. ScanRecord deviceName
        val recordName = scanResult?.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
        if (recordName != null) return recordName.trim()

        // 2. BluetoothDevice.name
        val deviceName = try {
            device.name?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            null
        }
        if (deviceName != null) return deviceName.trim()

        // 3. Parse raw BLE advertisement packet bytes (Complete Local Name 0x09 or Shortened 0x08)
        val rawBytes = scanResult?.scanRecord?.bytes
        val parsedName = parseNameFromBytes(rawBytes)?.takeIf { it.isNotBlank() }
        if (parsedName != null) return parsedName.trim()

        // 4. Check bonded devices via BluetoothAdapter
        val bondedName = try {
            bluetoothAdapter?.bondedDevices?.firstOrNull {
                it.address.equals(device.address, ignoreCase = true)
            }?.name?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            null
        }
        if (bondedName != null) return bondedName.trim()

        return null
    }

    private fun parseNameFromBytes(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        var index = 0
        var shortName: String? = null
        while (index < bytes.size) {
            val length = bytes[index++].toInt() and 0xFF
            if (length == 0 || index + length > bytes.size) break
            val type = bytes[index].toInt() and 0xFF
            val dataLength = length - 1
            val dataStart = index + 1
            if (dataStart + dataLength <= bytes.size && dataLength > 0) {
                if (type == 0x09) { // Complete Local Name
                    try {
                        val name = String(bytes, dataStart, dataLength, Charsets.UTF_8).trim()
                        if (name.isNotBlank()) return name
                    } catch (e: Exception) { }
                } else if (type == 0x08) { // Shortened Local Name
                    try {
                        val name = String(bytes, dataStart, dataLength, Charsets.UTF_8).trim()
                        if (name.isNotBlank()) shortName = name
                    } catch (e: Exception) { }
                }
            }
            index += length
        }
        return shortName
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val address = device.address ?: return
            val name = extractDeviceName(device, result)

            val current = _discoveredDevices.value.toMutableList()
            val index = current.indexOfFirst { it.address.equals(address, ignoreCase = true) }

            val resolvedName = name ?: if (index >= 0) current[index].name else null

            val relay = RelayDevice(
                address = address,
                name = resolvedName,
                rssi = result.rssi,
                lastSeenTimestamp = System.currentTimeMillis()
            )

            if (index >= 0) {
                current[index] = relay
            } else {
                current.add(relay)
            }
            _discoveredDevices.value = current
            _scanTick.value = System.currentTimeMillis()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(durationMs: Long = 10000) {
        if (!hasPermissions() || !isBluetoothEnabled) return
        if (_isScanning.value) return

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        _isScanning.value = true
        startPeriodicCleanup()
        scanner.startScan(null, settings, scanCallback)

        stopScanRunnable = Runnable {
            stopScan()
        }
        handler.postDelayed(stopScanRunnable!!, durationMs)
    }

    @SuppressLint("MissingPermission")
    fun startContinuousScan(forceRestart: Boolean = false) {
        if (!hasPermissions() || !isBluetoothEnabled) return
        if (_isScanning.value && !forceRestart) return
        if (_isScanning.value) {
            stopScan()
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        _isScanning.value = true
        startPeriodicCleanup()
        try {
            scanner.startScan(null, settings, scanCallback)
            _scanTick.value = System.currentTimeMillis()
        } catch (e: Exception) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        stopScanRunnable?.let { handler.removeCallbacks(it) }
        stopScanRunnable = null
        stopPeriodicCleanup()
        if (!_isScanning.value) return

        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isScanning.value = false
        _scanTick.value = System.currentTimeMillis()
    }

    fun touchDevice(address: String) {
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.address.equals(address, ignoreCase = true) }
        val now = System.currentTimeMillis()
        if (index >= 0) {
            current[index] = current[index].copy(lastSeenTimestamp = now)
            _discoveredDevices.value = current
        }
        _scanTick.value = now
    }

    private fun startPeriodicCleanup() {
        stopPeriodicCleanup()
        periodicCleanupRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                // Remove devices that have not been seen for more than 3.5 seconds
                val filtered = _discoveredDevices.value.filter { now - it.lastSeenTimestamp <= 3500L }
                if (filtered.size != _discoveredDevices.value.size) {
                    _discoveredDevices.value = filtered
                }
                _scanTick.value = now
                handler.postDelayed(this, 1000L) // Refresh UI every second
            }
        }
        handler.postDelayed(periodicCleanupRunnable!!, 1000L)
    }

    private fun stopPeriodicCleanup() {
        periodicCleanupRunnable?.let { handler.removeCallbacks(it) }
        periodicCleanupRunnable = null
    }

    fun isDeviceInRange(targetAddress: String?, targetName: String? = null, maxAgeMs: Long = 2500L): Boolean {
        if (targetAddress.isNullOrBlank() && targetName.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        return _discoveredDevices.value.any { device ->
            val matchAddress = !targetAddress.isNullOrBlank() && device.address.equals(targetAddress, ignoreCase = true)
            val matchName = !targetName.isNullOrBlank() && (
                device.name?.equals(targetName, ignoreCase = true) == true ||
                device.displayName.equals(targetName, ignoreCase = true)
            )
            (matchAddress || matchName) && (now - device.lastSeenTimestamp <= maxAgeMs)
        }
    }

    fun clearDiscovered() {
        _discoveredDevices.value = emptyList()
        _scanTick.value = System.currentTimeMillis()
    }
}
