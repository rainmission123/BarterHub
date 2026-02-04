package com.example.barterhub.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R

class RecentSearchesAdapter(
    private val searches: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<RecentSearchesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSearch: TextView = view.findViewById(R.id.tvRecentSearch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val search = searches[position]
        holder.tvSearch.text = search
        holder.itemView.setOnClickListener {
            onItemClick(search)
        }
    }

    override fun getItemCount(): Int = searches.size
}