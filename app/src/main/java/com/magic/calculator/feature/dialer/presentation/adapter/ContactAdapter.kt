package com.magic.calculator.feature.dialer.presentation.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.magic.calculator.databinding.ItemContactBinding
import com.magic.calculator.feature.dialer.domain.model.ContactEntry

class ContactAdapter(
    private val contacts: List<ContactEntry>,
    private val onItemClick: (ContactEntry) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    private val colors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", 
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4", 
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39", 
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722"
    )

    class ViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.binding.tvName.text = contact.name
        holder.binding.tvAvatarLetter.text = if (contact.name.isNotEmpty()) contact.name.take(1).uppercase() else "?"
        
        // Pick a random color based on the name to stay consistent
        val colorHex = colors[Math.abs(contact.name.hashCode()) % colors.size]
        holder.binding.cvAvatar.setCardBackgroundColor(Color.parseColor(colorHex))

        val isDark = com.magic.calculator.core.logic.SettingsManager(holder.itemView.context).isDialerThemeDark
        val primaryColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF212121.toInt()
        holder.binding.tvName.setTextColor(primaryColor)

        holder.binding.root.setOnClickListener {
            onItemClick(contact)
        }
    }

    override fun getItemCount() = contacts.size
}
