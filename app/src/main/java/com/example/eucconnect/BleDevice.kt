package com.example.eucconnect

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int
) {
    fun signalStrength(): String {
        return when {
            rssi >= -50 -> "Excellent"
            rssi >= -70 -> "Good"
            rssi >= -85 -> "Fair"
            else -> "Weak"
        }
    }
}