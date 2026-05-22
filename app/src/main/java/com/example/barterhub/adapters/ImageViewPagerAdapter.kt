package com.example.barterhub.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R

class ImageViewPagerAdapter(
    private val imageUrls: List<String>,
    private val onImageClick: (index: Int) -> Unit
) : RecyclerView.Adapter<ImageViewPagerAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageViewItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_viewpager, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]

        if (imageUrl == "default") {
            holder.imageView.setImageResource(R.drawable.bg_home_profile_logo)
        } else {
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .placeholder(R.drawable.bg_home_profile_logo)
                .error(R.drawable.bg_home_profile_logo)
                .centerCrop()
                .into(holder.imageView)
        }

        holder.imageView.setOnClickListener { onImageClick(position) }
    }

    override fun getItemCount(): Int = imageUrls.size
}
