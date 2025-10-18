package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.adapters.TradeHistoryAdapter
import com.example.barterhub.data.models.TradeHistoryItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class TradeHistoryFragment : Fragment(R.layout.fragment_trade_history) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoTrades: TextView
    private val trades = mutableListOf<TradeHistoryItem>()
    private lateinit var adapter: TradeHistoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        recyclerView = view.findViewById(R.id.rvTradeHistory)
        tvNoTrades = view.findViewById(R.id.tvNoTrades)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = TradeHistoryAdapter(trades)
        recyclerView.adapter = adapter

        loadTradeHistory()
    }

    private fun loadTradeHistory() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        Log.d("TradeHistory", "Current UID: $userId") // log current user ID

        if (userId == null) return

        val ref = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("trades")
            .child(userId)

        ref.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                trades.clear()
                Log.d("TradeHistory", "Children count: ${snapshot.childrenCount}") // log number of trades found

                for (child in snapshot.children) {
                    Log.d("TradeHistory", "Child key: ${child.key}, value: ${child.value}")
                    val trade = child.getValue(TradeHistoryItem::class.java)
                    if (trade != null) {
                        trades.add(trade)
                    } else {
                        Log.e("TradeHistory", "Failed to parse trade: ${child.key}")
                    }
                }

                adapter.notifyDataSetChanged()
                tvNoTrades.visibility = if (trades.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load trade history", Toast.LENGTH_SHORT).show()
            }
        })
    }


}