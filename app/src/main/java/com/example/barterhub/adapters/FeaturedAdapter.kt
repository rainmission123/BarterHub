package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.FeaturedItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class FeaturedAdapter(
    private val items: MutableList<FeaturedItem> = mutableListOf(),
    private var onThreeDotsClick: ((View, FeaturedItem) -> Unit)? = null
) : RecyclerView.Adapter<FeaturedAdapter.FeaturedViewHolder>() {

    fun setOnThreeDotsClickListener(listener: (View, FeaturedItem) -> Unit) {
        onThreeDotsClick = listener
    }

    class FeaturedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.itemTitle)
        val description: TextView = itemView.findViewById(R.id.itemDescription)
        val image: ImageView = itemView.findViewById(R.id.itemImage)
        val price: TextView = itemView.findViewById(R.id.itemPrice)
        val ownerName: TextView = itemView.findViewById(R.id.itemOwner)
        val ownerImage: ImageView = itemView.findViewById(R.id.itemOwnerImage)
        val itemWishlistBottom: ImageView = itemView.findViewById(R.id.itemWishlistBottom)
        val btnThreeDots: ImageButton = itemView.findViewById(R.id.btnThreeDots)
        val daysPosted: TextView = itemView.findViewById(R.id.tvDaysPosted)
        val likeContainer: LinearLayout = itemView.findViewById(R.id.likeCountContainer)
        val likeCountText: TextView = itemView.findViewById(R.id.tvLikeCount)
        val itemCondition: TextView = itemView.findViewById(R.id.itemCondition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeaturedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_post, parent, false)
        return FeaturedViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: FeaturedViewHolder, position: Int) {

        if (items.isEmpty()) return

        val safeIndex = position % items.size
        val item = items[safeIndex]

        try {
            Log.d("AdapterBinding", "Binding item at pos=$position safeIndex=$safeIndex id=${item.itemId}")

            holder.title.text = ""
            holder.description.text = ""
            holder.itemCondition.text = ""
            holder.price.text = ""
            holder.ownerName.text = ""
            holder.daysPosted.text = ""
            holder.image.setImageDrawable(null)
            holder.ownerImage.setImageDrawable(null)

            // 🔹 Days Posted
            holder.daysPosted.text =
                if (item.timestamp == 0L) "New"
                else calculateDaysPosted(item.timestamp)

            // 🔹 Like count
            displayLikeCount(holder, item)

            // 🔹 Title
            holder.title.text = item.title?.takeIf { it.isNotBlank() } ?: "No Title"

            // 🔹 Description
            val finalDescription = item.description
                ?.replace(Regex("[\\u0000-\\u001F\\u200B-\\u200D\\uFEFF]"), "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "No description available"

            holder.description.apply {
                text = finalDescription
                visibility = View.VISIBLE
            }

            // 🔹 Condition
            holder.itemCondition.text =
                "Condition: ${item.condition?.takeIf { it.isNotBlank() } ?: "N/A"}"

            // 🔹 Price
            holder.price.text = when {
                item.price == null -> "N/A"
                item.price == 0.0 -> "Barter Only"
                else -> "₱${item.price}"
            }

            // 🔹 Owner Name
            holder.ownerName.text =
                "Posted by: ${item.ownerName?.takeIf { it.isNotBlank() } ?: "Unknown"}"

            // 🔹 Item Image - SIMPLIFIED VERSION
            Glide.with(holder.itemView.context).clear(holder.image)
            val firstImage = item.imageUrls
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "none"

            Glide.with(holder.itemView.context)
                .load(firstImage)
                .placeholder(R.drawable.bg_home_profile_logo)
                .error(R.drawable.bg_home_profile_logo)
                .centerCrop()
                .into(holder.image)

            // 🔹 Owner Image
            Glide.with(holder.itemView.context).clear(holder.ownerImage)
            if (!item.ownerProfileImage.isNullOrBlank()) {
                Glide.with(holder.itemView.context)
                    .load(item.ownerProfileImage)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(holder.ownerImage)
            } else {
                holder.ownerImage.setImageResource(R.drawable.ic_profile)
            }

            holder.btnThreeDots.setOnClickListener { view ->
                onThreeDotsClick?.invoke(view, item)
            }

            // 🔹 Favorite button
            setupFavoriteButton(holder, item)

            // 🔹 Item click → open details
            holder.itemView.setOnClickListener { view ->
                val bundle = android.os.Bundle().apply {
                    putString("itemId", item.itemId)
                    putString("ownerId", item.ownerId)
                }
                view.findNavController().navigate(
                    R.id.action_homeFragment_to_itemDetailFragment,
                    bundle
                )
            }

        } catch (e: Exception) {
            Log.e("FeaturedAdapter", "CRITICAL ERROR at position $position", e)
            holder.title.text = "Error loading"
            holder.description.text = "Please try again"
            holder.price.text = "N/A"
            holder.ownerName.text = "Posted by: Unknown"
            holder.image.setImageResource(R.drawable.bg_home_profile_logo)
            holder.ownerImage.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun setupFavoriteButton(holder: FeaturedViewHolder, item: FeaturedItem) {
        holder.itemWishlistBottom.setOnClickListener {
            val isSelected = !holder.itemWishlistBottom.isSelected
            holder.itemWishlistBottom.isSelected = isSelected

            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val itemId = item.itemId
            val favRef = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
                .getReference("favorites")
                .child(userId)
                .child(itemId)

            if (isSelected) {
                favRef.setValue(item)
                    .addOnSuccessListener {
                        Toast.makeText(holder.itemView.context, "Added to Favorites", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(holder.itemView.context, "Failed to add favorite", Toast.LENGTH_SHORT).show()
                    }
            } else {
                favRef.removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(holder.itemView.context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(holder.itemView.context, "Failed to remove favorite", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun displayLikeCount(holder: FeaturedViewHolder, item: FeaturedItem) {
        val likeCount = item.likeCount
        holder.likeContainer.visibility = View.VISIBLE
        holder.likeCountText.text = likeCount.toString()
    }

    private fun calculateDaysPosted(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val safeTimestamp = if (timestamp > now) now else timestamp
        val difference = now - safeTimestamp
        val days = (difference / (1000 * 60 * 60 * 24)).toInt()

        return when {
            days == 0 -> "Today"
            days == 1 -> "1d ago"
            days < 7 -> "${days}d ago"
            days < 30 -> "${days / 7}w ago"
            else -> "${days / 30}mo ago"
        }
    }

    override fun getItemCount() = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: List<FeaturedItem>) {
        Log.d("FeaturedAdapter", "🔁 Updating adapter with ${newItems.size} items")

        // Log all items for debugging
        newItems.forEachIndexed { index, item ->
            Log.d("AdapterUpdate", "Item $index: '${item.title}'")
            Log.d("AdapterUpdate", "   Description: '${item.description}'")
            Log.d("AdapterUpdate", "   Description length: ${item.description.length}")
        }

        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()

        Log.d("FeaturedAdapter", "✅ Adapter updated with ${items.size} items")
    }
}