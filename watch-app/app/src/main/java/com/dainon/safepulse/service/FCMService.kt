package com.dainon.safepulse.service

import android.content.Intent
import android.os.*
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 푸시 수신 — 관제센터/AI에서 보내는 알림
 * - 휴식 권고, 혼잡 예측, 대기질 경고 등
 * - 앱이 꺼져있어도 수신
 */
class FCMService : FirebaseMessagingService() {

    companion object {
        const val TAG = "FCM"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token: $token")
        // 서버에 토큰 등록 (작업자 ID와 매핑)
        sendBroadcast(Intent("com.dainon.safepulse.FCM_TOKEN").apply {
            putExtra("token", token)
        })
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "SafePulse"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val type = message.data["type"] ?: "info"  // info, warning, danger, rest, congestion
        val action = message.data["action"] ?: ""

        Log.d(TAG, "📩 Push: [$type] $title - $body")

        // 진동
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        when (type) {
            "danger" -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 100, 300, 100, 500), -1))
            "warning" -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
            "rest" -> vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        }

        // UI에 브로드캐스트
        sendBroadcast(Intent("com.dainon.safepulse.PUSH_MESSAGE").apply {
            putExtra("title", title)
            putExtra("body", body)
            putExtra("type", type)
        })
    }
}
