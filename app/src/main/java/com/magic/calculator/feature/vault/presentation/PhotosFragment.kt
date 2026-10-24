package com.magic.calculator.feature.vault.presentation

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
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
import com.magic.calculator.feature.vault.presentation.viewmodel.GalleryViewModel

class PhotosFragment : Fragment() {

    private var imageList = listOf<String>()
    private lateinit var viewModel: GalleryViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_photos, container, false)
        
        viewModel = ViewModelProvider(requireActivity())[GalleryViewModel::class.java]

        val rvPhotos = view.findViewById<RecyclerView>(R.id.rvPhotos)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val icSettings = view.findViewById<ImageView>(R.id.icSettings)
        
        icSettings.setOnClickListener {
            val popup = androidx.appcompat.widget.PopupMenu(requireContext(), icSettings)
            popup.menu.add(0, 1, 0, "â˜€ï¸ Day Theme (Light)")
            popup.menu.add(0, 2, 1, "ðŸŒ™ Night Theme (Dark)")
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> viewModel.setTheme(false)
                    2 -> viewModel.setTheme(true)
                }
                true
            }
            popup.show()
        }

        viewModel.isDarkTheme.observe(viewLifecycleOwner) { isDark ->
            val bgColor = if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#1F1F1F")
            val subtextColor = if (isDark) android.graphics.Color.parseColor("#88FFFFFF") else android.graphics.Color.parseColor("#757575")

            view.findViewById<View>(R.id.photos_container)?.setBackgroundColor(bgColor)
            view.findViewById<TextView>(R.id.tvTitle)?.setTextColor(textColor)
            view.findViewById<TextView>(R.id.tvEmpty)?.setTextColor(subtextColor)
            
            val iconTint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#1F1F1F")
            view.findViewById<ImageView>(R.id.icSettings)?.setColorFilter(iconTint)
            view.findViewById<ImageView>(R.id.icSelect)?.setColorFilter(iconTint)
        }

        rvPhotos.layoutManager = GridLayoutManager(requireContext(), 4)
        
        // Load image names from assets/gallery
        try {
            val allFiles = requireContext().assets.list("gallery")
            val settingsManager = com.magic.calculator.core.logic.SettingsManager(requireContext())
            imageList = allFiles?.filter { 
                val lower = it.lowercase()
                val isImage = lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
                isImage && it != "prediction_board.png" && it != "prediction_friends.jpg"
            }?.sorted() ?: listOf()
            
            // Add custom magic prediction boards in active modes
            val finalMutableList = imageList.toMutableList()
            if (settingsManager.activeMagic == "TOXIC_GALLERY") {
                finalMutableList.add(0, "prediction_board.png")
            } else if (settingsManager.activeMagic == "CONTACT_FORCE" || settingsManager.activeMagic == "PHONE_NUMBER_FORCE") {
                finalMutableList.add(0, "prediction_friends.jpg")
            }
            imageList = finalMutableList
            Log.d("GalleryDebug", "Found ${imageList.size} images in assets/gallery")
        } catch (e: Exception) {
            Log.e("GalleryDebug", "Error listing assets", e)
        }
        
        tvEmpty.visibility = if (imageList.isEmpty()) View.VISIBLE else View.GONE

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_photo, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val iv = holder.itemView.findViewById<ImageView>(R.id.ivPhoto)
                val fileName = imageList[position]
                
                try {
                    if (fileName == "prediction_board.png") {
                        val settingsManager = com.magic.calculator.core.logic.SettingsManager(requireContext())
                        val bitmap = com.magic.calculator.core.overlay.PredictionOverlay.getOverlayBitmap(
                            requireContext(), 
                            settingsManager.forcedGalleryTime, 
                            sampleSize = 4
                        )
                        iv.setImageBitmap(bitmap)
                    } else if (fileName == "prediction_friends.jpg") {
                        val settingsManager = com.magic.calculator.core.logic.SettingsManager(requireContext())
                        val bitmap = com.magic.calculator.core.overlay.PredictionFriendsOverlay.getFriendsOverlayBitmap(
                            requireContext(),
                            settingsManager.forcedContactName,
                            settingsManager.forcedContactPhone,
                            sampleSize = 4
                        )
                        if (bitmap != null) {
                            iv.setImageBitmap(bitmap)
                        } else {
                            val inputStream = requireContext().assets.open("gallery/$fileName")
                            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                            val rawBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                            iv.setImageBitmap(rawBitmap)
                            inputStream.close()
                        }
                    } else {
                        val inputStream = requireContext().assets.open("gallery/$fileName")
                        
                        // Downsample to avoid OOM
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 4 // Load 1/4 size
                        }
                        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                        iv.setImageBitmap(bitmap)
                        inputStream.close()
                    }
                } catch (e: Exception) {
                    Log.e("GalleryDebug", "Error loading $fileName", e)
                    iv.setBackgroundColor(android.graphics.Color.parseColor("#000000"))
                }

                holder.itemView.setOnClickListener {
                    val intent = Intent(requireContext(), PhotoViewerActivity::class.java).apply {
                        putExtra("EXTRA_IMAGE_NAME", fileName)
                        putExtra("EXTRA_POSITION", position + 1)
                        putExtra("EXTRA_TOTAL", imageList.size)
                    }
                    startActivity(intent)
                }
            }

            override fun getItemCount() = imageList.size
        }
        
        rvPhotos.adapter = adapter
        adapter.notifyDataSetChanged()
        
        return view
    }
}
