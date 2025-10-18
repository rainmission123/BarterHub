package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.util.TypedValue
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

class FeaturedAdapter(private val items: MutableList<FeaturedItem>) :
    RecyclerView.Adapter<FeaturedAdapter.FeaturedViewHolder>() {

    private var isDarkMode = false

    fun setDarkMode(enabled: Boolean) {
        isDarkMode = enabled
        notifyDataSetChanged()
    }

    class FeaturedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.itemTitle)
        val description: TextView = itemView.findViewById(R.id.itemDescription)
        val image: ImageView = itemView.findViewById(R.id.itemImage)
        val price: TextView = itemView.findViewById(R.id.itemPrice)
        val originalPrice: TextView = itemView.findViewById(R.id.itemOriginalPrice)
        val ownerName: TextView = itemView.findViewById(R.id.itemOwner)
        val ownerImage: ImageView = itemView.findViewById(R.id.itemOwnerImage)
        val itemWishlistBottom: ImageView = itemView.findViewById(R.id.itemWishlistBottom)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeaturedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_post, parent, false)
        return FeaturedViewHolder(view)
    }@SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: FeaturedViewHolder, position: Int) {
        val item = items[position]

        try {
            // --- Set text ---
            holder.title.text = item.title
            holder.description.text = item.description
            holder.price.text = if (item.price == null || item.price == 0.0) "Barter Only"
            else "₱%.2f".format(item.price)
            holder.originalPrice.text = item.originalPrice

            val displayName = item.ownerName.ifEmpty { "Unknown" }
            holder.ownerName.text = "Posted by: $displayName"

            // Strikethrough original price only if not empty
            holder.originalPrice.paintFlags =
                if (item.originalPrice.isNotEmpty())
                    holder.originalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else 0

            // --- Load images ---
            val firstImage = item.imageUrls.split(",").firstOrNull()?.trim() ?: ""
            Glide.with(holder.itemView.context)
                .load(firstImage)
                .placeholder(R.drawable.login_background)
                .error(R.drawable.login_background)
                .into(holder.image)

            if (item.ownerProfileImage.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(item.ownerProfileImage)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(holder.ownerImage)
            } else {
                holder.ownerImage.setImageResource(R.drawable.ic_profile)
            }

            // --- Apply theme-based colors ---
            val context = holder.itemView.context
            val typedValue = TypedValue()

            context.theme.resolveAttribute(R.attr.postTextColor, typedValue, true)
            holder.title.setTextColor(typedValue.data)

            context.theme.resolveAttribute(R.attr.postTextColorSecondary, typedValue, true)
            holder.description.setTextColor(typedValue.data)
            holder.ownerName.setTextColor(typedValue.data)
            holder.originalPrice.setTextColor(typedValue.data)

            context.theme.resolveAttribute(R.attr.postPriceColor, typedValue, true)
            holder.price.setTextColor(typedValue.data)

            // --- Wishlist click listener ---
            holder.itemWishlistBottom.setOnClickListener {
                val isSelected = !holder.itemWishlistBottom.isSelected
                holder.itemWishlistBottom.isSelected = isSelected

                val context = holder.itemView.context
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
                val itemId = item.itemId
                val favRef = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
                    .getReference("favorites")
                    .child(userId)
                    .child(itemId)

                if (isSelected) {
                    favRef.setValue(item)
                        .addOnSuccessListener { Toast.makeText(context, "Added to Favorites", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener { Toast.makeText(context, "Failed to add favorite", Toast.LENGTH_SHORT).show() }
                } else {
                    favRef.removeValue()
                        .addOnSuccessListener { Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener { Toast.makeText(context, "Failed to remove favorite", Toast.LENGTH_SHORT).show() }
                }

            }

            // --- Item click navigation ---
            holder.itemView.setOnClickListener { view ->
                val bundle = android.os.Bundle().apply {
                    putString("itemId", item.itemId)
                    putString("ownerId", item.ownerId)
                }
                view.findNavController().navigate(R.id.action_homeFragment_to_itemDetailFragment, bundle)
            }

        } catch (e: Exception) {
            Log.e("FeaturedAdapter", "Error binding item at position $position", e)
        }
    }


    override fun getItemCount() = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: List<FeaturedItem>) {
        Log.d("FeaturedAdapter", "🔁 Updating adapter with ${newItems.size} items")
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}

