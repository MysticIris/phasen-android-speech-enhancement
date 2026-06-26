package com.example.headphonecontroller.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

// Future integration placeholder:
// This scanner is intentionally kept for later headset connection and control phases.
class BluetoothScanner(
    private val context: Context,
    private val onDeviceFound: (BluetoothDevice, String) -> Unit,
    private val onScanFinished: (Int) -> Unit
) {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val devices = linkedMapOf<String, BluetoothDevice>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device ?: return
                    if (devices.containsKey(device.address)) return

                    devices[device.address] = device
                    onDeviceFound(device, resolveDeviceName(device))
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    onScanFinished(devices.size)
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun startScan(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        devices.clear()
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        return adapter.startDiscovery()
    }

    fun stopScan() {
        bluetoothAdapter?.cancelDiscovery()
    }

    fun resolveDeviceName(device: BluetoothDevice): String {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return if (hasPermission) device.name ?: "Unknown device" else "Unknown device"
    }
}
