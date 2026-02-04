package com.example.barterhub.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import androidx.appcompat.app.AppCompatActivity


class FullscreenImageAdapter(
    private val imageUrls: List<String>
) : RecyclerView.Adapter<FullscreenImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.fullscreenImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fullscreen_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        Glide.with(holder.itemView.context)
            .load(imageUrls[position])
            .placeholder(R.drawable.ic_image_placeholder)
            .into(holder.imageView)

        holder.imageView.setOnClickListener {
            (holder.itemView.context as? AppCompatActivity)?.finish()
        }
    }

    override fun getItemCount(): Int = imageUrls.size
}
