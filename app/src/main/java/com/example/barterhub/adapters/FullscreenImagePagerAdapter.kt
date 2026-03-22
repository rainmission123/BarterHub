package com.example.barterhub.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.databinding.ItemFullscreenImageBinding

class FullscreenImagePagerAdapter(
    private val urls: List<String>
) : RecyclerView.Adapter<FullscreenImagePagerAdapter.VH>() {

    inner class VH(val binding: ItemFullscreenImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFullscreenImageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        Glide.with(holder.itemView.context)
            .load(urls[position])
            .placeholder(R.drawable.backgroundlogin)
            .error(R.drawable.backgroundlogin)
            .into(holder.binding.photoView)   // ✅ matches XML id
    }

    override fun getItemCount(): Int = urls.size
}
