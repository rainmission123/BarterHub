package com.example.barterhub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.FeaturedItem

class FeaturedItemsAdapter(
    private val items: List<FeaturedItem>
) : RecyclerView.Adapter<FeaturedItemsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.itemTitle)
        val image: ImageView = view.findViewById(R.id.itemImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_featured, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.title.text = item.title

        // ✅ FIX: imageUrls is comma-separated STRING
        val firstImageUrl = item.imageUrls
            .split(",")
            .firstOrNull()
            ?.trim()
            .orEmpty()

        if (firstImageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(firstImageUrl)
                .placeholder(R.drawable.placeholder_item)
                .error(R.drawable.placeholder_item)
                .centerCrop()
                .into(holder.image)
        } else {
            holder.image.setImageResource(R.drawable.placeholder_item)
        }
    }

    override fun getItemCount(): Int = items.size
}
