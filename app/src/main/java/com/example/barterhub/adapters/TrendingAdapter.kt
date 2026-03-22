package com.example.barterhub.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.barterhub.R
import com.example.barterhub.data.models.FeaturedItem
import com.example.barterhub.databinding.ItemTrendingSliderBinding

class TrendingAdapter(
    private val items: MutableList<FeaturedItem> = mutableListOf(),
    private val onItemClick: ((FeaturedItem) -> Unit)? = null
) : RecyclerView.Adapter<TrendingAdapter.TrendingViewHolder>() {

    inner class TrendingViewHolder(
        val binding: ItemTrendingSliderBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendingViewHolder {
        val binding = ItemTrendingSliderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TrendingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrendingViewHolder, position: Int) {
        val item = items[position]

        holder.binding.tvTrendingTitle.text = item.title.ifBlank { "Untitled Item" }
        holder.binding.tvTrendingCategory.text = item.category.ifBlank { "Unknown Category" }

        val imageUrl = if (item.imageUrls.isNotBlank()) {
            item.imageUrls.split(",").firstOrNull()?.trim() ?: ""
        } else {
            ""
        }

        Log.d("TRENDING_DEBUG", "Item $position: ${item.title}")
        Log.d("TRENDING_DEBUG", "  Raw imageUrls: '${item.imageUrls}'")
        Log.d("TRENDING_DEBUG", "  Extracted URL: '$imageUrl'")

        holder.binding.ivTrendingImage.load(imageUrl) {
            placeholder(R.drawable.ic_launcher_background)
            error(R.drawable.ic_launcher_background)
            crossfade(true)

            listener(
                onSuccess = { request, result ->
                    Log.d("TRENDING_DEBUG", "✅ Image loaded successfully for: ${item.title}")
                },
                onError = { request, error ->
                    Log.e("TRENDING_DEBUG", "❌ Failed to load image for: ${item.title}", error.throwable)
                }
            )
        }

        holder.binding.root.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<FeaturedItem>) {
        Log.d("TRENDING_DEBUG", "submitList called with ${newItems.size} items")
        items.clear()
        items.addAll(newItems)

        newItems.forEachIndexed { index, item ->
            val firstImage = if (item.imageUrls.isNotBlank()) {
                item.imageUrls.split(",").firstOrNull()?.trim() ?: ""
            } else {
                ""
            }
            Log.d("TRENDING_DEBUG", "Item $index: ${item.title}")
            Log.d("TRENDING_DEBUG", "  First image: $firstImage")
        }

        notifyDataSetChanged()
    }
}