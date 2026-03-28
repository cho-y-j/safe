package com.dainon.safepulse.ui

import android.app.NotificationManager
import android.content.*
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
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
    private var wakeLock: PowerManager.WakeLock? = null

    // 거리 실시간 갱신 브로드캐스트 수신
    private val distanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val dist = intent.getDoubleExtra("distance", 99.0)
            val zone = intent.getStringExtra("zone") ?: "ZONE3"
            updateDistance(dist, zone)
        }
    }

    // ★ 발신자 경보 해제 시 자동 닫기
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            android.util.Log.d("P2pAlert", "경보 해제 → 화면 닫기")
            clearNotification()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ★ 화면 꺼지지 않게 유지 (긴급 상황)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "safepulse:p2p_alert")
        wakeLock?.acquire(5 * 60 * 1000L)

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

    // ★ 뒤로가기 차단 — "응답함"/"도움불가" 눌러야만 닫힘
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // 무시
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
        registerReceiver(closeReceiver, IntentFilter("com.dainon.safepulse.CLOSE_P2P"), RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(distanceReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(closeReceiver) } catch (_: Exception) {}
    }

    private fun clearNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(999)
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
