package com.example.barterhub.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.FeaturedItem

class TrendingItemsAdapter(
    private val items: List<FeaturedItem>,
    private val onItemClick: (FeaturedItem) -> Unit
) : RecyclerView.Adapter<TrendingItemsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivItem: ImageView = view.findViewById(R.id.ivTrendingItem)
        val tvTitle: TextView = view.findViewById(R.id.tvTrendingTitle)
        val tvCategory: TextView = view.findViewById(R.id.tvTrendingCategory)
        val tvViews: TextView = view.findViewById(R.id.tvTrendingViews)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trending, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // Set title
        holder.tvTitle.text = item.title

        // Set category
        holder.tvCategory.text = item.category

        // Set views count - use likeCount instead of viewCount
        val context = holder.itemView.context
        holder.tvViews.text = context.getString(R.string.views_count, item.likeCount)

        // Load image - use the first image from imageUrls
        val imageUrl = if (item.imageUrls.contains(",")) {
            item.imageUrls.split(",").firstOrNull() ?: ""
        } else {
            item.imageUrls
        }

        if (imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .placeholder(R.drawable.placeholder_item)  // Make sure this drawable exists
                .into(holder.ivItem)
        } else {
            // Set a default placeholder if no image
            holder.ivItem.setImageResource(R.drawable.placeholder_item)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}