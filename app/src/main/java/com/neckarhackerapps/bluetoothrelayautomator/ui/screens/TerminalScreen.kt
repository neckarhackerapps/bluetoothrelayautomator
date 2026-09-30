package com.neckarhackerapps.bluetoothrelayautomator.ui.screens

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neckarhackerapps.bluetoothrelayautomator.R
import com.neckarhackerapps.bluetoothrelayautomator.RelayAutomatorApp
import com.neckarhackerapps.bluetoothrelayautomator.model.TerminalEntry
import com.neckarhackerapps.bluetoothrelayautomator.model.TerminalEntryType
import com.neckarhackerapps.bluetoothrelayautomator.ui.components.ConnectionStatusBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    val app = RelayAutomatorApp.instance
    val bleManager = app.bleManager
    val relayRepo = app.relayRepository
    val coroutineScope = rememberCoroutineScope()

    val connectionState by bleManager.connectionState.collectAsState()
    val terminalEntries by bleManager.terminalEntries.collectAsState()
    val savedDevices by relayRepo.savedDevices.collectAsState()

    var selectedDeviceAddress by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("A0 01 01 A2") }
    var isHexMode by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()

    // Auto-scroll on new terminal entries
    LaunchedEffect(terminalEntries.size) {
        if (terminalEntries.isNotEmpty()) {
            listState.animateScrollToItem(terminalEntries.size - 1)
        }
    }

    // If devices are saved and none selected yet, select first one
    LaunchedEffect(savedDevices) {
        if (selectedDeviceAddress.isBlank() && savedDevices.isNotEmpty()) {
            selectedDeviceAddress = savedDevices.first().address
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Connection status
        ConnectionStatusBar(
            connectionState = connectionState,
            connectedDeviceName = bleManager.connectedDeviceName,
            connectedDeviceAddress = bleManager.connectedDeviceAddress,
            onDisconnect = { bleManager.disconnect() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Device selection & quick connect
        if (!connectionState.isConnected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    val currentDevice = savedDevices.firstOrNull { it.address == selectedDeviceAddress }
                    val displayLabel = currentDevice?.displayName
                        ?: selectedDeviceAddress.ifBlank { stringResource(R.string.editor_select_device_hint) }

                    OutlinedTextField(
                        value = displayLabel,
                        onValueChange = { selectedDeviceAddress = it },
                        readOnly = savedDevices.isNotEmpty(),
                        label = { Text(stringResource(R.string.term_target_relay)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        if (savedDevices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.term_no_saved_relays)) },
                                onClick = { dropdownExpanded = false }
                            )
                        } else {
                            savedDevices.forEach { device ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(device.displayName, fontWeight = FontWeight.Bold)
                                            Text(device.address, style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                    onClick = {
                                        selectedDeviceAddress = device.address
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (selectedDeviceAddress.isNotBlank()) {
                            coroutineScope.launch {
                                bleManager.connect(selectedDeviceAddress)
                            }
                        }
                    },
                    enabled = selectedDeviceAddress.isNotBlank() && !connectionState.isConnecting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (connectionState.isConnecting) stringResource(R.string.term_connecting) else stringResource(R.string.btn_connect))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Terminal window
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            color = Color(0xFF1E222A)
        ) {
            if (terminalEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.term_no_data),
                        color = Color(0xFF6B7280),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(terminalEntries) { entry ->
                        TerminalEntryRow(entry)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick control buttons (ON, OFF, Pulse, Clear)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        bleManager.turnOn()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.term_btn_on), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        bleManager.turnOff()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.term_btn_off), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        bleManager.turnOn()
                        delay(500)
                        bleManager.turnOff()
                    }
                },
                modifier = Modifier.weight(1.3f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.term_btn_pulse), fontSize = 11.sp)
            }

            IconButton(
                onClick = { bleManager.clearTerminal() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.term_btn_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input row for arbitrary hex and text commands
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = isHexMode,
                onClick = {
                    isHexMode = true
                    if (inputText.isBlank() || !inputText.contains(" ")) {
                        inputText = "A0 01 01 A2"
                    }
                },
                label = { Text(stringResource(R.string.term_mode_hex), fontSize = 11.sp) }
            )

            Spacer(modifier = Modifier.width(4.dp))

            FilterChip(
                selected = !isHexMode,
                onClick = {
                    isHexMode = false
                    if (inputText == "A0 01 01 A2") {
                        inputText = "AT"
                    }
                },
                label = { Text(stringResource(R.string.term_mode_text), fontSize = 11.sp) }
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(if (isHexMode) R.string.term_placeholder_hex else R.string.term_placeholder_text)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        coroutineScope.launch {
                            if (isHexMode) {
                                bleManager.sendHex(inputText)
                            } else {
                                bleManager.sendText(inputText)
                            }
                        }
                    }
                },
                enabled = inputText.isNotBlank() && connectionState.isConnected
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = stringResource(R.string.btn_send),
                    tint = if (connectionState.isConnected) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}

@Composable
fun TerminalEntryRow(entry: TerminalEntry) {
    val (badgeBg, badgeColor, badgeLabel) = when (entry.type) {
        TerminalEntryType.TX -> Triple(Color(0xFF065F46), Color(0xFF6EE7B7), "TX")
        TerminalEntryType.RX -> Triple(Color(0xFF1E40AF), Color(0xFF93C5FD), "RX")
        TerminalEntryType.INFO -> Triple(Color(0xFF374151), Color(0xFFD1D5DB), "INFO")
        TerminalEntryType.ERROR -> Triple(Color(0xFF7F1D1D), Color(0xFFFCA5A5), "ERR")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp
        Text(
            text = entry.formattedTime,
            color = Color(0xFF6B7280),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(badgeBg)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = badgeLabel,
                color = badgeColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Text / Data
        Column(modifier = Modifier.weight(1f)) {
            when (entry.type) {
                TerminalEntryType.TX, TerminalEntryType.RX -> {
                    Text(
                        text = entry.hexString,
                        color = Color(0xFFF3F4F6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (entry.asciiString.isNotBlank()) {
                        Text(
                            text = "\"${entry.asciiString}\"",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                TerminalEntryType.INFO -> {
                    Text(
                        text = entry.textMessage ?: "",
                        color = Color(0xFFD1D5DB),
                        fontSize = 11.sp
                    )
                }
                TerminalEntryType.ERROR -> {
                    Text(
                        text = entry.textMessage ?: "",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
