package com.neckarhackerapps.bluetoothrelayautomator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neckarhackerapps.bluetoothrelayautomator.R
import com.neckarhackerapps.bluetoothrelayautomator.RelayAutomatorApp
import com.neckarhackerapps.bluetoothrelayautomator.engine.MacroExecutionState
import com.neckarhackerapps.bluetoothrelayautomator.model.Macro
import com.neckarhackerapps.bluetoothrelayautomator.model.MacroStep
import com.neckarhackerapps.bluetoothrelayautomator.model.StepType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorDialog(
    initialMacro: Macro?,
    onDismiss: () -> Unit,
    onSave: (Macro) -> Unit
) {
    val app = RelayAutomatorApp.instance
    val relayRepo = app.relayRepository
    val macroExecutor = app.macroExecutor
    val coroutineScope = rememberCoroutineScope()

    val savedDevices by relayRepo.savedDevices.collectAsState()
    val executionState by macroExecutor.executionState.collectAsState()

    var name by remember { mutableStateOf(initialMacro?.name ?: "") }
    var description by remember { mutableStateOf(initialMacro?.description ?: "") }
    var defaultDevice by remember { mutableStateOf(initialMacro?.defaultDevice ?: "") }
    val steps = remember {
        mutableStateListOf<MacroStep>().apply {
            initialMacro?.steps?.let { addAll(it) }
        }
    }

    var showAddStepDialog by remember { mutableStateOf(false) }
    var deviceDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                // Title
                Text(
                    text = if (initialMacro == null) stringResource(R.string.editor_title_create) else stringResource(R.string.editor_title_edit),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.editor_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.editor_desc_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Default target device
                ExposedDropdownMenuBox(
                    expanded = deviceDropdownExpanded,
                    onExpandedChange = { deviceDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentDevice = savedDevices.firstOrNull { it.address == defaultDevice }
                    val displayLabel = currentDevice?.displayName ?: defaultDevice.ifBlank { stringResource(R.string.editor_no_default_device) }

                    OutlinedTextField(
                        value = displayLabel,
                        onValueChange = { defaultDevice = it },
                        label = { Text(stringResource(R.string.editor_default_device)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = savedDevices.isNotEmpty(),
                        singleLine = true
                    )

                    ExposedDropdownMenu(
                        expanded = deviceDropdownExpanded,
                        onDismissRequest = { deviceDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_no_default_device)) },
                            onClick = {
                                defaultDevice = ""
                                deviceDropdownExpanded = false
                            }
                        )
                        savedDevices.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(device.displayName, fontWeight = FontWeight.Bold)
                                        Text(device.address, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                onClick = {
                                    defaultDevice = device.address
                                    deviceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Steps header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.editor_steps_title, steps.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = { showAddStepDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_add_step), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Steps list
                if (steps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.editor_no_steps),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(steps) { index, step ->
                            StepItemCard(
                                index = index,
                                totalCount = steps.size,
                                step = step,
                                onMoveUp = {
                                    if (index > 0) {
                                        val item = steps.removeAt(index)
                                        steps.add(index - 1, item)
                                    }
                                },
                                onMoveDown = {
                                    if (index < steps.size - 1) {
                                        val item = steps.removeAt(index)
                                        steps.add(index + 1, item)
                                    }
                                },
                                onDelete = {
                                    steps.removeAt(index)
                                }
                            )
                        }
                    }
                }

                // Test execution feedback
                if (executionState is MacroExecutionState.Running) {
                    val running = executionState as MacroExecutionState.Running
                    Text(
                        text = "Test: ${running.currentStepTitle} (${running.stepIndex + 1}/${running.totalSteps})",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer buttons (test run, cancel, save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val tempMacro = Macro(
                                id = initialMacro?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.ifBlank { "Test Macro" },
                                description = description,
                                defaultDevice = defaultDevice.ifBlank { null },
                                steps = steps.toList()
                            )
                            coroutineScope.launch {
                                macroExecutor.execute(tempMacro, source = "Macro Editor")
                            }
                        },
                        enabled = steps.isNotEmpty() && !executionState.isRunning,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_test_run), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.btn_cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val savedMacro = Macro(
                                    id = initialMacro?.id ?: java.util.UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    description = description.trim(),
                                    defaultDevice = defaultDevice.ifBlank { null },
                                    steps = steps.toList()
                                )
                                onSave(savedMacro)
                            }
                        },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_save))
                    }
                }
            }
        }
    }

    if (showAddStepDialog) {
        AddStepDialog(
            savedDevices = savedDevices,
            onDismiss = { showAddStepDialog = false },
            onAddStep = { newStep ->
                steps.add(newStep)
                showAddStepDialog = false
            }
        )
    }
}

@Composable
fun StepItemCard(
    index: Int,
    totalCount: Int,
    step: MacroStep,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val stepColor = when (step.type) {
        StepType.ON -> Color(0xFF10B981)
        StepType.OFF -> Color(0xFFEF4444)
        StepType.DELAY -> Color(0xFF2563EB)
        StepType.CONNECT -> Color(0xFF8B5CF6)
        StepType.DISCONNECT -> Color(0xFF6B7280)
        StepType.SEND_HEX -> Color(0xFFF59E0B)
        StepType.SEND_TEXT -> Color(0xFF3B82F6)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(stepColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = step.displaySubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Move buttons
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
            }

            IconButton(
                onClick = onMoveDown,
                enabled = index < totalCount - 1,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.btn_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStepDialog(
    savedDevices: List<com.neckarhackerapps.bluetoothrelayautomator.model.RelayDevice>,
    onDismiss: () -> Unit,
    onAddStep: (MacroStep) -> Unit
) {
    var selectedType by remember { mutableStateOf(StepType.ON) }
    var parameter by remember { mutableStateOf("") }
    var deviceDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_step_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.editor_step_type_label), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedType == StepType.ON,
                        onClick = { selectedType = StepType.ON },
                        label = { Text("ON", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedType == StepType.OFF,
                        onClick = { selectedType = StepType.OFF },
                        label = { Text("OFF", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedType == StepType.DELAY,
                        onClick = {
                            selectedType = StepType.DELAY
                            if (parameter.isBlank()) parameter = "500"
                        },
                        label = { Text("DELAY", fontSize = 11.sp) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedType == StepType.CONNECT,
                        onClick = {
                            selectedType = StepType.CONNECT
                            if (parameter.isBlank() && savedDevices.isNotEmpty()) {
                                parameter = savedDevices.first().address
                            }
                        },
                        label = { Text("CONNECT", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedType == StepType.DISCONNECT,
                        onClick = { selectedType = StepType.DISCONNECT },
                        label = { Text("DISCONNECT", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedType == StepType.SEND_HEX,
                        onClick = {
                            selectedType = StepType.SEND_HEX
                            if (parameter.isBlank()) parameter = "A0 01 01 A2"
                        },
                        label = { Text("HEX", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedType) {
                    StepType.ON -> {
                        Text(stringResource(R.string.editor_step_on_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    StepType.OFF -> {
                        Text(stringResource(R.string.editor_step_off_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    StepType.DELAY -> {
                        OutlinedTextField(
                            value = parameter,
                            onValueChange = { parameter = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.editor_delay_label)) },
                            placeholder = { Text("500") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    StepType.CONNECT -> {
                        ExposedDropdownMenuBox(
                            expanded = deviceDropdownExpanded,
                            onExpandedChange = { deviceDropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = parameter,
                                onValueChange = { parameter = it },
                                label = { Text(stringResource(R.string.editor_target_device_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                singleLine = true
                            )

                            ExposedDropdownMenu(
                                expanded = deviceDropdownExpanded,
                                onDismissRequest = { deviceDropdownExpanded = false }
                            ) {
                                savedDevices.forEach { device ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(device.displayName, fontWeight = FontWeight.Bold)
                                                Text(device.address, style = MaterialTheme.typography.labelSmall)
                                            }
                                        },
                                        onClick = {
                                            parameter = device.address
                                            deviceDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    StepType.DISCONNECT -> {
                        Text(stringResource(R.string.editor_step_disconnect_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    StepType.SEND_HEX -> {
                        OutlinedTextField(
                            value = parameter,
                            onValueChange = { parameter = it },
                            label = { Text(stringResource(R.string.editor_hex_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    StepType.SEND_TEXT -> {
                        OutlinedTextField(
                            value = parameter,
                            onValueChange = { parameter = it },
                            label = { Text(stringResource(R.string.editor_text_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val step = when (selectedType) {
                        StepType.ON -> MacroStep.on()
                        StepType.OFF -> MacroStep.off()
                        StepType.DELAY -> MacroStep.delay(parameter.toLongOrNull() ?: 500L)
                        StepType.CONNECT -> MacroStep.connect(parameter.trim())
                        StepType.DISCONNECT -> MacroStep.disconnect()
                        StepType.SEND_HEX -> MacroStep.sendHex(parameter.trim())
                        StepType.SEND_TEXT -> MacroStep.sendText(parameter.trim())
                    }
                    onAddStep(step)
                }
            ) {
                Text(stringResource(R.string.btn_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
