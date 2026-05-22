package com.example.barterhub.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R

data class OwnerItemUi(
    val itemId: String,
    val ownerId: String,
    val title: String,
    val priceText: String,
    val imageUrl: String?
)

class OwnerProfileItemsAdapter(
    private val items: MutableList<OwnerItemUi>,
    private val onClick: (OwnerItemUi) -> Unit
) : RecyclerView.Adapter<OwnerProfileItemsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.itemImage)
        val title: TextView = v.findViewById(R.id.itemTitle)
        val price: TextView = v.findViewById(R.id.itemPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_owner_profile_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.price.text = item.priceText

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(R.drawable.bg_home_profile_logo)
            .error(R.drawable.bg_home_profile_logo)
            .centerCrop()
            .into(holder.img)

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<OwnerItemUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
