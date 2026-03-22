package com.example.barterhub.adapters


import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.barterhub.R
import com.example.barterhub.data.models.TradeRequest
import com.example.barterhub.databinding.ItemTradeRequestBinding
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.database.FirebaseDatabase


class TradeRequestsAdapter(
    private var requests: List<TradeRequest>,
    private val currentUserId: String,
    private val onStatusUpdate: (TradeRequest, String) -> Unit
) : RecyclerView.Adapter<TradeRequestsAdapter.TradeRequestViewHolder>() {

    private val db = FirebaseDatabase.getInstance().reference

    private inner class AdditionalPhotosAdapter(
        private val photoUrls: List<String>,
        private val onPhotoClick: (String) -> Unit
    ) : RecyclerView.Adapter<AdditionalPhotosAdapter.PhotoViewHolder>() {

        inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.ivAdditionalPhoto)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_additional_photo, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val photoUrl = photoUrls[position]

            Glide.with(holder.itemView.context)
                .load(photoUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .centerCrop()
                .into(holder.imageView)

            holder.itemView.setOnClickListener {
                onPhotoClick(photoUrl)
            }
        }

        override fun getItemCount(): Int = photoUrls.size
    }

    inner class TradeRequestViewHolder(private val binding: ItemTradeRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private fun setupAdditionalPhotosRecyclerView(photoUrls: List<String>) {
            val photoAdapter = AdditionalPhotosAdapter(photoUrls) { photoUrl ->
                // Handle photo click - you can show full screen image here
                Log.d("TradeAdapter", "Photo clicked: $photoUrl")
            }

            binding.rvOfferPhotos.apply {
                layoutManager = LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
                adapter = photoAdapter
            }
        }

        private fun showMultiplePhotosIndicator(imageView: ImageView, photoCount: Int) {
            // For now, let's just log it
            Log.d("TradeAdapter", "Multiple photos available: $photoCount")

            // You can add visual indicator here later
            if (photoCount > 1) {
                // Add a badge or overlay to indicate multiple photos
                imageView.setOnClickListener {
                    // Show dialog or expand view with all photos
                    Log.d("TradeAdapter", "Show all $photoCount photos")
                }
            }
        }

        private fun hideMultiplePhotosIndicator(imageView: ImageView) {
            // Remove any indicators if needed
            imageView.setOnClickListener(null)
        }

        private fun loadItemImageWithFallback(imageUrl: String, imageView: ImageView, debugTag: String) {
            if (imageUrl.isNotEmpty() && (imageUrl.startsWith("http") || imageUrl.startsWith("content") || imageUrl.startsWith("file"))) {
                // Valid URL - load with Glide
                Log.d("ImageDebug", "✅ Loading image for $debugTag: $imageUrl")
                Glide.with(imageView.context)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .addListener(object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<android.graphics.drawable.Drawable>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.e("ImageDebug", "❌ Failed to load image for $debugTag: ${e?.message}")
                            return false
                        }

                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable?,
                            model: Any?,
                            target: Target<android.graphics.drawable.Drawable>?,
                            dataSource: com.bumptech.glide.load.DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.d("ImageDebug", "✅ Successfully loaded image for $debugTag")
                            return false
                        }
                    })
                    .into(imageView)
            } else {
                // Invalid or empty URL - use placeholder
                Log.w("ImageDebug", "⚠️ Using placeholder for $debugTag - Invalid URL: '$imageUrl'")
                imageView.setImageResource(R.drawable.ic_image_placeholder)
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(request: TradeRequest) {
            val isReceived = request.toUser.userId == currentUserId
            Log.d("LocationDebug", "📍 BEFORE Setting Location: '${binding.tvUserLocation.text}'")
            Log.d("LocationDebug", "📍 START OF BIND FUNCTION")
            Log.d("LocationDebug", "📍 User: ${request.fromUser.username}")
            Log.d("LocationDebug", "📍 Location data: '${request.fromUser.location}'")

            // Debug each additional photo
            request.additionalPhotos.forEachIndexed { index, url ->
                Log.d("PhotoDebug", "Additional Photo $index: $url")
            }

            binding.tvRequester.text = request.fromUser.username.ifEmpty { "Unknown User" }

            Log.d("LocationDebug", "📍 Before location logic")
            Log.d("LocationDebug", "📍 Location data: '${request.fromUser.location}'")

            val displayLocation = if (!request.fromUser.location.isNullOrEmpty()) {
                request.fromUser.location
            } else {
                "Manila, Philippines" // Better default value
            }

            binding.tvUserLocation.text = displayLocation

            if (request.fromUser.profileImage.isNotEmpty() && request.fromUser.profileImage.startsWith("http")) {
                Glide.with(binding.root.context)
                    .load(request.fromUser.profileImage)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(binding.ivRequesterPhoto)
            } else {
                binding.ivRequesterPhoto.setImageResource(R.drawable.ic_profile)
            }

            // Set trade summary
            val tradeSummary = if (isReceived) {
                "wants to trade with your item"
            } else {
                "You offered to trade"
            }
            binding.tvTradeSummary.text = tradeSummary

            // Set date and time
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            binding.tvDate.text = dateFormat.format(Date(request.timestamp))
            binding.tvTime.text = timeFormat.format(Date(request.timestamp))

            // Set status
            binding.tvStatus.text = request.status
            when (request.status) {
                "Accepted" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.status_accepted_background)
                    binding.tvStatus.setTextColor(binding.root.context.getColor(android.R.color.white))
                }
                "Sold Out" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.status_rejected_background)
                    binding.tvStatus.setTextColor(binding.root.context.getColor(android.R.color.white))
                }
                else -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.status_pending_background)
                    binding.tvStatus.setTextColor(binding.root.context.getColor(android.R.color.white))
                }
            }

            loadItemImageWithFallback(
                request.targetItem.image,
                binding.ivTargetItem,
                "Target: ${request.targetItem.title}"
            )

            val offeredItemImageUrl = if (request.additionalPhotos.isNotEmpty()) {
                // Use first additional photo as the main offered item image
                request.additionalPhotos.first()
            } else {
                // Fallback to the original offered item image
                request.offeredItem.image
            }

            loadItemImageWithFallback(
                offeredItemImageUrl,
                binding.ivOfferedItem,
                "Offered: ${request.offeredItem.title}"
            )

            // Set item titles
            binding.tvTargetItemTitle.text = request.targetItem.title.ifEmpty { "Unknown Item" }
            binding.tvOfferedItemTitle.text = request.offeredItem.title.ifEmpty { "Unknown Item" }

            // Handle message
            if (request.message.isNotEmpty()) {
                binding.tvMessageLabel.visibility = View.VISIBLE
                binding.tvMessage.visibility = View.VISIBLE
                binding.tvMessage.text = request.message
                binding.tvMessageLabel.text = "Message from ${request.fromUser.username}"
            } else {
                binding.tvMessageLabel.visibility = View.GONE
                binding.tvMessage.visibility = View.GONE
            }

            if (request.additionalPhotos.isNotEmpty() && request.additionalPhotos.any { it.isNotEmpty() }) {
                binding.tvPhotosLabel.visibility = View.VISIBLE
                binding.rvOfferPhotos.visibility = View.VISIBLE
                setupAdditionalPhotosRecyclerView(request.additionalPhotos)
                showMultiplePhotosIndicator(binding.ivOfferedItem, request.additionalPhotos.size)

                Log.d("TradeAdapter", "Additional photos available: ${request.additionalPhotos.size}")
            } else {
                binding.tvPhotosLabel.visibility = View.GONE
                binding.rvOfferPhotos.visibility = View.GONE
                hideMultiplePhotosIndicator(binding.ivOfferedItem)
            }

            if (isReceived && request.status == "Pending") {
                binding.btnAccept.visibility = View.VISIBLE
                binding.btnReject.visibility = View.VISIBLE
            } else {
                binding.btnAccept.visibility = View.GONE
                binding.btnReject.visibility = View.GONE
            }

            binding.btnRemove.visibility = View.VISIBLE

            binding.btnAccept.setOnClickListener {
                // Use "accept" (not "Accepted") to trigger acceptTradeRequest function
                onStatusUpdate(request, "accept")
            }

            binding.btnReject.setOnClickListener {
                onStatusUpdate(request, "Sold Out")
            }

            binding.btnRemove.setOnClickListener {
                val uid = currentUserId
                if (uid.isEmpty()) return@setOnClickListener

                val requestId = request.requestId
                if (requestId.isEmpty()) return@setOnClickListener

                db.child("trade_requests")
                    .child(requestId)
                    .child("hiddenBy")
                    .child(uid)
                    .setValue(true)
                    .addOnSuccessListener {
                        val pos = bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            val mutable = requests.toMutableList()
                            mutable.removeAt(pos)
                            updateRequests(mutable)
                        }
                    }
            }


            Log.d("TradeAdapter", "Binding request: ${request.fromUser.username} -> ${request.targetItem.title}")
            Log.d("LocationDebug", "📍 FINAL Location Set: '$displayLocation'")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TradeRequestViewHolder {
        val binding = ItemTradeRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TradeRequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TradeRequestViewHolder, position: Int) {
        holder.bind(requests[position])
    }

    override fun getItemCount(): Int = requests.size

    fun updateRequests(newRequests: List<TradeRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}