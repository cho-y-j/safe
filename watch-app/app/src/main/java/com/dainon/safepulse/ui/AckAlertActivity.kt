package com.dainon.safepulse.ui

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // 즉시 호출 (바로 P2P + 서버 경보)
        findViewById<Button>(R.id.btnEmergency).setOnClickListener {
            startService(Intent(this, SensorService::class.java).apply {
                action = SensorService.ACTION_MANUAL_EMERGENCY
            })
            clearNotification()
            finish()
        }
    }

    private fun clearNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(998)
    }
}
