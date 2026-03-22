package com.example.barterhub.utils

import android.util.TypedValue
import android.view.View

fun Int.dpToPx(view: View): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        view.resources.displayMetrics
    ).toInt()
}