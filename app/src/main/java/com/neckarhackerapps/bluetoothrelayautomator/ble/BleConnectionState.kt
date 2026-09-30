package com.neckarhackerapps.bluetoothrelayautomator.ble

sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    data class Connecting(val address: String, val name: String? = null) : BleConnectionState()
    data class Connected(val address: String, val name: String? = null) : BleConnectionState()
    data class Disconnecting(val address: String) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()

    val isConnected: Boolean
        get() = this is Connected

    val isConnecting: Boolean
        get() = this is Connecting
}
