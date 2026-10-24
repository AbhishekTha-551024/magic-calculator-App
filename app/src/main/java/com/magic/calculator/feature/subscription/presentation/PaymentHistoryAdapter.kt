package com.magic.calculator.feature.subscription.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.magic.calculator.R
import com.magic.calculator.feature.subscription.domain.model.PaymentRecord
import com.magic.calculator.core.ui.util.MagicToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentHistoryAdapter(
    private var items: List<PaymentRecord> = emptyList()
) : RecyclerView.Adapter<PaymentHistoryAdapter.PaymentViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    fun submitList(newItems: List<PaymentRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_payment_history, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtPlanName: TextView = itemView.findViewById(R.id.txtPaymentPlanName)
        private val txtAmount: TextView = itemView.findViewById(R.id.txtPaymentAmount)
        private val txtStatusChip: TextView = itemView.findViewById(R.id.txtPaymentStatusChip)
        private val txtMethodChip: TextView = itemView.findViewById(R.id.txtPaymentMethodChip)
        private val txtPaymentId: TextView = itemView.findViewById(R.id.txtPaymentId)
        private val txtOrderId: TextView = itemView.findViewById(R.id.txtOrderId)
        private val rowPaymentValidity: View = itemView.findViewById(R.id.rowPaymentValidity)
        private val txtPaymentValidity: TextView = itemView.findViewById(R.id.txtPaymentValidity)
        private val txtDate: TextView = itemView.findViewById(R.id.txtPaymentDate)

        fun bind(record: PaymentRecord) {
            val context = itemView.context

            txtPlanName.text = record.planName.ifEmpty { "CalcMagic Pro" }
            txtAmount.text = "₹${record.amount}"

            txtStatusChip.text = "● ${record.status.ifEmpty { "SUCCESS" }}"
            txtMethodChip.text = record.method.ifEmpty { "UPI" }

            val displayPayId = record.paymentId.ifEmpty { "pay_verified" }
            val displayOrderId = record.orderId.ifEmpty { "order_verified" }

            txtPaymentId.text = displayPayId
            txtOrderId.text = displayOrderId

            if (record.expiryDate.isNotEmpty() && record.expiryDate != "N/A") {
                rowPaymentValidity.visibility = View.VISIBLE
                txtPaymentValidity.text = record.expiryDate
            } else {
                rowPaymentValidity.visibility = View.GONE
            }

            val formattedDate = if (record.startDate.isNotEmpty() && record.startDate != "N/A") {
                record.startDate
            } else if (record.timestamp > 0L) {
                dateFormat.format(Date(record.timestamp))
            } else {
                "Verified Order"
            }
            txtDate.text = formattedDate


            // Copy IDs on click
            itemView.setOnClickListener {
                val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipData = ClipData.newPlainText("Payment ID", displayPayId)
                clipManager?.setPrimaryClip(clipData)
                MagicToast.show(context, "Payment ID copied to clipboard!")
            }
        }
    }
}
