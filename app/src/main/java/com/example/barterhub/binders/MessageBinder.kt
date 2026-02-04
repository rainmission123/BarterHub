package com.example.barterhub.binders

import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.data.models.Message

interface MessageBinder {
    fun bind(holder: RecyclerView.ViewHolder, message: Message, position: Int)
}