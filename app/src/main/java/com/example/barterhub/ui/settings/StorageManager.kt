package com.example.barterhub.ui.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.example.barterhub.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class StorageManager(
    private val context: Context
) {

    fun showDataStorageBottomSheet(onClearCache: () -> Unit) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_data_storage, null)

        val btnClearCache = view.findViewById<View>(R.id.btnClearCache)

        btnClearCache.setOnClickListener {
            onClearCache()
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }
}