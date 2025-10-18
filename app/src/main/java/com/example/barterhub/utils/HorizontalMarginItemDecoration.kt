package com.example.barterhub.utils

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class HorizontalMarginItemDecoration(
    context: Context,
    margin: Int
) : RecyclerView.ItemDecoration() {

    private val horizontalMargin = margin

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.left = horizontalMargin
        outRect.right = horizontalMargin
    }
}