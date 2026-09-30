package com.neckarhackerapps.bluetoothrelayautomator

import com.neckarhackerapps.bluetoothrelayautomator.model.RelayDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayDeviceTest {

    @Test
    fun testDisplayNameResolution() {
        // When alias is present, it should be used
        val deviceWithAlias = RelayDevice(
            address = "AA:BB:CC:DD:EE:FF",
            name = "BT-Relay",
            alias = "Garagentor"
        )
        assertEquals("Garagentor", deviceWithAlias.displayName)

        // When alias is null, name should be used
        val deviceWithNameOnly = RelayDevice(
            address = "AA:BB:CC:DD:EE:FF",
            name = "BT-Relay",
            alias = null
        )
        assertEquals("BT-Relay", deviceWithNameOnly.displayName)

        // When alias and name are null, fallback to address
        val deviceWithAddressOnly = RelayDevice(
            address = "AA:BB:CC:DD:EE:FF",
            name = null,
            alias = null
        )
        assertTrue(deviceWithAddressOnly.displayName.contains("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun testRelayDeviceJsonRoundtrip() {
        val device = RelayDevice(
            address = "11:22:33:44:55:66",
            name = "BLE-SPP",
            alias = "Licht Garten",
            rssi = -65,
            isSaved = true
        )

        val json = device.toJsonObject()
        val deserialized = RelayDevice.fromJsonObject(json)

        assertEquals(device.address, deserialized.address)
        assertEquals(device.name, deserialized.name)
        assertEquals(device.alias, deserialized.alias)
        assertEquals(device.isSaved, deserialized.isSaved)
    }
}
