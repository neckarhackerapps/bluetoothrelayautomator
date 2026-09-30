package com.neckarhackerapps.bluetoothrelayautomator.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.neckarhackerapps.bluetoothrelayautomator.model.TerminalEntry
import com.neckarhackerapps.bluetoothrelayautomator.model.TerminalEntryType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class BleRelayManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _terminalEntries = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalEntries: StateFlow<List<TerminalEntry>> = _terminalEntries.asStateFlow()

    var connectedDeviceAddress: String? = null
        private set
    var connectedDeviceName: String? = null
        private set

    private var connectDeferred: CompletableDeferred<Boolean>? = null
    private var writeDeferred: CompletableDeferred<Boolean>? = null

    fun addTerminalLog(type: TerminalEntryType, bytes: ByteArray? = null, message: String? = null) {
        val entry = TerminalEntry(
            timestamp = System.currentTimeMillis(),
            type = type,
            dataBytes = bytes,
            textMessage = message
        )
        val current = _terminalEntries.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_TERMINAL_ENTRIES) {
            _terminalEntries.value = current.takeLast(MAX_TERMINAL_ENTRIES)
        } else {
            _terminalEntries.value = current
        }
    }

    fun clearTerminal() {
        _terminalEntries.value = emptyList()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val address = gatt?.device?.address ?: ""
            val name = try { gatt?.device?.name } catch (e: SecurityException) { null }

            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDeviceAddress = address
                connectedDeviceName = name
                addTerminalLog(TerminalEntryType.INFO, message = "Connected to $address. Discovering GATT services...")
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = BleConnectionState.Disconnected
                writeCharacteristic = null
                connectedDeviceAddress = null
                connectedDeviceName = null
                addTerminalLog(TerminalEntryType.INFO, message = "Disconnected.")
                connectDeferred?.complete(false)
                closeGatt()
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = BleConnectionState.Error("Connection failed (status: $status)")
                addTerminalLog(TerminalEntryType.ERROR, message = "GATT error: $status")
                connectDeferred?.complete(false)
                closeGatt()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val characteristic = findRelayWriteCharacteristic(gatt)
                if (characteristic != null) {
                    writeCharacteristic = characteristic
                    _connectionState.value = BleConnectionState.Connected(
                        address = gatt.device.address,
                        name = try { gatt.device.name } catch (e: SecurityException) { null }
                    )
                    addTerminalLog(
                        TerminalEntryType.INFO,
                        message = "Ready! Control characteristic found (${characteristic.uuid})"
                    )

                    // Enable notifications/indications for RX if available
                    enableNotificationsIfSupported(gatt, characteristic)

                    connectDeferred?.complete(true)
                } else {
                    addTerminalLog(
                        TerminalEntryType.ERROR,
                        message = "No writable relay characteristic found!"
                    )
                    connectDeferred?.complete(false)
                }
            } else {
                addTerminalLog(TerminalEntryType.ERROR, message = "Service discovery failed: $status")
                connectDeferred?.complete(false)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeDeferred?.complete(true)
            } else {
                addTerminalLog(TerminalEntryType.ERROR, message = "Write error (status $status)")
                writeDeferred?.complete(false)
            }
        }

        @Deprecated("Deprecated for Android 13+")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.value?.let { bytes ->
                handleReceivedBytes(bytes)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleReceivedBytes(value)
        }
    }

    private fun handleReceivedBytes(bytes: ByteArray) {
        addTerminalLog(TerminalEntryType.RX, bytes = bytes)
    }

    private fun findRelayWriteCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        // 1. Search known default relay UUIDs (HM-10 / JDY / LC Technology)
        val hm10Service = gatt.getService(BleGattConstants.UUID_SERVICE_HM10)
        hm10Service?.getCharacteristic(BleGattConstants.UUID_CHAR_HM10)?.let { return it }

        // 2. Search Nordic UART Service (NUS)
        val nusService = gatt.getService(BleGattConstants.UUID_SERVICE_NUS)
        nusService?.getCharacteristic(BleGattConstants.UUID_CHAR_NUS_RX)?.let { return it }

        // 3. Search alternative UART
        val altService = gatt.getService(BleGattConstants.UUID_SERVICE_ALT)
        altService?.getCharacteristic(BleGattConstants.UUID_CHAR_ALT_WRITE)?.let { return it }

        // 4. Universal fallback: First characteristic with WRITE or WRITE_NO_RESPONSE
        for (service in gatt.services) {
            for (char in service.characteristics) {
                val props = char.properties
                if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                ) {
                    return char
                }
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun enableNotificationsIfSupported(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        // Search receive characteristic for notifications/indications (if separate like NUS or same as HM-10)
        var rxChar: BluetoothGattCharacteristic? = characteristic
        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0 &&
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0
        ) {
            // NUS TX char
            val nusService = gatt.getService(BleGattConstants.UUID_SERVICE_NUS)
            rxChar = nusService?.getCharacteristic(BleGattConstants.UUID_CHAR_NUS_TX)
        }

        rxChar?.let { targetChar ->
            if ((targetChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                (targetChar.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            ) {
                gatt.setCharacteristicNotification(targetChar, true)
                val descriptor = targetChar.getDescriptor(BleGattConstants.UUID_CCCD)
                if (descriptor != null) {
                    val descriptorValue = if ((targetChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, descriptorValue)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = descriptorValue
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String, timeoutMs: Long = 5000): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            addTerminalLog(TerminalEntryType.ERROR, message = "Bluetooth is not enabled!")
            _connectionState.value = BleConnectionState.Error("Bluetooth is disabled")
            return false
        }

        // Return true if already connected to the requested device
        if (bluetoothGatt != null &&
            _connectionState.value is BleConnectionState.Connected &&
            (connectedDeviceAddress.equals(address, ignoreCase = true) ||
             connectedDeviceName.equals(address, ignoreCase = true)) &&
            writeCharacteristic != null
        ) {
            return true
        }

        // Disconnect previous connection
        disconnect()
        delay(200)

        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            addTerminalLog(TerminalEntryType.ERROR, message = "Invalid MAC address: '$address'")
            _connectionState.value = BleConnectionState.Error("Invalid MAC address")
            return false
        }

        val device: BluetoothDevice = try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (e: Exception) {
            addTerminalLog(TerminalEntryType.ERROR, message = "Invalid MAC address: $address")
            _connectionState.value = BleConnectionState.Error("Invalid MAC address")
            return false
        }

        _connectionState.value = BleConnectionState.Connecting(address, try { device.name } catch (e: SecurityException) { null })
        addTerminalLog(TerminalEntryType.INFO, message = "Connecting to ${device.address}... (Timeout: ${timeoutMs / 1000}s)")

        connectDeferred = CompletableDeferred()

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }

        val success = withTimeoutOrNull(timeoutMs) {
            connectDeferred?.await()
        } ?: false

        if (!success) {
            addTerminalLog(TerminalEntryType.ERROR, message = "Connection setup timed out (${timeoutMs / 1000}s)!")
            _connectionState.value = BleConnectionState.Error("Connection setup timed out (${timeoutMs / 1000}s)")
            disconnect()
        } else {
            // Important: Brief pause so GATT services and descriptors are ready for write commands
            delay(300)
        }

        return success
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            closeGatt()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _connectionState.value = BleConnectionState.Disconnected
        writeCharacteristic = null
        connectedDeviceAddress = null
        connectedDeviceName = null
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    suspend fun sendBytes(bytes: ByteArray): Boolean {
        val gatt = bluetoothGatt
        val characteristic = writeCharacteristic

        if (gatt == null || characteristic == null || !_connectionState.value.isConnected) {
            addTerminalLog(TerminalEntryType.ERROR, message = "Send failed: No relay connected!")
            return false
        }

        writeDeferred = CompletableDeferred()

        val writeType = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        characteristic.writeType = writeType

        val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeCharacteristic(characteristic, bytes, writeType)
            status == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }

        if (!initiated) {
            addTerminalLog(TerminalEntryType.ERROR, message = "GATT writeCharacteristic call failed")
            return false
        }

        addTerminalLog(TerminalEntryType.TX, bytes = bytes)

        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            delay(80) // Short delay for write with no response
            return true
        }

        // Wait for onCharacteristicWrite confirmation
        withTimeoutOrNull(1000) {
            writeDeferred?.await()
        }
        delay(60) // Pause between commands
        return true
    }

    suspend fun sendHex(hexString: String): Boolean {
        val bytes = BleGattConstants.hexToBytes(hexString)
        if (bytes.isEmpty()) {
            addTerminalLog(TerminalEntryType.ERROR, message = "Invalid hex string: '$hexString'")
            return false
        }
        return sendBytes(bytes)
    }

    suspend fun sendText(text: String): Boolean {
        return sendBytes(text.toByteArray(Charsets.UTF_8))
    }

    suspend fun turnOn(): Boolean {
        return sendBytes(BleGattConstants.CMD_ON)
    }

    suspend fun turnOff(): Boolean {
        return sendBytes(BleGattConstants.CMD_OFF)
    }

    companion object {
        private const val TAG = "BleRelayManager"
        private const val MAX_TERMINAL_ENTRIES = 200
    }
}
