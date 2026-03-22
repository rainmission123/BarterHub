package com.example.barterhub.utils

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class GridItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)

        val position = parent.getChildAdapterPosition(view)
        val column = position % 2

        if (column == 0) {
            outRect.right = spacing / 2
        } else {
            outRect.left = spacing / 2
        }

        outRect.bottom = spacing

        if (position < 2) {
            outRect.top = spacing
        }
    }
}