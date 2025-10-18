package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.databinding.ItemTradeRequestBinding
import com.example.barterhub.data.models.TradeRequest
import com.google.firebase.database.*

@Suppress("DEPRECATION")
class TradeRequestsAdapter(
    private val requests: List<TradeRequest>,
    private val currentUserId: String,
    private val onAction: (TradeRequest, String) -> Unit
) : RecyclerView.Adapter<TradeRequestsAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemTradeRequestBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTradeRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]

        holder.binding.btnRemove.setOnClickListener {
            val dbRef = FirebaseDatabase.getInstance().reference
            dbRef.child("trade_requests").child(request.requestId).removeValue()
                .addOnSuccessListener {
                    // 1️⃣ Remove from local list
                    val position = holder.adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        (requests as MutableList).removeAt(position)
                        notifyItemRemoved(position)
                    }

                    Toast.makeText(holder.itemView.context, "Request removed", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(holder.itemView.context, "Failed to remove: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }


        val displayName = if (request.requester == currentUserId) {
            request.ownerName
        } else {
            request.requesterName
        }

        holder.binding.tvRequester.text = if (request.requester == currentUserId) {
            "To: $displayName"
        } else {
            "From: $displayName"
        }

        holder.binding.tvItemTitle.text = request.itemTitle
        holder.binding.tvStatus.text = "Status: ${request.status}"
        val profilePhotoUrl = if (request.requester == currentUserId) {
            request.ownerPhoto
        } else {
            request.requesterPhoto
        }
        if (profilePhotoUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(profilePhotoUrl)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .into(holder.binding.ivRequesterPhoto)
        } else {
            holder.binding.ivRequesterPhoto.setImageResource(R.drawable.ic_profile)
        }

        // Load item image
        if (request.itemImage.isNotEmpty() && request.itemImage != "null") {
            Glide.with(holder.itemView.context)
                .load(request.itemImage)
                .placeholder(R.drawable.login_background)
                .error(R.drawable.login_background)
                .centerCrop()
                .into(holder.binding.ivItemImage)
        } else {
            holder.binding.ivItemImage.setImageResource(R.drawable.login_background)
            loadItemImageFromDatabase(request.itemId, holder.binding.ivItemImage)
        }
        if (request.owner == currentUserId && request.status == "Pending") {
            holder.binding.btnAccept.visibility = View.VISIBLE
            holder.binding.btnReject.visibility = View.VISIBLE
        } else {
            holder.binding.btnAccept.visibility = View.GONE
            holder.binding.btnReject.visibility = View.GONE
        }

        holder.binding.btnAccept.setOnClickListener {
            onAction(request, "Accepted")
        }

        holder.binding.btnReject.setOnClickListener {
            onAction(request, "Rejected")
        }

        holder.itemView.setOnClickListener {
            if (request.itemId.isNotEmpty() && request.owner.isNotEmpty()) {
                val bundle = android.os.Bundle().apply {
                    putString("itemId", request.itemId)
                    putString("ownerId", request.owner)
                }
                try {
                    holder.itemView.findNavController().navigate(
                        R.id.action_tradeRequestsFragment_to_itemDetailFragment,
                        bundle
                    )
                } catch (_: Exception) {
                    Toast.makeText(holder.itemView.context, "Cannot open item details", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(holder.itemView.context, "Item data incomplete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadItemImageFromDatabase(itemId: String, imageView: ImageView) {
        if (itemId.isEmpty()) return

        val database = FirebaseDatabase.getInstance().reference
        database.child("items").child(itemId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var imageUrl = snapshot.child("imageUrl").getValue(String::class.java) ?: ""
                    if (imageUrl.isEmpty()) {
                        imageUrl = snapshot.child("imageUrls").getValue(String::class.java) ?: ""
                    }
                    if (imageUrl.contains(",")) {
                        imageUrl = imageUrl.split(",").firstOrNull()?.trim() ?: ""
                    }
                    if (imageUrl.isNotEmpty() && imageUrl != "null") {
                        Glide.with(imageView.context)
                            .load(imageUrl)
                            .placeholder(R.drawable.login_background)
                            .error(R.drawable.login_background)
                            .centerCrop()
                            .into(imageView)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun getItemCount() = requests.size
}
