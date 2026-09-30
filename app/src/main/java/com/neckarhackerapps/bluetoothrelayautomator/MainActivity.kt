package com.neckarhackerapps.bluetoothrelayautomator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.neckarhackerapps.bluetoothrelayautomator.theme.BluetoothRelayAutomatorTheme
import com.neckarhackerapps.bluetoothrelayautomator.ui.MainAppScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      BluetoothRelayAutomatorTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainAppScreen()
        }
      }
    }
  }
}
