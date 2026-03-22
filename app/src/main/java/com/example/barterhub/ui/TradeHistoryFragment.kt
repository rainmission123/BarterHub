package com.example.barterhub.ui

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
import com.google.firebase.database.*

class TradeHistoryFragment : Fragment(R.layout.fragment_trade_history) {

    companion object {
        private const val TAG = "TradeHistory"
        private const val DB_URL = "https://barterhub-3c947-default-rtdb.firebaseio.com/"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoTrades: TextView

    private lateinit var adapter: TradeHistoryAdapter
    private val trades = mutableListOf<TradeHistoryItem>()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance(DB_URL).reference }

    private var tradesListener: ValueEventListener? = null
    private var tradesRef: DatabaseReference? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvTradeHistory)
        tvNoTrades = view.findViewById(R.id.tvNoTrades)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = TradeHistoryAdapter(mutableListOf())
        recyclerView.adapter = adapter

        tvNoTrades.visibility = View.GONE

        loadTradeHistory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // important: alisin listener para walang leak / duplicate listener
        tradesListener?.let { listener ->
            tradesRef?.removeEventListener(listener)
        }
        tradesListener = null
        tradesRef = null
    }

    private fun loadTradeHistory() {
        val userId = auth.currentUser?.uid
        Log.d(TAG, "Current UID: $userId")

        if (userId.isNullOrEmpty()) {
            tvNoTrades.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Please login first.", Toast.LENGTH_SHORT).show()
            return
        }

        val ref = db.child("trades").child(userId)
        tradesRef = ref

        // remove old listener if any (safety)
        tradesListener?.let { ref.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                trades.clear()
                Log.d(TAG, "Trades children count: ${snapshot.childrenCount}")

                for (child in snapshot.children) {
                    val trade = child.getValue(TradeHistoryItem::class.java)

                    if (trade != null) {
                        trades.add(trade)
                    } else {
                        Log.e(TAG, "Failed to parse trade. key=${child.key} value=${child.value}")
                    }
                }

                // optional: newest first kung date is readable, pero since string lang,
                // we won't sort para di masira.
                adapter.submitList(trades)

                tvNoTrades.visibility = if (trades.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                Log.e(TAG, "loadTradeHistory cancelled: ${error.message}")
                Toast.makeText(requireContext(), "Failed to load trade history", Toast.LENGTH_SHORT).show()
                tvNoTrades.visibility = View.VISIBLE
            }
        }

        tradesListener = listener
        ref.addValueEventListener(listener)
    }
}