package com.magic.calculator.feature.dialer.presentation.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.magic.calculator.databinding.ItemCallLogBinding
import com.magic.calculator.feature.dialer.domain.model.CallLogEntry

class CallLogAdapter(
    private val logs: List<CallLogEntry>,
    private val onItemClick: (CallLogEntry) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCallLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCallLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = logs[position]
        holder.binding.tvName.text = if (entry.name.isEmpty()) entry.number else entry.name
        holder.binding.tvNumber.text = entry.number
        holder.binding.tvDate.text = entry.date

        val isDark = com.magic.calculator.core.logic.SettingsManager(holder.itemView.context).isDialerThemeDark
        val primaryColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF212121.toInt()
        val secondaryColor = if (isDark) 0xFFBBBBBB.toInt() else 0xFF757575.toInt()

        holder.binding.tvNumber.setTextColor(secondaryColor)
        holder.binding.tvDate.setTextColor(secondaryColor)
        holder.binding.ivInfo.setColorFilter(secondaryColor)

        // Match color for missed calls
        if (entry.type == "Missed") {
            holder.binding.tvName.setTextColor(0xFFD32F2F.toInt()) // Red for missed
        } else {
            holder.binding.tvName.setTextColor(primaryColor)
        }

        holder.binding.root.setOnClickListener {
            onItemClick(entry)
        }
        holder.binding.ivInfo.setOnClickListener {
            onItemClick(entry)
        }
    }

    override fun getItemCount() = logs.size
}
