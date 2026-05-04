package com.example.eucconnect

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.eucconnect.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onDeviceClick: (BleDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BleDevice>()

    @SuppressLint("NotifyDataSetChanged")
    fun addOrUpdateDevice(device: BleDevice) {
        val existingIndex = devices.indexOfFirst { it.address == device.address }
        if (existingIndex != -1) {
            devices[existingIndex] = device
            notifyItemChanged(existingIndex)
        } else {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        devices.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: BleDevice) {
            binding.tvDeviceName.text = device.name
            binding.tvDeviceAddress.text = device.address
            binding.tvSignalStrength.text = "RSSI: ${device.rssi} dBm (${device.signalStrength()})"

            val isLikelyEuc =
                device.name.contains("InMotion", ignoreCase = true) ||
                        device.name.contains("Inmotion", ignoreCase = true) ||
                        device.name.contains("GotWay", ignoreCase = true) ||
                        device.name.contains("Begode", ignoreCase = true) ||
                        device.name.contains("KingSong", ignoreCase = true) ||
                        device.name.contains("Ninebot", ignoreCase = true) ||
                        device.name.contains("EUC", ignoreCase = true) ||
                        device.name.contains("V5", ignoreCase = true) ||
                        device.name.contains("V8", ignoreCase = true) ||
                        device.name.contains("V10", ignoreCase = true) ||
                        device.name.contains("V11", ignoreCase = true) ||
                        device.name.contains("V12", ignoreCase = true)

            if (isLikelyEuc) {
                binding.root.setCardBackgroundColor(0xFF1B5E20.toInt())
                binding.tvEucBadge.visibility = View.VISIBLE
            } else {
                binding.root.setCardBackgroundColor(0xFF1E1E1E.toInt())
                binding.tvEucBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }
}