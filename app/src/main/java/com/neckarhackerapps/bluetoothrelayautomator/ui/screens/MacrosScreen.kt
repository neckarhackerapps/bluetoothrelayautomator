package com.neckarhackerapps.bluetoothrelayautomator.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neckarhackerapps.bluetoothrelayautomator.R
import com.neckarhackerapps.bluetoothrelayautomator.RelayAutomatorApp
import com.neckarhackerapps.bluetoothrelayautomator.ble.BleConnectionState
import com.neckarhackerapps.bluetoothrelayautomator.ble.BleScanner
import com.neckarhackerapps.bluetoothrelayautomator.engine.MacroExecutionState
import com.neckarhackerapps.bluetoothrelayautomator.model.Macro
import com.neckarhackerapps.bluetoothrelayautomator.model.MacroBleStatus
import com.neckarhackerapps.bluetoothrelayautomator.model.RelayDevice
import com.neckarhackerapps.bluetoothrelayautomator.model.StepType
import com.neckarhackerapps.bluetoothrelayautomator.widget.RelayMacroWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MacrosScreen(modifier: Modifier = Modifier) {
    val app = RelayAutomatorApp.instance
    val macroRepo = app.macroRepository
    val macroExecutor = app.macroExecutor
    val bleScanner = app.bleScanner
    val bleManager = app.bleManager
    val relayRepo = app.relayRepository
    val coroutineScope = rememberCoroutineScope()

    val macros by macroRepo.macros.collectAsState()
    val executionState by macroExecutor.executionState.collectAsState()
    val connectionState by bleManager.connectionState.collectAsState()
    val savedDevices by relayRepo.savedDevices.collectAsState()
    val scanTick by bleScanner.scanTick.collectAsState()
    val discoveredDevices by bleScanner.discoveredDevices.collectAsState()

    val isAnyMacroRunning = executionState is MacroExecutionState.Running

    // Start continuous background BLE scan when MacrosScreen is active, but pause while a macro is executing
    DisposableEffect(isAnyMacroRunning) {
        if (!isAnyMacroRunning) {
            bleScanner.startContinuousScan()
        } else {
            bleScanner.stopScan()
        }
        onDispose {
            bleScanner.stopScan()
        }
    }

    var editingMacro by remember { mutableStateOf<Macro?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var macroToDelete by remember { mutableStateOf<Macro?>(null) }
    var statusDialogData by remember { mutableStateOf<Pair<MacroBleStatus, String>?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isCreatingNew = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.macros_create_new))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (macros.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.macros_no_macros),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(macros, key = { it.id }) { macro ->
                        val isThisMacroRunning = (executionState as? MacroExecutionState.Running)?.macroId == macro.id
                        val isThisMacroSuccess = (executionState as? MacroExecutionState.Success)?.macroId == macro.id
                        val isThisMacroFailure = (executionState as? MacroExecutionState.Failure)?.macroId == macro.id

                        val (bleStatus, targetName) = resolveMacroBleStatus(
                            macro = macro,
                            connectionState = connectionState,
                            connectedAddress = bleManager.connectedDeviceAddress,
                            connectedName = bleManager.connectedDeviceName,
                            savedDevices = savedDevices,
                            bleScanner = bleScanner,
                            tick = scanTick,
                            isRunningThisMacro = isThisMacroRunning
                        )

                        MacroItemCard(
                            macro = macro,
                            isRunning = isThisMacroRunning,
                            isSuccess = isThisMacroSuccess,
                            isFailure = isThisMacroFailure,
                            executionState = executionState,
                            bleStatus = bleStatus,
                            targetName = targetName,
                            onStatusClick = {
                                statusDialogData = Pair(bleStatus, targetName)
                            },
                            onRun = {
                                coroutineScope.launch {
                                    macroExecutor.execute(macro, source = "App UI")
                                }
                            },
                            onEdit = { editingMacro = macro },
                            onDelete = { macroToDelete = macro }
                        )
                    }
                }
            }
        }
    }

    // Editor Dialog
    if (isCreatingNew || editingMacro != null) {
        MacroEditorDialog(
            initialMacro = editingMacro,
            onDismiss = {
                isCreatingNew = false
                editingMacro = null
            },
            onSave = { saved ->
                macroRepo.saveMacro(saved)
                RelayMacroWidgetProvider.updateAllWidgets(app)
                isCreatingNew = false
                editingMacro = null
            }
        )
    }

    // Delete Confirmation Dialog
    macroToDelete?.let { macro ->
        AlertDialog(
            onDismissRequest = { macroToDelete = null },
            title = { Text(stringResource(R.string.macros_delete_title)) },
            text = { Text(stringResource(R.string.macros_delete_confirm, macro.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        macroRepo.deleteMacro(macro.id)
                        RelayMacroWidgetProvider.updateAllWidgets(app)
                        macroToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { macroToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Bluetooth Status Info Dialog
    statusDialogData?.let { (status, targetName) ->
        val dialogMessage = when (status) {
            MacroBleStatus.CONNECTED -> stringResource(R.string.macro_status_connected_desc, targetName)
            MacroBleStatus.IN_RANGE -> stringResource(R.string.macro_status_in_range_desc, targetName)
            MacroBleStatus.NOT_IN_RANGE -> stringResource(R.string.macro_status_not_in_range_desc, targetName)
        }
        val icon = if (status == MacroBleStatus.NOT_IN_RANGE) Icons.Default.BluetoothDisabled else Icons.Default.Bluetooth
        val iconTint = if (status == MacroBleStatus.NOT_IN_RANGE) Color(0xFF9CA3AF) else Color(0xFF2563EB)

        AlertDialog(
            onDismissRequest = { statusDialogData = null },
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text(stringResource(R.string.macro_status_dialog_title)) },
            text = {
                Text(
                    text = dialogMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { statusDialogData = null }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }
}

@Composable
fun MacroItemCard(
    macro: Macro,
    isRunning: Boolean,
    isSuccess: Boolean,
    isFailure: Boolean,
    executionState: MacroExecutionState,
    bleStatus: MacroBleStatus,
    targetName: String,
    onStatusClick: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            showSuccess = true
            delay(5000L)
            showSuccess = false
        } else {
            showSuccess = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name, Bluetooth Icon & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing animation for IN_RANGE status
                    val infiniteTransition = rememberInfiniteTransition(label = "bt_pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_alpha"
                    )

                    IconButton(
                        onClick = onStatusClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        when (bleStatus) {
                            MacroBleStatus.CONNECTED -> {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = stringResource(R.string.macro_status_dialog_title),
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            MacroBleStatus.IN_RANGE -> {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = stringResource(R.string.macro_status_dialog_title),
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer(alpha = pulseAlpha)
                                )
                            }
                            MacroBleStatus.NOT_IN_RANGE -> {
                                Icon(
                                    imageVector = Icons.Default.BluetoothDisabled,
                                    contentDescription = stringResource(R.string.macro_status_dialog_title),
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = macro.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (macro.description.isNotBlank()) {
                            Text(
                                text = macro.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.btn_edit), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.btn_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Step badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                macro.steps.take(5).forEach { step ->
                    val (badgeBg, label) = when (step.type) {
                        StepType.ON -> Pair(Color(0xFF10B981), "ON")
                        StepType.OFF -> Pair(Color(0xFFEF4444), "OFF")
                        StepType.DELAY -> Pair(Color(0xFF2563EB), "${step.parameter}ms")
                        StepType.CONNECT -> Pair(Color(0xFF8B5CF6), "CONNECT")
                        StepType.DISCONNECT -> Pair(Color(0xFF6B7280), "DISC")
                        StepType.SEND_HEX -> Pair(Color(0xFFF59E0B), "HEX")
                        StepType.SEND_TEXT -> Pair(Color(0xFF3B82F6), "TEXT")
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (macro.steps.size > 5) {
                    Text(
                        text = "+${macro.steps.size - 5}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Execution progress bar
            AnimatedVisibility(visible = isRunning) {
                val runningState = executionState as? MacroExecutionState.Running
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    LinearProgressIndicator(
                        progress = { runningState?.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(runningState?.stepIndex ?: 0) + 1}/${runningState?.totalSteps ?: 0}: ${runningState?.currentStepTitle ?: ""}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Post-execution status
            AnimatedVisibility(visible = showSuccess || isFailure) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showSuccess) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.status_success), color = Color(0xFF10B981), fontSize = 11.sp)
                    } else if (isFailure) {
                        val failState = executionState as? MacroExecutionState.Failure
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(failState?.errorReason ?: stringResource(R.string.status_error), color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Run button
            val isOutOfRange = bleStatus == MacroBleStatus.NOT_IN_RANGE
            val canExecute = macro.steps.isNotEmpty() && !isRunning && !isOutOfRange

            Button(
                onClick = onRun,
                enabled = canExecute,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFFBBF24) else MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.HourglassTop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isRunning -> stringResource(R.string.status_running)
                        isOutOfRange -> stringResource(R.string.macro_btn_not_in_range)
                        else -> stringResource(R.string.btn_run)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun resolveMacroBleStatus(
    macro: Macro,
    connectionState: BleConnectionState,
    connectedAddress: String?,
    connectedName: String?,
    savedDevices: List<RelayDevice>,
    bleScanner: BleScanner,
    tick: Long,
    isRunningThisMacro: Boolean
): Pair<MacroBleStatus, String> {
    val rawTargetIdentifier = macro.resolveTargetIdentifier()
    val targetIdentifier = rawTargetIdentifier
        ?: if (savedDevices.size == 1) savedDevices.first().address else null
    val savedDevice = targetIdentifier?.let { id ->
        savedDevices.firstOrNull {
            it.address.equals(id, ignoreCase = true) ||
            it.displayName.equals(id, ignoreCase = true) ||
            it.alias?.equals(id, ignoreCase = true) == true ||
            it.name?.equals(id, ignoreCase = true) == true
        }
    }
    val targetMac = savedDevice?.address ?: targetIdentifier
    val targetDisplayName = savedDevice?.displayName ?: targetIdentifier ?: "Relay"

    // Active connection data directly from state or BleRelayManager
    val activeConnectedAddress = (connectionState as? BleConnectionState.Connected)?.address
        ?: (connectionState as? BleConnectionState.Connecting)?.address
        ?: connectedAddress
    val activeConnectedName = (connectionState as? BleConnectionState.Connected)?.name
        ?: connectedName

    val isConnected = connectionState.isConnected && (
        isRunningThisMacro ||
        targetMac.isNullOrBlank() ||
        (activeConnectedAddress != null && activeConnectedAddress.equals(targetMac, ignoreCase = true)) ||
        (activeConnectedName != null && (
            activeConnectedName.equals(targetMac, ignoreCase = true) ||
            activeConnectedName.equals(targetDisplayName, ignoreCase = true)
        )) ||
        (targetIdentifier != null && activeConnectedAddress?.equals(targetIdentifier, ignoreCase = true) == true)
    )

    val inRange = if (targetIdentifier != null) {
        bleScanner.isDeviceInRange(targetMac, targetDisplayName)
    } else {
        false
    }

    val status = when {
        isConnected -> MacroBleStatus.CONNECTED
        inRange -> MacroBleStatus.IN_RANGE
        else -> MacroBleStatus.NOT_IN_RANGE
    }

    return Pair(status, targetDisplayName)
}
