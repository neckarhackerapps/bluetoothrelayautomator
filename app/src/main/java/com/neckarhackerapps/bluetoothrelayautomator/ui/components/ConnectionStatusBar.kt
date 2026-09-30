package com.neckarhackerapps.bluetoothrelayautomator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neckarhackerapps.bluetoothrelayautomator.R
import com.neckarhackerapps.bluetoothrelayautomator.ble.BleConnectionState

@Composable
fun ConnectionStatusBar(
    connectionState: BleConnectionState,
    connectedDeviceName: String?,
    connectedDeviceAddress: String?,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusColor, statusText, statusIcon) = when (connectionState) {
        is BleConnectionState.Connected -> Triple(
            Color(0xFF10B981),
            stringResource(R.string.conn_connected, connectedDeviceName ?: connectedDeviceAddress ?: ""),
            Icons.Default.BluetoothConnected
        )
        is BleConnectionState.Connecting -> Triple(
            Color(0xFFFBBF24),
            stringResource(R.string.conn_connecting, connectionState.name ?: connectionState.address),
            Icons.Default.Bluetooth
        )
        is BleConnectionState.Disconnecting -> Triple(
            Color(0xFFFBBF24),
            stringResource(R.string.conn_disconnecting),
            Icons.Default.Bluetooth
        )
        is BleConnectionState.Error -> Triple(
            Color(0xFFEF4444),
            stringResource(R.string.conn_error, connectionState.message),
            Icons.Default.BluetoothDisabled
        )
        is BleConnectionState.Disconnected -> Triple(
            Color(0xFF9CA3AF),
            stringResource(R.string.conn_disconnected),
            Icons.Default.BluetoothDisabled
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        if (connectionState is BleConnectionState.Connected) {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onDisconnect,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(stringResource(R.string.btn_disconnect), fontSize = 11.sp)
            }
        }
    }
}
