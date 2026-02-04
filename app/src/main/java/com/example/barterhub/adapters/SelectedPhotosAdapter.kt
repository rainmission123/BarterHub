package com.example.barterhub.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R // ✅ FIXED: Use your actual package name

class SelectedPhotosAdapter(
    private val photos: List<Uri>,
    private val onPhotoClick: (Uri) -> Unit
) : RecyclerView.Adapter<SelectedPhotosAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoUri = photos[position]
        holder.bind(photoUri)
    }

    override fun getItemCount() = photos.size

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.ivSelectedPhoto)
        private val btnRemove: ImageView = itemView.findViewById(R.id.btnRemovePhoto)

        fun bind(photoUri: Uri) {
            Glide.with(itemView.context)
                .load(photoUri)
                .centerCrop()
                .into(imageView)

            btnRemove.setOnClickListener {
                onPhotoClick(photoUri)
            }
        }
    }
}