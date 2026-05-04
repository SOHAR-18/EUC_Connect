package com.example.eucconnect

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.eucconnect.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bleManager: BleManager
    private lateinit var deviceAdapter: DeviceAdapter

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startScanning()
        } else {
            Toast.makeText(
                this,
                "Bluetooth permissions are required",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bleManager.isBluetoothEnabled()) {
            checkPermissionsAndScan()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleManager = BleManager(this)
        setupBleCallbacks()

        deviceAdapter = DeviceAdapter { device ->
            onDeviceClicked(device)
        }

        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }

        binding.btnScan.setOnClickListener {
            if (binding.btnScan.text == "Stop Scanning") {
                bleManager.stopScan()
            } else {
                deviceAdapter.clear()
                binding.tvDeviceCount.text = "Devices found: 0"
                checkPermissionsAndScan()
            }
        }

        binding.btnDisconnect.setOnClickListener {
            bleManager.disconnect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.cleanup()
    }

    private fun setupBleCallbacks() {

        bleManager.onDeviceFound = { device ->
            runOnUiThread {
                deviceAdapter.addOrUpdateDevice(device)
                val count = binding.rvDevices.adapter?.itemCount ?: 0
                binding.tvDeviceCount.text = "Devices found: $count"
            }
        }

        bleManager.onConnectionStateChanged = { connected, deviceName ->
            runOnUiThread {
                if (connected) {
                    binding.tvStatus.text = "Connected to $deviceName"
                    binding.tvStatus.setTextColor(0xFF4CAF50.toInt())
                    binding.btnDisconnect.isEnabled = true
                    Toast.makeText(this, "Connected to $deviceName!", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvStatus.text = "Disconnected from $deviceName"
                    binding.tvStatus.setTextColor(0xFFFF5722.toInt())
                    binding.btnDisconnect.isEnabled = false
                    binding.tvServicesTitle.visibility = View.GONE
                    binding.tvServices.visibility = View.GONE
                }
            }
        }

        bleManager.onServicesDiscovered = { services ->
            runOnUiThread {
                displayServices(services)
            }
        }

        bleManager.onError = { error ->
            runOnUiThread {
                binding.tvStatus.text = "Error: $error"
                binding.tvStatus.setTextColor(0xFFFF5722.toInt())
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }

        bleManager.onScanningStateChanged = { scanning ->
            runOnUiThread {
                if (scanning) {
                    binding.btnScan.text = "Stop Scanning"
                    binding.progressScanning.visibility = View.VISIBLE
                    binding.tvStatus.text = "Scanning..."
                    binding.tvStatus.setTextColor(0xFF2196F3.toInt())
                } else {
                    binding.btnScan.text = "Start Scanning"
                    binding.progressScanning.visibility = View.GONE
                    if (binding.tvStatus.text.toString().contains("Scanning")) {
                        binding.tvStatus.text = "Scan complete"
                        binding.tvStatus.setTextColor(0xFFAAAAAA.toInt())
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndScan() {
        if (!bleManager.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
            return
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startScanning()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startScanning() {
        bleManager.startScan()
    }

    private fun onDeviceClicked(device: BleDevice) {
        AlertDialog.Builder(this)
            .setTitle("Connect to ${device.name}?")
            .setMessage(
                "Address: ${device.address}\n" +
                        "Signal: ${device.rssi} dBm (${device.signalStrength()})"
            )
            .setPositiveButton("Connect") { _, _ ->
                binding.tvStatus.text = "Connecting to ${device.name}..."
                binding.tvStatus.setTextColor(0xFFFFC107.toInt())
                bleManager.connect(device.address)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun displayServices(services: List<BluetoothGattService>) {
        binding.tvServicesTitle.visibility = View.VISIBLE
        binding.tvServices.visibility = View.VISIBLE

        val sb = StringBuilder()
        for (service in services) {
            sb.appendLine("Service: ${service.uuid}")
            for (char in service.characteristics) {
                val props = mutableListOf<String>()
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0)
                    props.add("READ")
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
                    props.add("WRITE")
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                    props.add("NOTIFY")
                sb.appendLine("  ${char.uuid} [${props.joinToString(",")}]")
            }
        }
        binding.tvServices.text = sb.toString()
        binding.tvServices.movementMethod = android.text.method.ScrollingMovementMethod()
    }
}