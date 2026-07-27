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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magic.calculator.R
import com.magic.calculator.viewmodel.GalleryViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class AlbumsFragment : Fragment() {

    private lateinit var viewModel: GalleryViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_albums, container, false)
        
        viewModel = ViewModelProvider(requireActivity())[GalleryViewModel::class.java]

        val rvAlbums = view.findViewById<RecyclerView>(R.id.rvAlbums)
        rvAlbums.layoutManager = GridLayoutManager(requireContext(), 2)
        
        // Get gallery images for the cover
        val imageList = try {
            requireContext().assets.list("gallery")?.filter { 
                val lower = it.lowercase()
                lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
            }?.sorted() ?: listOf()
        } catch (e: Exception) {
            listOf()
        }

        val albumNames = listOf("All Photos", "WhatsApp Images", "Camera", "Instagram", "Screenshots")
        
        // Dynamic realistic counts
        val total = imageList.size
        val whatsapp = maxOf(1, (total * 0.3).toInt())
        val camera = maxOf(1, (total * 0.4).toInt())
        val instagram = maxOf(1, (total * 0.15).toInt())
        val screenshots = maxOf(1, total - whatsapp - camera - instagram)
        val albumCounts = listOf(total, whatsapp, camera, instagram, screenshots)

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val nameTv = holder.itemView.findViewById<TextView>(R.id.tvAlbumName)
                val countTv = holder.itemView.findViewById<TextView>(R.id.tvAlbumCount)
                
                nameTv.text = albumNames[position % albumNames.size]
                countTv.text = albumCounts[position % albumCounts.size].toString()
                
                val iv = holder.itemView.findViewById<ImageView>(R.id.ivAlbumCover)
                
                // Set theme colors on items
                val isDark = viewModel.isDarkTheme.value ?: true
                nameTv.setTextColor(if (isDark) Color.WHITE else Color.parseColor("#1F1F1F"))
                countTv.setTextColor(if (isDark) Color.parseColor("#88FFFFFF") else Color.parseColor("#757575"))

                if (imageList.isNotEmpty()) {
                    try {
                        // Cycle premium cover images for all albums
                        val imgName = imageList[position % imageList.size]
                        val inputStream = requireContext().assets.open("gallery/$imgName")
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        iv.setImageBitmap(bitmap)
                        inputStream.close()
                    } catch (e: Exception) {
                        iv.setBackgroundColor(Color.LTGRAY)
                    }
                } else {
                    iv.setBackgroundColor(Color.LTGRAY)
                }

                // Active open listener
                holder.itemView.setOnClickListener {
                    (activity as? GalleryActivity)?.let { activity ->
                        activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.nav_photos
                    }
                }
            }

            override fun getItemCount() = 5
        }

        rvAlbums.adapter = adapter

        viewModel.isDarkTheme.observe(viewLifecycleOwner) { isDark ->
            val bgColor = if (isDark) Color.BLACK else Color.WHITE
            val textColor = if (isDark) Color.WHITE else Color.parseColor("#1F1F1F")

            view.findViewById<View>(R.id.albums_container)?.setBackgroundColor(bgColor)
            view.findViewById<TextView>(R.id.tvTitle)?.setTextColor(textColor)
            
            // Notify adapter to bind new theme colors
            adapter.notifyDataSetChanged()
        }
        
        return view
    }
}
