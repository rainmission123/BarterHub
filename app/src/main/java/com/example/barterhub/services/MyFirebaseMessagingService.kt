package com.example.barterhub.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.barterhub.R
import com.example.barterhub.ui.HomeActivity
import com.example.barterhub.utils.ActiveChatTracker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.data["title"]
            ?: remoteMessage.notification?.title
            ?: "BarterHub PH"

        val body = remoteMessage.data["body"]
            ?: remoteMessage.notification?.body
            ?: "You have a new notification"

        val type = remoteMessage.data["type"]
        val chatId = remoteMessage.data["chatId"]
        val itemId = remoteMessage.data["itemId"]
        val requestId = remoteMessage.data["requestId"]

        val partnerId = remoteMessage.data["partnerId"]
        val partnerName = remoteMessage.data["partnerName"]
        val partnerProfilePic = remoteMessage.data["partnerProfilePic"]

        val fromUserId = remoteMessage.data["fromUserId"]
        val fromUserName = remoteMessage.data["fromUserName"]
        val fromUserProfilePic = remoteMessage.data["fromUserProfilePic"]
        val notificationKey = remoteMessage.data["notificationId"]
            ?: remoteMessage.data["messageId"]
            ?: remoteMessage.messageId

        Log.d(
            "FCM_DEBUG",
            "Message received: type=$type chatId=$chatId partnerId=$partnerId fromUserId=$fromUserId"
        )

        if (type == "chat_message" && !chatId.isNullOrBlank() && chatId == ActiveChatTracker.currentChatId) {
            Log.d("FCM_DEBUG", "Suppressing chat notification for active chat: $chatId")
            return
        }

        showNotification(
            title = title,
            message = body,
            type = type,
            chatId = chatId,
            itemId = itemId,
            requestId = requestId,
            partnerId = partnerId,
            partnerName = partnerName,
            partnerProfilePic = partnerProfilePic,
            fromUserId = fromUserId,
            fromUserName = fromUserName,
            fromUserProfilePic = fromUserProfilePic,
            notificationKey = notificationKey
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_DEBUG", "New FCM token: $token")
        saveTokenToFirebase(token)
    }

    private fun saveTokenToFirebase(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("fcmToken")
            .setValue(token)
            .addOnSuccessListener {
                Log.d("FCM_DEBUG", "FCM token saved for user: $userId")
            }
            .addOnFailureListener { e ->
                Log.e("FCM_DEBUG", "Failed to save token: ${e.message}")
            }
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun showNotification(
        title: String,
        message: String,
        type: String? = null,
        chatId: String? = null,
        itemId: String? = null,
        requestId: String? = null,
        partnerId: String? = null,
        partnerName: String? = null,
        partnerProfilePic: String? = null,
        fromUserId: String? = null,
        fromUserName: String? = null,
        fromUserProfilePic: String? = null,
        notificationKey: String? = null
    ) {
        if (!canPostNotifications()) {
            Log.d("FCM_DEBUG", "POST_NOTIFICATIONS permission not granted")
            return
        }

        val channelId = CHANNEL_ID
        ensureChannel(channelId)
        val fallbackNotificationKey = listOfNotNull(type, chatId, requestId, itemId, fromUserId)
            .joinToString("_")
            .ifBlank { "notification" } + "_${System.currentTimeMillis()}"
        val uniqueNotificationKey = notificationKey
            ?: fallbackNotificationKey

        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "$packageName.NOTIFICATION.$uniqueNotificationKey"
            data = Uri.parse("barterhub://notification/${Uri.encode(uniqueNotificationKey)}")

            putExtra("notification_type", type)

            chatId?.let { putExtra("chatId", it) }
            itemId?.let { putExtra("itemId", it) }
            requestId?.let { putExtra("requestId", it) }

            partnerId?.let { putExtra("partnerId", it) }
            partnerName?.let { putExtra("partnerName", it) }
            partnerProfilePic?.let { putExtra("partnerProfilePic", it) }

            fromUserId?.let { putExtra("fromUserId", it) }
            fromUserName?.let { putExtra("fromUserName", it) }
            fromUserProfilePic?.let { putExtra("fromUserProfilePic", it) }
        }

        val requestCode = uniqueNotificationKey.hashCode()

        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smallIcon = when (type) {

            "chat_message" ->
                R.drawable.ic_notification_message

            "trade_request" ->
                R.drawable.handshake2

            "friend_request" ->
                R.drawable.ic_add_friend

            "friend_accept" ->
                R.drawable.ic_accept

            "trade_completed_clicked" ->
                R.drawable.ic_notification_handshake

            "trade_rated" ->
                R.drawable.ic_notification_star

            "receipt_created" ->
                R.drawable.ic_notification_receipt

            else ->
                R.drawable.ic_notification
        }

        val notificationId = uniqueNotificationKey.hashCode()

        val senderImageUrl = when (type) {
            "chat_message" -> partnerProfilePic ?: fromUserProfilePic
            "friend_request", "friend_accept" -> fromUserProfilePic
            else -> fromUserProfilePic ?: partnerProfilePic
        }

        fun showBuiltNotification(largeIcon: Bitmap? = null) {
            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(smallIcon)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            if (type == "chat_message" && largeIcon != null) {
                val senderName = fromUserName ?: partnerName ?: title

                val person = androidx.core.app.Person.Builder()
                    .setName(senderName)
                    .setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(largeIcon))
                    .build()

                builder
                    .setContentTitle(senderName)
                    .setContentText(message)
                    .setStyle(
                        NotificationCompat.MessagingStyle(person)
                            .setConversationTitle(senderName)
                            .addMessage(message, System.currentTimeMillis(), person)
                    )
            } else {
                builder
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))

                if (largeIcon != null) {
                    builder.setLargeIcon(largeIcon)
                }
            }

            try {
                NotificationManagerCompat.from(this).notify(notificationId, builder.build())
                Log.d("FCM_DEBUG", "Notification shown. type=$type, notificationId=$notificationId")
            } catch (e: SecurityException) {
                Log.e("FCM_DEBUG", "SecurityException while showing notification: ${e.message}")
            } catch (e: Exception) {
                Log.e("FCM_DEBUG", "Error while showing notification: ${e.message}")
            }
        }

        if (!senderImageUrl.isNullOrBlank() && senderImageUrl != "null") {
            Glide.with(applicationContext)
                .asBitmap()
                .load(senderImageUrl)
                .circleCrop()
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        showBuiltNotification(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        showBuiltNotification(null)
                    }
                })
        } else {
            showBuiltNotification(null)
        }
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val existing = manager.getNotificationChannel(channelId)
        if (existing != null) return

        val channel = NotificationChannel(
            channelId,
            "BarterHub Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Chat, trade, friend request, and system notifications"
        }

        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "barterhub_general_notifications"
    }
}
