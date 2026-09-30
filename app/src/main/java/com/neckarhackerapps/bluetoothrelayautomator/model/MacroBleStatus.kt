package com.neckarhackerapps.bluetoothrelayautomator.model

enum class MacroBleStatus {
    /** Solid blue: Target device is currently connected */
    CONNECTED,

    /** Pulsing blue: Target device is in range and can be connected */
    IN_RANGE,

    /** Gray with strikethrough: Not connected and target device is out of range */
    NOT_IN_RANGE
}
