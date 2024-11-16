package com.example.picoble3

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.picoble3.ui.theme.PicoBle3Theme
import java.util.*

// Important: This app is designed specifically for Android 11, API 29.
// API 30 might work but 31 and 33 use different functions in parts of the code.

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothGattServer: BluetoothGattServer
    private val sUUID = UUID.fromString("00001201-0000-1000-8000-00805F9B34FB") // generic networking service UUID
    private val cUUID = UUID.fromString("00002A46-0000-1000-8000-00805F9B34FB") // new alert UUID
    private lateinit var characteristic: BluetoothGattCharacteristic
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var connectionStatus by mutableStateOf("Disconnected")
    private var bluetoothLog by mutableStateOf("No logs yet")
    private var connectedDevice: BluetoothDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request necessary permissions at runtime
        requestBluetoothPermissions()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            Log.d("Permissions", "BLUETOOTH permission granted")
        } else {
            Log.d("Permissions", "BLUETOOTH permission denied")
        }
        // Check if the device supports BLE
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e("BLE", "BLE not supported on this device.")
            return
        }

        // Setup Bluetooth and GATT server
        setupBluetooth()

        setContent {
            PicoBle3Theme {
                BluetoothAppUI(connectionStatus, bluetoothLog, ::startAdvertising, ::stopAdvertising, ::setAlarmClosed, ::setAlarmOpen, ::resetAlarm, ::logCharVal, ::requestBluetoothPermissions, ::clearLog)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupBluetooth() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLog = ""

        // Setup GATT Server
        bluetoothLog += "\nopening GATT server..."
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)

        // Create GATT service and characteristic
        val service = BluetoothGattService(sUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        characteristic = BluetoothGattCharacteristic(
            cUUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ
        )


        bluetoothLog += ", adding characteristic..."
        service.addCharacteristic(characteristic)

        bluetoothLog += ", adding service..."
        bluetoothGattServer.addService(service)
        bluetoothLog += " setup complete."
    }

    // GATT Server Callbacks
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                connectionStatus = "Connected to ${device.address}"
                bluetoothLog += "\nDevice connected: ${device.address}"
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null
                connectionStatus = "Disconnected"
                bluetoothLog += "\nDevice disconnected: ${device.address}"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        /*
        if (bluetoothAdapter.isMultipleAdvertisementSupported) {
            bluetoothLog += "\nDevice supports BLE advertising"
        } else {
            bluetoothLog += "\nDevice does not support BLE advertising"
        }
        */

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(sUUID))
            .build()
        //bluetoothLog += "\nAdvertisement Settings: $settings"
        //bluetoothLog += "\nAdvertisement Data: $advertiseData"
        advertiser.startAdvertising(settings, advertiseData, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        advertiser.stopAdvertising(advertiseCallback)
        bluetoothLog += "\nAdvertisement stopped successfully"
    }

    private fun setAlarmClosed() {
        sendNotificationToClient("1")
    }

    private fun setAlarmOpen() {
        sendNotificationToClient("2")
    }

    private fun resetAlarm() {
        sendNotificationToClient("3")
    }

    private fun logCharVal() {
        val valB = characteristic.value

        val valP = characteristic.properties
        bluetoothLog += "\nchar. properties: $valP"
        bluetoothLog += ", char. value is (B) $valB"
    }

    private fun clearLog() {
        bluetoothLog = ""
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            bluetoothLog += "\nAdvertisement started successfully"
        }

        override fun onStartFailure(errorCode: Int) {
            bluetoothLog += "\nAdvertisement failed: $errorCode"
        }
    }

    private fun requestBluetoothPermissions() {
        bluetoothLog += "\nrequesting 4 permissions..."
        if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 1
            )
        }
        else {
            bluetoothLog += "\nPermissions already granted"
            return
        }
        bluetoothLog += if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            "\nPermissions still not granted"
        } else {
            "\nPermissions all granted"
        }
    }

    @SuppressLint("Deprecated", "MissingPermission")
    private fun sendNotificationToClient(value: String) {
        connectedDevice?.let { device ->
            //characteristic.value = value.toByteArray()
            bluetoothLog += "\nupdating char. value to $value"
            //val newval = value.toByteArray()
            //bluetoothLog += "\nupdating char. value to (B) $newval"

            characteristic.setValue(value)

            bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false)
        }
    }
}

@Composable
fun BluetoothAppUI(
    connectionStatus: String,
    log: String,
    onStartAdvertising: () -> Unit,
    onStopAdvertising: () -> Unit,
    onSetAlarmClosed: () -> Unit,
    onSetAlarmOpen: () -> Unit,
    onResetAlarm: () -> Unit,
    onCheckChar: () -> Unit,
    onReqPerms: () -> Unit,
    onClearLog: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("BLE Peripheral: to Pico", style = MaterialTheme.typography.headlineMedium)

            Text("Connection Status: $connectionStatus", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(4.dp))

            Button(onClick = onStartAdvertising) {
                Text("Start Advertising")
            }

            Button(onClick = onStopAdvertising) {
                Text("Stop Advertising")
            }

            Button(onClick = onSetAlarmClosed) {
                Text("Set Alarm for Closed Door") // 1
            }

            Button(onClick = onSetAlarmOpen) {
                Text("Set Alarm for Open Door") // 2
            }

            Button(onClick = onResetAlarm) {
                Text("Reset Alarm") // 3
            }

            Button(onClick = onCheckChar) {
                Text("Check Char. Value") // 3
            }

            Button(onClick = onReqPerms) {
                Text("Grant Permissions")
            }

            Button(onClick = onClearLog) {
                Text("Clear Log")
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text("Logs:", style = MaterialTheme.typography.headlineSmall)
            Text(log)
        }
    }
}