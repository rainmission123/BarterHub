package com.example.barterhub.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R

class EmojiPagerAdapter(
    private val context: Context,
    private val categories: List<Pair<String, List<String>>>,
    private val onEmojiSelected: (String) -> Unit
) : RecyclerView.Adapter<EmojiPagerAdapter.EmojiCategoryViewHolder>() {

    inner class EmojiCategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recyclerView: RecyclerView = itemView.findViewById(R.id.rvEmojiGrid)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiCategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emoji_category, parent, false)
        return EmojiCategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmojiCategoryViewHolder, position: Int) {
        val emojis = categories[position].second

        // Use simple GridLayoutManager
        val layoutManager = GridLayoutManager(context, 8)
        holder.recyclerView.layoutManager = layoutManager

        val adapter = SimpleEmojiAdapter(emojis) { selectedEmoji ->
            onEmojiSelected(selectedEmoji)
        }
        holder.recyclerView.adapter = adapter
    }

    override fun getItemCount(): Int = categories.size
}

class SimpleEmojiAdapter(
    private val emojis: List<String>,
    private val onEmojiSelected: (String) -> Unit
) : RecyclerView.Adapter<SimpleEmojiAdapter.EmojiViewHolder>() {

    inner class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emojiText: TextView = itemView.findViewById(R.id.tvEmojiGrid)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emoji_grid, parent, false)
        return EmojiViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = emojis[position]
        holder.emojiText.text = emoji

        holder.itemView.setOnClickListener {
            onEmojiSelected(emoji)
        }
    }

    override fun getItemCount(): Int = emojis.size
}