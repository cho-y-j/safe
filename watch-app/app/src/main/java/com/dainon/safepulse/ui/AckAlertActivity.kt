package com.dainon.safepulse.ui

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dainon.safepulse.R
import com.dainon.safepulse.service.SensorService

/**
 * 발신자(사고자) 워치 전체화면 ACK 경보
 * fullScreenIntent로 실행 → 화면 자동 켜짐
 * "괜찮아요" / "오작동 종료" / "즉시 호출"
 */
class AckAlertActivity : AppCompatActivity() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ★ 화면 꺼지지 않게 유지 (긴급 상황)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        // WakeLock으로 OS가 화면 끄는 것 방지
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "safepulse:ack_alert")
        wakeLock?.acquire(5 * 60 * 1000L) // 최대 5분

        setContentView(R.layout.activity_ack_alert)

        val state = intent.getStringExtra("state") ?: "WAITING_ACK"

        val title = when (state) {
            "FALL_DETECTED" -> "넘어지셨나요?"
            "EMERGENCY" -> "긴급 상황"
            else -> "괜찮으세요?"
        }
        val titleColor = when (state) {
            "FALL_DETECTED", "EMERGENCY" -> 0xFFE53935.toInt()
            else -> 0xFFFFB74D.toInt()
        }

        findViewById<TextView>(R.id.tvAckTitle).apply {
            text = title
            setTextColor(titleColor)
        }

        // 괜찮아요 (학습 + 해제)
        findViewById<Button>(R.id.btnOk).setOnClickListener {
            startService(Intent(this, SensorService::class.java).apply {
                action = SensorService.ACTION_ACKNOWLEDGE
            })
            clearNotification()
            finish()
        }

        // 오작동 종료 (학습 안 함 + 해제)
        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            startService(Intent(this, SensorService::class.java).apply {
                action = SensorService.ACTION_DISMISS
            })
            clearNotification()
            finish()
        }

        // 즉시 호출 (바로 P2P + 서버 경보) — 화면 유지! finish 안 함
        findViewById<Button>(R.id.btnEmergency).setOnClickListener {
            startService(Intent(this, SensorService::class.java).apply {
                action = SensorService.ACTION_MANUAL_EMERGENCY
            })
            // ★ finish() 안 함 — EMERGENCY 중 화면 유지
            // 제목을 "긴급 상황"으로 변경
            findViewById<TextView>(R.id.tvAckTitle).apply {
                text = "긴급 상황"
                setTextColor(0xFFE53935.toInt())
            }
        }
    }

    // ★ 뒤로가기 차단 — "괜찮아요"/"오작동 종료" 눌러야만 닫힘
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // 무시 — 긴급 상황에서 뒤로가기로 나가면 안 됨
    }

    private fun clearNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(998)
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
