package com.example.eucconnect

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_TIMEOUT_MS = 30_000L
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    var onDeviceFound: ((BleDevice) -> Unit)? = null
    var onConnectionStateChanged: ((connected: Boolean, deviceName: String?) -> Unit)? = null
    var onServicesDiscovered: ((List<BluetoothGattService>) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onScanningStateChanged: ((Boolean) -> Unit)? = null

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) return

        if (!isBluetoothEnabled()) {
            onError?.invoke("Bluetooth is not enabled")
            return
        }

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        if (bluetoothLeScanner == null) {
            onError?.invoke("BLE Scanner not available")
            return
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            isScanning = true
            onScanningStateChanged?.invoke(true)
            Log.d(TAG, "Scan started")

            handler.postDelayed({
                stopScan()
            }, SCAN_TIMEOUT_MS)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            onError?.invoke("Failed to start scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            onScanningStateChanged?.invoke(false)
            handler.removeCallbacksAndMessages(null)
            Log.d(TAG, "Scan stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scan", e)
        }
    }

    private val scanCallback = object : ScanCallback() {

        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name

            if (deviceName != null && deviceName.isNotBlank()) {
                val bleDevice = BleDevice(
                    name = deviceName,
                    address = device.address,
                    rssi = result.rssi
                )
                onDeviceFound?.invoke(bleDevice)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMessage = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE not supported"
                else -> "Unknown error: $errorCode"
            }
            Log.e(TAG, "Scan failed: $errorMessage")
            isScanning = false
            onScanningStateChanged?.invoke(false)
            onError?.invoke("Scan failed: $errorMessage")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String) {
        stopScan()

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            onError?.invoke("Device not found")
            return
        }

        disconnect()

        Log.d(TAG, "Connecting to ${device.name} ($deviceAddress)")

        bluetoothGatt = device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceName = gatt.device.name ?: "Unknown"

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to $deviceName")
                    Handler(Looper.getMainLooper()).post {
                        onConnectionStateChanged?.invoke(true, deviceName)
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        gatt.discoverServices()
                    }, 500)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from $deviceName")
                    Handler(Looper.getMainLooper()).post {
                        onConnectionStateChanged?.invoke(false, deviceName)
                    }
                    gatt.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services
                Log.d(TAG, "Discovered ${services.size} services")

                for (service in services) {
                    Log.d(TAG, "Service: ${service.uuid}")
                    for (characteristic in service.characteristics) {
                        val properties = mutableListOf<String>()
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0)
                            properties.add("READ")
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
                            properties.add("WRITE")
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                            properties.add("NOTIFY")
                        Log.d(TAG, "  Char: ${characteristic.uuid} [${properties.joinToString(", ")}]")
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    onServicesDiscovered?.invoke(services)
                }
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                Handler(Looper.getMainLooper()).post {
                    onError?.invoke("Service discovery failed")
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            Log.d(TAG, "Char read: ${characteristic.uuid}")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d(TAG, "Char changed: ${characteristic.uuid}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
        }
    }

    fun isConnected(): Boolean {
        return bluetoothGatt != null
    }

    @SuppressLint("MissingPermission")
    fun cleanup() {
        stopScan()
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        bluetoothGatt = null
        handler.removeCallbacksAndMessages(null)
    }
}