package com.example.barterhub.adapters.message.reactions

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import android.widget.Toast
import androidx.viewpager2.widget.ViewPager2
import com.example.barterhub.R
import com.example.barterhub.adapters.EmojiPagerAdapter
import com.example.barterhub.data.models.Message
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class ReactionPickerDialog(
    private val currentUserId: String,
    private val findMessagePosition: (messageId: String) -> Int,
    private val onReactionSelected: (
        messageId: String,
        emoji: String
    ) -> Unit,
    private val onDeleteMessage: (
        message: Message,
        position: Int
    ) -> Unit
) {

    companion object {
        private const val TAG = "ReactionPickerDialog"
    }

    fun show(
        context: Context,
        message: Message
    ) {
        val messageId = message.messageId

        if (messageId.isBlank()) {
            Log.e(
                TAG,
                "Cannot open reaction picker: blank messageId"
            )

            Toast.makeText(
                context,
                "Cannot react to this message",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val dialogView = LayoutInflater
            .from(context)
            .inflate(
                R.layout.emoji_picker_dialog,
                null
            )

        val tabLayout =
            dialogView.findViewById<TabLayout>(
                R.id.tabLayout
            )

        val emojiPlaceholder =
            dialogView.findViewById<RecyclerView>(
                R.id.rvEmojis
            )

        val btnClose =
            dialogView.findViewById<Button>(
                R.id.btnClose
            )

        val btnCopyMessage =
            dialogView.findViewById<TextView>(
                R.id.btnCopyMessage
            )

        val btnDeleteMessage =
            dialogView.findViewById<TextView>(
                R.id.btnDeleteMessage
            )

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        setupCopyButton(
            context = context,
            dialog = dialog,
            button = btnCopyMessage,
            message = message
        )

        setupDeleteButton(
            context = context,
            dialog = dialog,
            button = btnDeleteMessage,
            message = message
        )

        setupQuickEmojis(
            dialogView = dialogView,
            dialog = dialog,
            messageId = messageId
        )

        setupEmojiPager(
            context = context,
            dialog = dialog,
            tabLayout = tabLayout,
            placeholder = emojiPlaceholder,
            messageId = messageId
        )

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawableResource(
                    android.R.color.transparent
                )

                val updatedAttributes = attributes
                updatedAttributes.gravity = Gravity.BOTTOM
                updatedAttributes.width =
                    ViewGroup.LayoutParams.MATCH_PARENT
                updatedAttributes.height =
                    ViewGroup.LayoutParams.WRAP_CONTENT

                attributes = updatedAttributes
            }
        }

        dialog.show()
    }

    private fun setupCopyButton(
        context: Context,
        dialog: AlertDialog,
        button: TextView,
        message: Message
    ) {
        button.setOnClickListener {
            val textToCopy = message.text.orEmpty()

            if (textToCopy.isBlank()) {
                Toast.makeText(
                    context,
                    "Nothing to copy",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val clipboard =
                context.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    "BarterHub message",
                    textToCopy
                )
            )

            Toast.makeText(
                context,
                "Message copied",
                Toast.LENGTH_SHORT
            ).show()

            dialog.dismiss()
        }
    }

    private fun setupDeleteButton(
        context: Context,
        dialog: AlertDialog,
        button: TextView,
        message: Message
    ) {
        button.visibility =
            if (message.senderId == currentUserId) {
                View.VISIBLE
            } else {
                View.GONE
            }

        button.setOnClickListener {
            val currentPosition =
                findMessagePosition(message.messageId)

            if (currentPosition < 0) {
                Toast.makeText(
                    context,
                    "Message could not be found",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            AlertDialog.Builder(context)
                .setTitle("Delete Message")
                .setMessage("Delete this message for you?")
                .setPositiveButton("Delete") { _, _ ->
                    onDeleteMessage(
                        message,
                        currentPosition
                    )

                    dialog.dismiss()
                }
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .show()
        }
    }

    private fun setupQuickEmojis(
        dialogView: View,
        dialog: AlertDialog,
        messageId: String
    ) {
        val quickEmojiViews = mapOf(
            R.id.emojiLike to "👍",
            R.id.emojiLove to "❤️",
            R.id.emojiHaha to "😂",
            R.id.emojiWow to "😮",
            R.id.emojiSad to "😢",
            R.id.emojiAngry to "😠"
        )

        quickEmojiViews.forEach { entry ->
            val emojiView =
                dialogView.findViewById<TextView>(
                    entry.key
                )

            emojiView?.setOnClickListener {
                onReactionSelected(
                    messageId,
                    entry.value
                )

                dialog.dismiss()
            }
        }
    }

    private fun setupEmojiPager(
        context: Context,
        dialog: AlertDialog,
        tabLayout: TabLayout,
        placeholder: RecyclerView,
        messageId: String
    ) {
        val viewPager = ViewPager2(context).apply {
            orientation =
                ViewPager2.ORIENTATION_HORIZONTAL

            adapter = EmojiPagerAdapter(
                context,
                EmojiCatalog.categories
            ) { selectedEmoji ->
                onReactionSelected(
                    messageId,
                    selectedEmoji
                )

                dialog.dismiss()
            }
        }

        val parent =
            placeholder.parent as? ViewGroup

        if (parent == null) {
            Log.e(
                TAG,
                "Emoji placeholder has no ViewGroup parent"
            )
            return
        }

        val placeholderIndex =
            parent.indexOfChild(placeholder)

        val layoutParams =
            placeholder.layoutParams
                ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    280.dpToPx(context)
                )

        parent.removeView(placeholder)

        parent.addView(
            viewPager,
            placeholderIndex,
            layoutParams
        )

        TabLayoutMediator(
            tabLayout,
            viewPager
        ) { tab, position ->
            tab.text =
                EmojiCatalog.categories[position].first
        }.attach()
    }

    private fun Int.dpToPx(
        context: Context
    ): Int {
        return (
                this *
                        context.resources.displayMetrics.density
                ).toInt()
    }
}