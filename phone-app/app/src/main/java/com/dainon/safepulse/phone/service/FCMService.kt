package com.dainon.safepulse.phone.service

import android.app.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dainon.safepulse.phone.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 푸시 수신 (폰 앱) — 관제센터/AI 알림
 */
class FCMService : FirebaseMessagingService() {

    companion object {
        const val TAG = "FCM"
        const val CHANNEL_ID = "safepulse_push"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "SafePulse"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val type = message.data["type"] ?: "info"

        Log.d(TAG, "📩 Push: [$type] $title - $body")

        // 알림 채널
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "관제센터 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
        )

        // 알림 표시
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val color = when (type) {
            "danger" -> 0xFFE53935.toInt()
            "warning" -> 0xFFFF9800.toInt()
            "rest" -> 0xFF43A047.toInt()
            else -> 0xFF2E75B6.toInt()
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setColor(color)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)

        // 앱 UI에도 전달
        sendBroadcast(Intent("com.dainon.safepulse.companion.PUSH_MESSAGE").apply {
            putExtra("title", title)
            putExtra("body", body)
            putExtra("type", type)
        })
    }
}
