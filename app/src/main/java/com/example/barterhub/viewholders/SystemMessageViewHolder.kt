package com.example.barterhub.viewholders

import android.view.View
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R

@Suppress("unused", "MemberVisibilityCanBePrivate")
class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val acceptedByText: TextView = itemView.findViewById(R.id.acceptedByText)
    val offeredByText: TextView = itemView.findViewById(R.id.offeredByText)
    val acceptedByUserText: TextView = itemView.findViewById(R.id.acceptedByUserText)
    val offeredItemText: TextView = itemView.findViewById(R.id.offeredItemText)
    val targetItemText: TextView = itemView.findViewById(R.id.targetItemText)
    val btnReportIssue: View = itemView.findViewById(R.id.btnReportIssue)
    val btnCompleted: View = itemView.findViewById(R.id.btnCompleted)
    val tradeReminderWarning: TextView = itemView.findViewById(R.id.tradeReminderWarning)
    val instructionText: TextView = itemView.findViewById(R.id.instructionText)
    val tradeActionButtons: View = itemView.findViewById(R.id.tradeActionButtons)
    val tradeAcceptedTitle: TextView = itemView.findViewById(R.id.tradeAcceptedTitle)
    val ratingContainer: LinearLayout = itemView.findViewById(R.id.ratingContainer)
    val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
    val tvRateUserName: TextView = itemView.findViewById(R.id.tvRateUserName)
    val btnSubmitRating: TextView = itemView.findViewById(R.id.btnSubmitRating)
    val btnSkipRating: TextView = itemView.findViewById(R.id.btnSkipRating)
    val waitingText: TextView = itemView.findViewById(R.id.waitingText)

}