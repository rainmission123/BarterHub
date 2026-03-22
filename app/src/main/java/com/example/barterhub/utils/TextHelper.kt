package com.example.barterhub.utils

import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import com.google.android.material.button.MaterialButton

object TextHelper {

    fun checkDescriptionLength(textView: TextView, viewMoreBtn: MaterialButton, description: String) {
        textView.text = description

        textView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                textView.viewTreeObserver.removeOnPreDrawListener(this)

                val layout = textView.layout
                if (layout != null) {
                    val lines = layout.lineCount
                    val isEllipsized = lines > 0 && layout.getEllipsisCount(lines - 1) > 0

                    viewMoreBtn.visibility = if (lines > 3 || isEllipsized) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }
                return true
            }
        })
    }
}