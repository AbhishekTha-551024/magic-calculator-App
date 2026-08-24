package com.magic.calculator.ui.payment

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magic.calculator.R
import com.magic.calculator.di.appContainer

class PaymentHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: PaymentHistoryAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var txtRecordCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_history)

        val btnBack = findViewById<ImageView>(R.id.btnBackPaymentHistory)
        val btnRefresh = findViewById<ImageView>(R.id.btnRefreshPaymentHistory)
        txtRecordCount = findViewById<TextView>(R.id.txtPaymentRecordCount)
        recycler = findViewById<RecyclerView>(R.id.recyclerPaymentHistory)
        layoutLoading = findViewById<LinearLayout>(R.id.layoutPaymentLoading)
        layoutEmpty = findViewById<LinearLayout>(R.id.layoutPaymentEmpty)

        adapter = PaymentHistoryAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnBack.setOnClickListener { finish() }
        btnRefresh.setOnClickListener { loadPaymentHistory(showLoadingSpinner = true) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        loadPaymentHistory(showLoadingSpinner = true)
    }

    private fun loadPaymentHistory(showLoadingSpinner: Boolean) {
        if (showLoadingSpinner) {
            layoutLoading.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            recycler.visibility = View.GONE
        }

        // Fetch via SubscriptionManager using existing AppContainer DI
        appContainer.subscriptionManager.fetchPaymentHistory(this) { records, error ->
            runOnUiThread {
                layoutLoading.visibility = View.GONE

                if (!error.isNullOrEmpty() && records.isEmpty()) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }

                if (records.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                    recycler.visibility = View.GONE
                    txtRecordCount.text = "0 Orders"
                } else {
                    layoutEmpty.visibility = View.GONE
                    recycler.visibility = View.VISIBLE
                    txtRecordCount.text = "${records.size} ${if (records.size == 1) "Order" else "Orders"}"
                    adapter.submitList(records)
                }
            }
        }
    }
}

