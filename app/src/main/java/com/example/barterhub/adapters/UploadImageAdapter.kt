package com.example.barterhub.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R

data class UploadImage(
    val imageUri: Uri? = null,
    val imageUrl: String? = null,
    var progress: Int = 0,
    var isUploading: Boolean = false
)

class UploadImageAdapter(
    private val images: MutableList<UploadImage>,
    private val onRemoveImage: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<UploadImageAdapter.UploadImageViewHolder>() {

    companion object {
        const val MAX_IMAGES = 5
    }

    inner class UploadImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val uploadImageView: ImageView = itemView.findViewById(R.id.uploadImageView)
        val uploadProgress: ProgressBar = itemView.findViewById(R.id.uploadProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UploadImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_upload_image, parent, false)
        return UploadImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: UploadImageViewHolder, position: Int) {
        val uploadImage = images[position]

        when {
            uploadImage.imageUri != null -> {
                Glide.with(holder.itemView.context)
                    .load(uploadImage.imageUri)
                    .into(holder.uploadImageView) // ✅ FIXED
            }
            uploadImage.imageUrl != null -> {
                Glide.with(holder.itemView.context)
                    .load(uploadImage.imageUrl)
                    .into(holder.uploadImageView) // ✅ FIXED
            }
            else -> {
                holder.uploadImageView.setImageResource(R.drawable.ic_image_placeholder)
            }
        }

        // Handle progress bar
        if (uploadImage.isUploading && uploadImage.progress in 1..99) {
            holder.uploadProgress.visibility = View.VISIBLE
            holder.uploadProgress.progress = uploadImage.progress
        } else {
            holder.uploadProgress.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            if (!uploadImage.isUploading) {
                onRemoveImage?.invoke(position)
            }
        }
    }

    override fun getItemCount(): Int = images.size

    fun updateProgress(position: Int, progress: Int) {
        if (position in images.indices) {
            images[position] = images[position].copy(
                progress = progress,
                isUploading = progress < 100
            )
            notifyItemChanged(position)
        }
    }

    fun markUploadComplete(position: Int, imageUrl: String) {
        if (position in images.indices) {
            images[position] = images[position].copy(
                imageUrl = imageUrl,
                progress = 100,
                isUploading = false
            )
            notifyItemChanged(position)
        }
    }

    fun addImage(image: UploadImage): Boolean {
        return if (images.size < MAX_IMAGES) {
            images.add(image)
            notifyItemInserted(images.size - 1)
            true
        } else {
            false
        }
    }

    fun removeImage(position: Int) {
        if (position in images.indices) {
            images.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getUploadedImageUrls(): List<String> = images
        .filter { it.imageUrl != null }
        .map { it.imageUrl!! }

    fun canAddMoreImages(): Boolean = images.size < MAX_IMAGES
}