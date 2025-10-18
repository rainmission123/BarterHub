package com.example.barterhub.adapters

import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.UserItem
import androidx.navigation.findNavController

class UserItemAdapter(private val items: List<UserItem>) :
    RecyclerView.Adapter<UserItemAdapter.UserItemViewHolder>() {

    private var isDarkMode = false

    class UserItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.itemTitle)
        val description: TextView = itemView.findViewById(R.id.itemDescription)
        val price: TextView = itemView.findViewById(R.id.itemPrice)
        val originalPrice: TextView = itemView.findViewById(R.id.itemOriginalPrice)
        val owner: TextView = itemView.findViewById(R.id.itemOwner)
        val image: ImageView = itemView.findViewById(R.id.itemImage)
        val wishlistIcon: ImageView = itemView.findViewById(R.id.itemWishlistBottom)
        val ownerImage: ImageView = itemView.findViewById(R.id.itemOwnerImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_post, parent, false)
        return UserItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserItemViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.price.text = item.price
        holder.description.text = item.description
        holder.originalPrice.text = item.originalPrice
        holder.owner.text = item.ownerName

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(R.drawable.ic_placeholder)
            .into(holder.image)

        // --- Apply theme attributes for colors ---
        val context = holder.itemView.context
        val typedValue = TypedValue()

        // Title color
        context.theme.resolveAttribute(R.attr.postTextColor, typedValue, true)
        holder.title.setTextColor(typedValue.data)

        // Description & Owner color
        context.theme.resolveAttribute(R.attr.postTextColorSecondary, typedValue, true)
        holder.description.setTextColor(typedValue.data)
        holder.owner.setTextColor(typedValue.data)
        holder.originalPrice.setTextColor(typedValue.data)

        // Price color
        context.theme.resolveAttribute(R.attr.postPriceColor, typedValue, true)
        holder.price.setTextColor(typedValue.data)

        // Wishlist icon color
        context.theme.resolveAttribute(R.attr.postWishlistIconColor, typedValue, true)
        holder.wishlistIcon.setColorFilter(typedValue.data)

        // --- Click listener for navigation ---
        holder.itemView.setOnClickListener { view ->
            val bundle = android.os.Bundle().apply {
                putString("itemId", item.id)
                putString("ownerId", item.userId)
            }
            view.findNavController()
                .navigate(R.id.action_homeFragment_to_itemDetailFragment, bundle)
        }
    }


    override fun getItemCount(): Int = items.size

    fun setDarkMode(enabled: Boolean) {
        isDarkMode = enabled
        notifyDataSetChanged() // Refresh lahat ng items
    }
}


