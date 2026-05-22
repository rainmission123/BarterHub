package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.FeaturedItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class FavoritesAdapter : ListAdapter<FeaturedItem, FavoritesAdapter.FavoritesViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FeaturedItem>() {
            override fun areItemsTheSame(oldItem: FeaturedItem, newItem: FeaturedItem): Boolean {
                return oldItem.itemId == newItem.itemId
            }

            override fun areContentsTheSame(oldItem: FeaturedItem, newItem: FeaturedItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    class FavoritesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.itemTitle)
        val price: TextView = itemView.findViewById(R.id.itemPrice)
        val image: ImageView = itemView.findViewById(R.id.itemImage)
        val removeButton: ImageView = itemView.findViewById(R.id.removeFavoriteButton)
        val owner: TextView = itemView.findViewById(R.id.itemOwner)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoritesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite, parent, false)
        return FavoritesViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: FavoritesViewHolder, position: Int) {
        val item = getItem(position)

        val title = item.title.trim().ifEmpty { "(Untitled item)" }
        holder.title.text = title

        // Price
        holder.price.text = when {
            item.displayPrice.isNotBlank() -> item.displayPrice
            item.price != 0.0 -> "₱${item.price}"
            else -> "Barter Only"
        }

        // Owner (fallback handled in Fragment via resolvedOwnerName, but we keep safe display)
        val ownerName = item.ownerName.trim()
        holder.owner.text = "Posted by: ${ownerName.ifEmpty { "Unknown" }}"

        // First image (comma-separated)
        val firstImage = item.imageUrls
            .split(",")
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()

        Glide.with(holder.itemView.context)
            .load(firstImage)
            .placeholder(R.drawable.bg_home_profile_logo)
            .error(R.drawable.bg_home_profile_logo)
            .into(holder.image)

        // Navigate to item detail
        holder.itemView.setOnClickListener { view ->
            val bundle = Bundle().apply {
                putString("itemId", item.itemId)
                putString("ownerId", item.ownerId)
            }
            view.findNavController()
                .navigate(R.id.action_favoritesFragment_to_itemDetailFragment, bundle)
        }

        // Remove favorite
        holder.removeButton.setOnClickListener {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val favRef = FirebaseDatabase
                .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
                .getReference("favorites")
                .child(userId)
                .child(item.itemId)

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