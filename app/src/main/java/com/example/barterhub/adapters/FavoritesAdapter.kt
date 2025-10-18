package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.FeaturedItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class FavoritesAdapter(private val items: MutableList<FeaturedItem>) :
    RecyclerView.Adapter<FavoritesAdapter.FavoritesViewHolder>() {

    class FavoritesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.itemTitle)
        val price: TextView = itemView.findViewById(R.id.itemPrice)
        val image: ImageView = itemView.findViewById(R.id.itemImage)
        val removeButton: ImageView = itemView.findViewById(R.id.removeFavoriteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoritesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite, parent, false)
        return FavoritesViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: FavoritesViewHolder, position: Int) {
        val item = items[position]

        holder.title.text = item.title
        holder.price.text = if (item.price != null) "₱${item.price}" else "Barter Only"

        // Load image
        val firstImage = item.imageUrls.split(",").firstOrNull()?.trim() ?: ""
        Glide.with(holder.itemView.context)
            .load(firstImage)
            .placeholder(R.drawable.login_background)
            .error(R.drawable.login_background)
            .into(holder.image)

        holder.itemView.setOnClickListener { view ->
            val bundle = android.os.Bundle().apply {
                putString("itemId", item.itemId)
                putString("ownerId", item.ownerId)
            }
            view.findNavController().navigate(R.id.action_favoritesFragment_to_itemDetailFragment, bundle)
        }

        holder.removeButton.setOnClickListener {
            val currentPosition = holder.absoluteAdapterPosition // ✅ USE THIS INSTEAD
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val favRef = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
                .getReference("favorites")
                .child(userId)
                .child(item.itemId)

            favRef.removeValue()
                .addOnSuccessListener {
                    Toast.makeText(holder.itemView.context, "Removed from Favorites", Toast.LENGTH_SHORT).show()

                    // ✅ SAFE REMOVE WITH BOUNDS CHECK
                    if (currentPosition in 0 until items.size) {
                        items.removeAt(currentPosition)
                        notifyItemRemoved(currentPosition)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(holder.itemView.context, "Failed to remove favorite", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun getItemCount() = items.size

    // ✅ OPTIONAL: Add function to update entire list safely
    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<FeaturedItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}