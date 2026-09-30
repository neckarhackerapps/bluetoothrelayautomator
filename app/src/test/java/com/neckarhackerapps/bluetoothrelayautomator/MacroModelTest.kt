package com.neckarhackerapps.bluetoothrelayautomator

import com.neckarhackerapps.bluetoothrelayautomator.model.Macro
import com.neckarhackerapps.bluetoothrelayautomator.model.MacroStep
import com.neckarhackerapps.bluetoothrelayautomator.model.StepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MacroModelTest {

    @Test
    fun testMacroStepCreation() {
        val onStep = MacroStep.on()
        assertEquals(StepType.ON, onStep.type)
        assertEquals("ON (A0 01 01 A2)", onStep.displayTitle)

        val offStep = MacroStep.off()
        assertEquals(StepType.OFF, offStep.type)
        assertEquals("OFF (A0 01 00 A1)", offStep.displayTitle)

        val delayStep = MacroStep.delay(250)
        assertEquals(StepType.DELAY, delayStep.type)
        assertEquals("250", delayStep.parameter)
        assertEquals("DELAY: 250 ms", delayStep.displayTitle)

        val connectStep = MacroStep.connect("00:11:22:33:44:55")
        assertEquals(StepType.CONNECT, connectStep.type)
        assertEquals("00:11:22:33:44:55", connectStep.parameter)
        assertEquals("CONNECT: 00:11:22:33:44:55", connectStep.displayTitle)

        val disconnectStep = MacroStep.disconnect()
        assertEquals(StepType.DISCONNECT, disconnectStep.type)
        assertEquals("DISCONNECT", disconnectStep.displayTitle)
    }

    @Test
    fun testMacroJsonRoundtrip() {
        val macro = Macro(
            name = "Garagentor Impuls",
            description = "Öffnet das Garagentor mit kurzem Impuls",
            defaultDevice = "00:11:22:33:44:55",
            steps = listOf(
                MacroStep.connect("00:11:22:33:44:55"),
                MacroStep.on(),
                MacroStep.delay(350),
                MacroStep.off(),
                MacroStep.disconnect()
            )
        )

        val json = macro.toJsonObject()
        assertNotNull(json)

        val deserialized = Macro.fromJsonObject(json)
        assertEquals(macro.id, deserialized.id)
        assertEquals(macro.name, deserialized.name)
        assertEquals(macro.description, deserialized.description)
        assertEquals(macro.defaultDevice, deserialized.defaultDevice)
        assertEquals(5, deserialized.steps.size)

        assertEquals(StepType.CONNECT, deserialized.steps[0].type)
        assertEquals("00:11:22:33:44:55", deserialized.steps[0].parameter)
        assertEquals(StepType.ON, deserialized.steps[1].type)
        assertEquals(StepType.DELAY, deserialized.steps[2].type)
        assertEquals("350", deserialized.steps[2].parameter)
        assertEquals(StepType.OFF, deserialized.steps[3].type)
        assertEquals(StepType.DISCONNECT, deserialized.steps[4].type)
    }

    @Test
    fun testMacroResolveTargetIdentifier() {
        // Case 1: CONNECT step with parameter takes precedence
        val macroWithConnect = Macro(
            name = "Test Macro",
            defaultDevice = "AA:BB:CC:DD:EE:FF",
            steps = listOf(
                MacroStep.connect("11:22:33:44:55:66"),
                MacroStep.on()
            )
        )
        assertEquals("11:22:33:44:55:66", macroWithConnect.resolveTargetIdentifier())

        // Case 2: No CONNECT step, falls back to defaultDevice
        val macroWithDefaultOnly = Macro(
            name = "Test Macro Default",
            defaultDevice = "AA:BB:CC:DD:EE:FF",
            steps = listOf(
                MacroStep.on(),
                MacroStep.off()
            )
        )
        assertEquals("AA:BB:CC:DD:EE:FF", macroWithDefaultOnly.resolveTargetIdentifier())

        // Case 3: Neither CONNECT step nor defaultDevice
        val macroWithoutTarget = Macro(
            name = "No Target Macro",
            steps = listOf(MacroStep.on())
        )
        assertEquals(null, macroWithoutTarget.resolveTargetIdentifier())
    }

    @Test
    fun testEmptyConnectStep() {
        val connectStep = MacroStep.connect()
        assertEquals(StepType.CONNECT, connectStep.type)
        assertEquals("", connectStep.parameter)
        assertEquals("CONNECT", connectStep.displayTitle)
        assertEquals("CONNECT (Target Relay)", connectStep.displaySubtitle)
    }

    @Test
    fun testDefaultMacrosStructure() {
        // 1. Switch On / Einschalten
        val switchOn = Macro(
            name = "Switch On",
            steps = listOf(MacroStep.on()),
            colorHex = "#10B981"
        )
        assertEquals(1, switchOn.steps.size)
        assertEquals(StepType.ON, switchOn.steps[0].type)

        // 2. Switch Off / Ausschalten
        val switchOff = Macro(
            name = "Switch Off",
            steps = listOf(MacroStep.off()),
            colorHex = "#EF4444"
        )
        assertEquals(1, switchOff.steps.size)
        assertEquals(StepType.OFF, switchOff.steps[0].type)

        // 3. Short Pulse / Impuls
        val shortPulse = Macro(
            name = "Short Pulse",
            steps = listOf(
                MacroStep.on(),
                MacroStep.delay(500),
                MacroStep.off()
            ),
            colorHex = "#2563EB"
        )
        assertEquals(3, shortPulse.steps.size)
        assertEquals(StepType.ON, shortPulse.steps[0].type)
        assertEquals(StepType.DELAY, shortPulse.steps[1].type)
        assertEquals("500", shortPulse.steps[1].parameter)
        assertEquals(StepType.OFF, shortPulse.steps[2].type)

        // 4. Connect with Short Pulse / Kurzverbindung mit Impuls
        val connectPulse = Macro(
            name = "Connect with Short Pulse",
            steps = listOf(
                MacroStep.connect(),
                MacroStep.on(),
                MacroStep.delay(500),
                MacroStep.off(),
                MacroStep.delay(500),
                MacroStep.disconnect()
            ),
            colorHex = "#8B5CF6"
        )
        assertEquals(6, connectPulse.steps.size)
        assertEquals(StepType.CONNECT, connectPulse.steps[0].type)
        assertEquals(StepType.ON, connectPulse.steps[1].type)
        assertEquals(StepType.DELAY, connectPulse.steps[2].type)
        assertEquals("500", connectPulse.steps[2].parameter)
        assertEquals(StepType.OFF, connectPulse.steps[3].type)
        assertEquals(StepType.DELAY, connectPulse.steps[4].type)
        assertEquals("500", connectPulse.steps[4].parameter)
        assertEquals(StepType.DISCONNECT, connectPulse.steps[5].type)
    }
}
