package com.neckarhackerapps.bluetoothrelayautomator.engine

import android.content.Context
import com.neckarhackerapps.bluetoothrelayautomator.ble.BleRelayManager
import com.neckarhackerapps.bluetoothrelayautomator.ble.BleScanner
import com.neckarhackerapps.bluetoothrelayautomator.model.ExecutionLog
import com.neckarhackerapps.bluetoothrelayautomator.model.Macro
import com.neckarhackerapps.bluetoothrelayautomator.model.StepType
import com.neckarhackerapps.bluetoothrelayautomator.repository.ExecutionLogRepository
import com.neckarhackerapps.bluetoothrelayautomator.repository.RelayRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MacroExecutor(
    private val context: Context,
    private val bleManager: BleRelayManager,
    private val relayRepository: RelayRepository,
    private val logRepository: ExecutionLogRepository,
    private val bleScanner: BleScanner
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoResetSuccessJob: Job? = null

    private val mutex = Mutex()
    private val _executionState = MutableStateFlow<MacroExecutionState>(MacroExecutionState.Idle)
    val executionState: StateFlow<MacroExecutionState> = _executionState.asStateFlow()

    suspend fun execute(macro: Macro, source: String = "App UI"): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val stepLogs = mutableListOf<String>()
            var hadError = false
            var errorMessage = ""

            // Pause background scanning during macro execution
            bleScanner.stopScan()

            try {
                val totalSteps = macro.steps.size

                for ((index, step) in macro.steps.withIndex()) {
                    _executionState.value = MacroExecutionState.Running(
                        macroId = macro.id,
                        macroName = macro.name,
                        stepIndex = index,
                        totalSteps = totalSteps,
                        currentStepTitle = step.displayTitle
                    )

                    val stepDesc = "Step ${index + 1}/$totalSteps: ${step.displayTitle}"

                    when (step.type) {
                        StepType.CONNECT -> {
                            val targetIdentifier = step.parameter.ifBlank {
                                macro.defaultDevice?.ifBlank { null }
                                    ?: if (relayRepository.savedDevices.value.size == 1) relayRepository.savedDevices.value.first().address else ""
                            }
                            if (targetIdentifier.isBlank()) {
                                throw IllegalStateException("No target device specified for CONNECT (pair a relay in Devices tab or select one in macro)")
                            }

                            // Attempt to resolve device from saved relays (name -> MAC)
                            val resolvedDevice = relayRepository.findDeviceByIdentifier(targetIdentifier)
                            val macAddress = resolvedDevice?.address ?: targetIdentifier

                            if (!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(macAddress)) {
                                throw IllegalStateException("Device '$targetIdentifier' is neither a known relay nor a valid Bluetooth MAC address.")
                            }

                            val isAlreadyConnected = bleManager.connectionState.value.isConnected &&
                                    (bleManager.connectedDeviceAddress.equals(macAddress, ignoreCase = true) ||
                                     bleManager.connectedDeviceName.equals(targetIdentifier, ignoreCase = true) ||
                                     (resolvedDevice?.displayName?.equals(bleManager.connectedDeviceName, ignoreCase = true) == true))

                            if (isAlreadyConnected) {
                                stepLogs.add("$stepDesc ➔ Already connected (skipped)")
                            } else {
                                _executionState.value = MacroExecutionState.Running(
                                    macroId = macro.id,
                                    macroName = macro.name,
                                    stepIndex = index,
                                    totalSteps = totalSteps,
                                    currentStepTitle = "Connecting to $targetIdentifier (Timeout 5s)..."
                                )

                                bleScanner.stopScan()
                                val connected = bleManager.connect(macAddress, timeoutMs = 5000L)
                                if (!connected) {
                                    throw IllegalStateException("Failed to establish connection to '$targetIdentifier' ($macAddress) within 5 seconds.")
                                }
                                stepLogs.add("$stepDesc ➔ Connected successfully")
                            }
                        }

                        StepType.DISCONNECT -> {
                            val lastAddr = bleManager.connectedDeviceAddress
                            bleManager.disconnect()
                            stepLogs.add("$stepDesc ➔ Disconnected")
                            delay(100)
                            if (!lastAddr.isNullOrBlank()) {
                                bleScanner.touchDevice(lastAddr)
                            }
                            bleScanner.startContinuousScan(forceRestart = true)
                        }

                        StepType.ON -> {
                            // If not connected yet, check if macro.defaultDevice is configured
                            ensureConnected(macro)
                            val sent = bleManager.turnOn()
                            if (!sent) {
                                throw IllegalStateException("Failed to send ON command")
                            }
                            stepLogs.add("$stepDesc ➔ Sent Relay ON")
                        }

                        StepType.OFF -> {
                            ensureConnected(macro)
                            val sent = bleManager.turnOff()
                            if (!sent) {
                                throw IllegalStateException("Failed to send OFF command")
                            }
                            stepLogs.add("$stepDesc ➔ Sent Relay OFF")
                        }

                        StepType.DELAY -> {
                            val ms = step.parameter.toLongOrNull() ?: 500L
                            stepLogs.add("$stepDesc ➔ Waiting ${ms}ms")
                            delay(ms)
                        }

                        StepType.SEND_HEX -> {
                            ensureConnected(macro)
                            val sent = bleManager.sendHex(step.parameter)
                            if (!sent) {
                                throw IllegalStateException("Failed to send Hex '${step.parameter}'")
                            }
                            stepLogs.add("$stepDesc ➔ Sent Hex: ${step.parameter}")
                        }

                        StepType.SEND_TEXT -> {
                            ensureConnected(macro)
                            val sent = bleManager.sendText(step.parameter)
                            if (!sent) {
                                throw IllegalStateException("Failed to send Text '${step.parameter}'")
                            }
                            stepLogs.add("$stepDesc ➔ Sent Text: ${step.parameter}")
                        }
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                _executionState.value = MacroExecutionState.Success(
                    macroId = macro.id,
                    macroName = macro.name,
                    durationMs = duration
                )

                autoResetSuccessJob?.cancel()
                autoResetSuccessJob = scope.launch {
                    delay(5000L)
                    if (_executionState.value is MacroExecutionState.Success) {
                        _executionState.value = MacroExecutionState.Idle
                    }
                }

                logRepository.addLog(
                    ExecutionLog(
                        macroName = macro.name,
                        source = source,
                        success = true,
                        durationMs = duration,
                        message = "Executed successfully in ${duration} ms",
                        stepDetails = stepLogs
                    )
                )

                Result.success(Unit)
            } catch (e: CancellationException) {
                val duration = System.currentTimeMillis() - startTime
                _executionState.value = MacroExecutionState.Failure(
                    macroId = macro.id,
                    macroName = macro.name,
                    errorReason = "Execution cancelled",
                    durationMs = duration
                )
                throw e
            } catch (e: Exception) {
                hadError = true
                errorMessage = e.message ?: "Unknown error"
                val duration = System.currentTimeMillis() - startTime

                _executionState.value = MacroExecutionState.Failure(
                    macroId = macro.id,
                    macroName = macro.name,
                    errorReason = errorMessage,
                    durationMs = duration
                )

                stepLogs.add("ERROR: $errorMessage")

                logRepository.addLog(
                    ExecutionLog(
                        macroName = macro.name,
                        source = source,
                        success = false,
                        durationMs = duration,
                        message = errorMessage,
                        stepDetails = stepLogs
                    )
                )

                Result.failure(e)
            } finally {
                // End of macro: immediately resume background scanning and refresh statuses
                bleScanner.startContinuousScan(forceRestart = true)
            }
        }
    }

    private suspend fun ensureConnected(macro: Macro) {
        if (!bleManager.connectionState.value.isConnected) {
            val target = macro.defaultDevice?.ifBlank { null }
                ?: if (relayRepository.savedDevices.value.size == 1) relayRepository.savedDevices.value.first().address else null
            if (!target.isNullOrBlank()) {
                val resolved = relayRepository.findDeviceByIdentifier(target)
                val address = resolved?.address ?: target
                if (!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address)) {
                    throw IllegalStateException("Target device '$target' is neither a known relay nor a valid Bluetooth MAC address.")
                }
                bleScanner.stopScan()
                val connected = bleManager.connect(address, timeoutMs = 5000L)
                if (!connected) {
                    throw IllegalStateException("Auto-connection to '$target' ($address) could not be established within 5 seconds.")
                }
            } else {
                throw IllegalStateException("No relay connected and no target device configured (pair a relay in Devices tab or select one in macro)")
            }
        }
    }

    fun resetState() {
        _executionState.value = MacroExecutionState.Idle
    }
}
