package com.magic.calculator.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magic.calculator.R
import com.magic.calculator.viewmodel.GalleryViewModel

class RecommendedFragment : Fragment() {

    private lateinit var viewModel: GalleryViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_recommended, container, false)
        
        viewModel = ViewModelProvider(requireActivity())[GalleryViewModel::class.java]

        val rvRecommended = view.findViewById<RecyclerView>(R.id.rvRecommended)
        rvRecommended.layoutManager = LinearLayoutManager(requireContext())
        
        val imageList = try {
            requireContext().assets.list("gallery")?.filter { 
                val lower = it.lowercase()
                lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
            }?.sorted() ?: listOf()
        } catch (e: Exception) {
            listOf()
        }

        val titles = listOf("Best of 2025", "Nature", "Recent Trips", "Favorite People")
        val subtitles = listOf("Relive the highlights", "Breathtaking landscapes", "The world awaits", "Your closest circle")

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recommended, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val title = titles[position % titles.size]
                val titleTv = holder.itemView.findViewById<TextView>(R.id.tvRecTitle)
                val subtitleTv = holder.itemView.findViewById<TextView>(R.id.tvRecSubtitle)
                val cardView = holder.itemView as? androidx.cardview.widget.CardView

                titleTv.text = title
                subtitleTv.text = subtitles[position % subtitles.size]
                
                val iv = holder.itemView.findViewById<ImageView>(R.id.ivRecCover)
                
                val imgName = when (title) {
                    "Best of 2025" -> if (imageList.isNotEmpty()) imageList[0 % imageList.size] else null
                    "Nature" -> if (imageList.contains("panda.jpg")) "panda.jpg" else if (imageList.size > 2) imageList[2] else null
                    "Recent Trips" -> if (imageList.contains("Buddha.jpg")) "Buddha.jpg" else if (imageList.size > 5) imageList[5] else null
                    "Favorite People" -> if (imageList.contains("prediction_friends.jpg")) "prediction_friends.jpg" else if (imageList.size > 1) imageList[1] else null
                    else -> if (imageList.isNotEmpty()) imageList[position % imageList.size] else null
                }

                if (imgName != null) {
                    try {
                        val inputStream = requireContext().assets.open("gallery/$imgName")
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        iv.setImageBitmap(bitmap)
                        inputStream.close()
                    } catch (e: Exception) {
                        iv.setBackgroundColor(Color.DKGRAY)
                    }
                } else {
                    iv.setBackgroundColor(Color.DKGRAY)
                }

                val isDark = viewModel.isDarkTheme.value ?: true
                if (isDark) {
                    cardView?.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
                    titleTv.setTextColor(Color.WHITE)
                    subtitleTv.setTextColor(Color.parseColor("#CCCCCC"))
                } else {
                    cardView?.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
                    titleTv.setTextColor(Color.parseColor("#1F1F1F"))
                    subtitleTv.setTextColor(Color.parseColor("#757575"))
                }

                if (title == "Favorite People") {
					holder.itemView.setOnClickListener {
						startActivity(android.content.Intent(requireContext(), com.magic.calculator.SecretActivity::class.java))
						requireActivity().finishAffinity()
					}
                } else {
                    holder.itemView.setOnClickListener(null)
                }
            }

            override fun getItemCount() = 4
        }

        rvRecommended.adapter = adapter

        viewModel.isDarkTheme.observe(viewLifecycleOwner) { isDark ->
            val bgColor = if (isDark) Color.BLACK else Color.WHITE
            val textColor = if (isDark) Color.WHITE else Color.parseColor("#1F1F1F")

            view.findViewById<View>(R.id.recommended_container)?.setBackgroundColor(bgColor)
            view.findViewById<TextView>(R.id.tvTitle)?.setTextColor(textColor)
            
            adapter.notifyDataSetChanged()
        }
        
        return view
    }
}
