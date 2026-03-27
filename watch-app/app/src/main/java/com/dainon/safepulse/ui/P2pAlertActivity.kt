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

        val distText = "~${"%.0f".format(distance)}m"
        val distColor = when (zone) {
            "ZONE1" -> 0xFFFF1744.toInt()   // 빨강
            "ZONE2" -> 0xFFFF5722.toInt()   // 주황
            "ZONE3" -> 0xFFFFEB3B.toInt()   // 노랑
            "ZONE4" -> 0xFF42A5F5.toInt()   // 파랑
            else -> 0xFFBDBDBD.toInt()       // 회색
        }
        findViewById<TextView>(R.id.tvAlertDistance).apply {
            text = distText
            setTextColor(distColor)
        }

        findViewById<Button>(R.id.btnRespond).setOnClickListener {
            BleAlertService.dismissReceivedAlertFull(workerId)
            clearNotification()
            finish()
        }

        findViewById<Button>(R.id.btnCantHelp).setOnClickListener {
            BleAlertService.dismissReceivedAlertFull(workerId)
            clearNotification()
            finish()
        }
    }

    private fun clearNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(999)
    }
}
