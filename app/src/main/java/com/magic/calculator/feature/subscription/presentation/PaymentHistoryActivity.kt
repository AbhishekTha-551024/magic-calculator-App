package com.magic.calculator.feature.subscription.presentation

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magic.calculator.R
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.subscription.presentation.state.PaymentHistoryUiState
import com.magic.calculator.feature.subscription.presentation.viewmodel.PaymentHistoryViewModel
import com.magic.calculator.core.ui.util.MagicToast
import kotlinx.coroutines.launch

class PaymentHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: PaymentHistoryAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var txtRecordCount: TextView

    private val viewModel: PaymentHistoryViewModel by viewModels {
        PaymentHistoryViewModel.Factory(appContainer.subscriptionRepository)
    }

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
        btnRefresh.setOnClickListener { viewModel.loadHistory() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        observeViewModel()
        viewModel.loadHistory()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is PaymentHistoryUiState.Loading -> {
                            layoutLoading.visibility = View.VISIBLE
                            layoutEmpty.visibility = View.GONE
                            recycler.visibility = View.GONE
                        }
                        is PaymentHistoryUiState.Success -> {
                            layoutLoading.visibility = View.GONE
                            val records = state.records
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
                        is PaymentHistoryUiState.Error -> {
                            layoutLoading.visibility = View.GONE
                            layoutEmpty.visibility = View.VISIBLE
                            recycler.visibility = View.GONE
                            txtRecordCount.text = "0 Orders"
                            MagicToast.show(this@PaymentHistoryActivity, state.message)
                        }
                    }
                }
            }
        }
    }
}
