package com.example.barterhub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.data.models.FeaturedItem

class FeaturedItemsAdapter(
    private val items: List<FeaturedItem>,
    private val onItemClick: (FeaturedItem) -> Unit
) : RecyclerView.Adapter<FeaturedItemsAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val img: ImageView = itemView.findViewById(com.example.barterhub.R.id.itemImage)
        val title: TextView = itemView.findViewById(com.example.barterhub.R.id.itemTitle)
        val price: TextView = itemView.findViewById(com.example.barterhub.R.id.itemPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.example.barterhub.R.layout.item_featured, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.title.text = item.title

        // ✅ Use displayPrice if available, fallback to numeric price
        holder.price.text =
            if (item.displayPrice.isNotBlank()) {
                item.displayPrice
            } else if (item.price > 0) {
                "₱${String.format("%,.2f", item.price)}"
            } else {
                "Barter"
            }

        // ✅ imageUrls is STRING → parse first URL safely
        val imageUrl = parseFirstImageUrl(item.imageUrls)

        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .centerCrop()
            .into(holder.img)

        // ✅ Delegate click to Fragment
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun parseFirstImageUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        // supports: "url1|url2", "url1,url2", or single url
        return raw
            .split("|", ",")
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }
}
