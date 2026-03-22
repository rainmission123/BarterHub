package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.barterhub.R
import com.example.barterhub.data.models.FeaturedItem
import com.example.barterhub.databinding.ItemTrendingFullBinding
import com.example.barterhub.utils.TimeUtils.getTimeAgo
import java.util.Locale

class AllTrendingItemsAdapter(
    private val onItemClick: (FeaturedItem) -> Unit,
    private val onWishlistClick: (FeaturedItem) -> Unit,
    private val onLikeClick: (FeaturedItem) -> Unit
) : RecyclerView.Adapter<AllTrendingItemsAdapter.TrendingViewHolder>() {

    private val items = mutableListOf<FeaturedItem>()

    inner class TrendingViewHolder(
        val binding: ItemTrendingFullBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendingViewHolder {
        val binding = ItemTrendingFullBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TrendingViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: TrendingViewHolder, position: Int) {
        val item = items[position]

        Log.d("AllTrendingItems", "Binding item $position: ${item.title}")
        Log.d(
            "AllTrendingItems",
            "itemId=${item.itemId}, displayPrice='${item.displayPrice}', price='${item.price}', ownerName='${item.ownerName}', ownerId='${item.ownerId}'"
        )

        val imageUrl = extractFirstImageUrl(item.imageUrls)

        if (imageUrl.isNotBlank()) {
            holder.binding.itemImage.load(imageUrl) {
                placeholder(R.drawable.ic_launcher_background)
                error(R.drawable.ic_launcher_background)
                crossfade(true)
            }
        } else {
            holder.binding.itemImage.setImageResource(R.drawable.ic_launcher_background)
        }

        // BASIC TEXT
        holder.binding.itemTitle.text = item.title.ifBlank { "Untitled Item" }
        holder.binding.itemDescription.text = item.description.ifBlank { "No description" }
        holder.binding.itemCondition.text = "Condition: ${item.condition.ifBlank { "Not specified" }}"

        // OWNER NAME
        holder.binding.itemOwner.text = "Posted by: ${getSafeOwnerName(item)}"

        // PRICE
        holder.binding.itemPrice.text = getSafePriceText(item)

        // LIKE COUNT
        holder.binding.tvLikeCount.text = item.likeCount.toString()

        // TIME AGO
        holder.binding.tvDaysPosted.text = getTimeAgo(item.timestamp)

        // LOCATION
        holder.binding.itemLocation.text = item.location.ifBlank { "Location not specified" }

        // OWNER IMAGE
        if (item.ownerProfileImage.isNotBlank()) {
            holder.binding.itemOwnerImage.load(item.ownerProfileImage) {
                placeholder(R.drawable.ic_profile)
                error(R.drawable.ic_profile)
                crossfade(true)
            }
        } else {
            holder.binding.itemOwnerImage.setImageResource(R.drawable.ic_profile)
        }

        // CLICK LISTENERS
        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }

        holder.binding.itemWishlist.setOnClickListener {
            onWishlistClick(item)
        }

        holder.binding.likeCountContainer.setOnClickListener {
            onLikeClick(item)
        }
    }

    override fun getItemCount(): Int {
        Log.d("AllTrendingItems", "getItemCount = ${items.size}")
        return items.size
    }

    fun submitList(newItems: List<FeaturedItem>) {
        Log.d("AllTrendingItems", "submitList called with ${newItems.size} items")
        items.clear()
        items.addAll(newItems)
        Log.d("AllTrendingItems", "Items after adding: ${items.size}")
        notifyDataSetChanged()
    }

    private fun extractFirstImageUrl(imageUrls: String): String {
        if (imageUrls.isBlank()) return ""

        return imageUrls
            .split(",")
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: ""
    }

    private fun getSafeOwnerName(item: FeaturedItem): String {
        return when {
            item.ownerName.isNotBlank() -> item.ownerName.trim()
            item.ownerId.isNotBlank() -> "User"
            else -> "Unknown"
        }
    }

    private fun getSafePriceText(item: FeaturedItem): String {
        val displayPrice = item.displayPrice.trim()

        // Priority 1: displayPrice
        if (displayPrice.isNotBlank()) {
            val normalized = displayPrice.lowercase(Locale.ROOT)

            if (
                normalized.contains("barter") ||
                normalized.contains("swap") ||
                normalized == "n/a" ||
                normalized == "none"
            ) {
                return "Barter Only"
            }

            val numericDisplayPrice = displayPrice.toDoubleOrNull()
            if (numericDisplayPrice != null) {
                return if (numericDisplayPrice > 0.0) {
                    formatPhp(numericDisplayPrice)
                } else {
                    "Barter Only"
                }
            }

            return displayPrice
        }

        // Priority 2: raw numeric price
        return if (item.price > 0.0) {
            formatPhp(item.price)
        } else {
            "Barter Only"
        }
    }

    private fun formatPhp(value: Double): String {
        return if (value % 1.0 == 0.0) {
            "PHP${value.toInt()}"
        } else {
            "PHP$value"
        }
    }
}