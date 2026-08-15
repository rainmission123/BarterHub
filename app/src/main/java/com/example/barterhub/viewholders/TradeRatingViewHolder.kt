package com.example.barterhub.viewholders

import android.view.View
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.google.android.material.button.MaterialButton

class TradeRatingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    val tvRatingTitle: TextView = itemView.findViewById(R.id.tvRatingTitle)
    val tvRateUserName: TextView = itemView.findViewById(R.id.tvRateUserName)
    val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
    val etRatingComment: EditText = itemView.findViewById(R.id.etRatingComment)
    val btnSubmitRating: MaterialButton = itemView.findViewById(R.id.btnSubmitRating)
}