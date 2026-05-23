package com.indianservers.ruhi

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context

class BleRobotManager(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null

    val isConnected: Boolean
        get() = gatt != null

    fun startScan(deviceName: String, onFound: (ScanResult) -> Unit) {
        val scanner = adapter?.bluetoothLeScanner ?: return
        scanner.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.name == deviceName) {
                    scanner.stopScan(this)
                    onFound(result)
                }
            }
        })
    }

    fun connect(result: ScanResult, callback: BluetoothGattCallback) {
        gatt = result.device.connectGatt(null, false, callback)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    fun sendControlCommand(command: String) {
        // GATT characteristics are hardware-specific and will be bound here when the robot board is chosen.
    }
}
