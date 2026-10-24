package com.magic.calculator.feature.vault.presentation

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.magic.calculator.R
import com.magic.calculator.feature.vault.presentation.viewmodel.GalleryViewModel

class SearchFragment : Fragment() {

    private lateinit var viewModel: GalleryViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_search, container, false)

        viewModel = ViewModelProvider(requireActivity())[GalleryViewModel::class.java]

        val searchContainer = view.findViewById<View>(R.id.search_container)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val cvSearch = view.findViewById<androidx.cardview.widget.CardView>(R.id.cvSearch)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val tvRecentSearches = view.findViewById<TextView>(R.id.tvRecentSearches)

        viewModel.isDarkTheme.observe(viewLifecycleOwner) { isDark ->
            if (isDark) {
                searchContainer?.setBackgroundColor(Color.BLACK)
                tvTitle?.setTextColor(Color.WHITE)
                cvSearch?.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
                etSearch?.setTextColor(Color.WHITE)
                etSearch?.setHintTextColor(Color.parseColor("#757575"))
                tvRecentSearches?.setTextColor(Color.WHITE)
            } else {
                searchContainer?.setBackgroundColor(Color.WHITE)
                tvTitle?.setTextColor(Color.parseColor("#1F1F1F"))
                cvSearch?.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
                etSearch?.setTextColor(Color.parseColor("#1F1F1F"))
                etSearch?.setHintTextColor(Color.parseColor("#757575"))
                tvRecentSearches?.setTextColor(Color.parseColor("#1F1F1F"))
            }
        }

        return view
    }
}
