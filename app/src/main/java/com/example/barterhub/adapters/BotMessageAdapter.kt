package com.example.barterhub.adapters

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.databinding.ItemBotMessageBinding

class BotMessageAdapter(private val messages: List<Pair<String, Boolean>>) :
    RecyclerView.Adapter<BotMessageAdapter.BotMessageViewHolder>() {

    inner class BotMessageViewHolder(val binding: ItemBotMessageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BotMessageViewHolder {
        val binding = ItemBotMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BotMessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BotMessageViewHolder, position: Int) {
        val (text, isUser) = messages[position]

        holder.binding.messageText.text = text

        // Reset visibility
        holder.binding.botProfileImage.visibility = View.GONE

        if (isUser) {
            // USER MESSAGE - RIGHT SIDE
            val params = holder.binding.root.layoutParams as ViewGroup.MarginLayoutParams
            params.marginStart = 100
            params.marginEnd = 16
            holder.binding.root.layoutParams = params
            holder.binding.root.gravity = Gravity.END

            // Hide bot profile
            holder.binding.botProfileImage.visibility = View.GONE

            // Set user message style
            holder.binding.messageText.setBackgroundResource(R.drawable.bg_user_message)
            holder.binding.messageText.setTextColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.white)
            )
        } else {
            // BOT MESSAGE - LEFT SIDE
            val params = holder.binding.root.layoutParams as ViewGroup.MarginLayoutParams
            params.marginStart = 60  // Space for profile picture
            params.marginEnd = 100
            holder.binding.root.layoutParams = params
            holder.binding.root.gravity = Gravity.START

            // Show bot profile
            holder.binding.botProfileImage.visibility = View.VISIBLE
            holder.binding.botProfileImage.setImageResource(R.drawable.ic_bot_avatar)

            // Set bot message style
            holder.binding.messageText.setBackgroundResource(R.drawable.bg_bot_message)
            holder.binding.messageText.setTextColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.black)
            )
        }
    }

    override fun getItemCount(): Int = messages.size
}