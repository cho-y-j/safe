package com.dainon.safepulse.ui

import android.app.NotificationManager
import android.content.*
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dainon.safepulse.R
import com.dainon.safepulse.service.BleAlertService

/**
 * 수신자 워치 전체화면 P2P 긴급 경보
 * fullScreenIntent로 실행 → 화면 자동 켜짐
 * 거리 브로드캐스트 수신으로 실시간 갱신
 */
class P2pAlertActivity : AppCompatActivity() {

    private var currentWorkerId = "?"

    // 거리 실시간 갱신 브로드캐스트 수신
    private val distanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val dist = intent.getDoubleExtra("distance", 99.0)
            val zone = intent.getStringExtra("zone") ?: "ZONE3"
            updateDistance(dist, zone)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_p2p_alert)

        currentWorkerId = intent.getStringExtra("workerId") ?: "?"
        val workerName = intent.getStringExtra("workerName") ?: currentWorkerId
        val distance = intent.getDoubleExtra("distance", 99.0)
        val zone = intent.getStringExtra("zone") ?: "ZONE3"

        findViewById<TextView>(R.id.tvAlertName).text = workerName
        updateDistance(distance, zone)

        findViewById<Button>(R.id.btnRespond).setOnClickListener {
            BleAlertService.dismissReceivedAlertFull(currentWorkerId)
            clearNotification()
            finish()
        }

        findViewById<Button>(R.id.btnCantHelp).setOnClickListener {
            BleAlertService.dismissReceivedAlertFull(currentWorkerId)
            clearNotification()
            finish()
        }
    }

    private fun updateDistance(distance: Double, zone: String) {
        val distText = "~${"%.0f".format(distance)}m"
        val distColor = when (zone) {
            "ZONE1" -> 0xFFFF1744.toInt()
            "ZONE2" -> 0xFFFF5722.toInt()
            "ZONE3" -> 0xFFFFEB3B.toInt()
            "ZONE4" -> 0xFF42A5F5.toInt()
            else -> 0xFFBDBDBD.toInt()
        }
        findViewById<TextView>(R.id.tvAlertDistance).apply {
            text = distText
            setTextColor(distColor)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(distanceReceiver, IntentFilter("com.dainon.safepulse.P2P_DISTANCE"), RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(distanceReceiver) } catch (_: Exception) {}
    }

    private fun clearNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(999)
    }
}
