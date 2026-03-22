package com.example.barterhub.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.FeaturedItem
import com.google.android.material.button.MaterialButton

class UserListingsAdapter(
    private var items: List<FeaturedItem>,
    private val onEditClick: (FeaturedItem) -> Unit,
    private val onDeleteClick: (FeaturedItem) -> Unit
) : RecyclerView.Adapter<UserListingsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemImage: ImageView = view.findViewById(R.id.itemImage)
        val itemTitle: TextView = view.findViewById(R.id.itemTitle)
        val itemDescription: TextView = view.findViewById(R.id.itemDescription)
        val itemPrice: TextView = view.findViewById(R.id.itemPrice)
        val btnEdit: MaterialButton = view.findViewById(R.id.btnEdit)
        val btnDelete: MaterialButton = view.findViewById(R.id.btnDelete)

        init {
            Log.d("UserListingsAdapter", "✅ ViewHolder created - Buttons found: " +
                    "Edit=${btnEdit != null}, Delete=${btnDelete != null}")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        Log.d("UserListingsAdapter", "🔧 Creating ViewHolder")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_listing, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        try {
            Log.d("UserListingsAdapter", "📱 Binding item: ${item.title} at position $position")

            // 1️⃣ Calculate width for 2-column grid
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val cardWidth = (screenWidth / 2) - 24 // 2 columns with margins

            // 2️⃣ Set card width
            val layoutParams = holder.itemView.layoutParams as RecyclerView.LayoutParams
            layoutParams.width = cardWidth
            holder.itemView.layoutParams = layoutParams

            // 3️⃣ Set Title
            holder.itemTitle.text = if (item.title.length > 20) {
                "${item.title.substring(0, 20)}..."
            } else {
                item.title
            }

            // 4️⃣ Set Description
            holder.itemDescription.text = if (!item.description.isNullOrEmpty()) {
                if (item.description.length > 30) {
                    "${item.description.substring(0, 30)}..."
                } else {
                    item.description
                }
            } else {
                "No description"
            }

            // 5️⃣ Set Price
            holder.itemPrice.text = if (item.price == 0.0) {
                "Barter Only"
            } else {
                "₱${item.price}"
            }

            // 6️⃣ Load first image
            val firstImage = item.imageUrls?.split(",")?.firstOrNull()?.trim() ?: ""
            Glide.with(context)
                .load(firstImage)
                .override(cardWidth, (cardWidth * 0.75).toInt())
                .placeholder(R.drawable.backgroundlogin)
                .error(R.drawable.backgroundlogin)
                .into(holder.itemImage)

            // ✅ CRITICAL FIX: Button click listeners - MUST BE SET EVERY TIME
            Log.d("UserListingsAdapter", "🔄 Setting listeners for item: ${item.itemId}")

            holder.btnEdit.apply {
                setOnClickListener {
                    Log.d("UserListingsAdapter", "✅ EDIT CLICKED: ${item.title} (ID: ${item.itemId})")
                    onEditClick(item)
                }
                // Make sure button is enabled and visible
                isEnabled = true
                visibility = View.VISIBLE
            }

            holder.btnDelete.apply {
                setOnClickListener {
                    Log.d("UserListingsAdapter", "✅ DELETE CLICKED: ${item.title} (ID: ${item.itemId})")
                    onDeleteClick(item)
                }
                // Make sure button is enabled and visible
                isEnabled = true
                visibility = View.VISIBLE
            }

            // ✅ Optional: Log button state
            Log.d("UserListingsAdapter", "🎯 Buttons ready - Edit: ${holder.btnEdit.isEnabled}, Delete: ${holder.btnDelete.isEnabled}")

        } catch (e: Exception) {
            Log.e("UserListingsAdapter", "❌ Error binding item at position $position", e)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<FeaturedItem>) {
        Log.d("UserListingsAdapter", "🔄 Updating data with ${newItems.size} items")
        items = newItems
        notifyDataSetChanged()
    }
}