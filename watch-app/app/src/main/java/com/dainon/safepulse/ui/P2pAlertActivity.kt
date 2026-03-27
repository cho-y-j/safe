package com.dainon.safepulse.ui

import android.app.NotificationManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dainon.safepulse.R
import com.dainon.safepulse.service.BleAlertService

/**
 * 수신자 워치 전체화면 P2P 긴급 경보
 * fullScreenIntent로 실행 → 화면 자동 켜짐
 * "응답함" 또는 "도움불가" 누르면 종료
 */
class P2pAlertActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_p2p_alert)

        val workerId = intent.getStringExtra("workerId") ?: "?"
        val workerName = intent.getStringExtra("workerName") ?: workerId
        val distance = intent.getDoubleExtra("distance", 99.0)
        val zone = intent.getStringExtra("zone") ?: "NEAR"

        findViewById<TextView>(R.id.tvAlertName).text = workerName

        val distText = when (zone) {
            "IMMEDIATE" -> "⚡ ${"%.0f".format(distance)}m"
            "NEAR" -> "📍 ${"%.0f".format(distance)}m"
            else -> "📡 ${"%.0f".format(distance)}m"
        }
        val distColor = when (zone) {
            "IMMEDIATE" -> 0xFFFF1744.toInt()
            "NEAR" -> 0xFFFFEB3B.toInt()
            else -> 0xFFBDBDBD.toInt()
        }
        findViewById<TextView>(R.id.tvAlertDistance).apply {
            text = distText
            setTextColor(distColor)
        }

        findViewById<Button>(R.id.btnRespond).setOnClickListener {
            BleAlertService.dismissReceivedAlert(workerId)
            clearNotification()
            finish()
        }

        findViewById<Button>(R.id.btnCantHelp).setOnClickListener {
            BleAlertService.dismissReceivedAlert(workerId)
            clearNotification()
            finish()
        }
    }

    private fun clearNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(999)
    }
}
