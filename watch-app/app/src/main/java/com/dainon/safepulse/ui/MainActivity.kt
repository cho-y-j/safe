package com.dainon.safepulse.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import android.widget.LinearLayout
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.dainon.safepulse.R
import com.dainon.safepulse.data.ServerClient
import com.dainon.safepulse.service.AlertService
import com.dainon.safepulse.service.BleAlertService
import com.dainon.safepulse.service.SensorService
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var tvStatus: TextView
    private lateinit var tvHeartRate: TextView
    private lateinit var tvSpO2: TextView
    private lateinit var tvRiskLevel: TextView
    private lateinit var tvRecommendation: TextView
    private lateinit var tvConnection: TextView
    private lateinit var tvLastAlert: TextView
    private lateinit var alertBanner: LinearLayout

    private val sensorReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val hr = intent.getIntExtra("heartRate", 0)
            val spo2 = intent.getIntExtra("spo2", 0)
            val connected = intent.getBooleanExtra("connected", false)

            tvHeartRate.text = if (hr > 0) "$hr" else "--"
            tvSpO2.text = if (spo2 > 0) "$spo2%" else "--%"
            tvConnection.text = if (connected) "● 서버 연결" else "○ 연결 대기"
            tvConnection.setTextColor(
                if (connected) 0xFF66BB6A.toInt() else 0xFFFFB74D.toInt()
            )
        }
    }

    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val level = intent.getStringExtra("level") ?: "info"
            val message = intent.getStringExtra("message") ?: ""

            alertBanner.visibility = View.VISIBLE
            tvLastAlert.text = message

            val color = when (level) {
                "danger" -> 0xFFE53935.toInt()
                "warning" -> 0xFFFF9800.toInt()
                else -> 0xFF42A5F5.toInt()
            }
            alertBanner.setBackgroundColor(color)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvHeartRate = findViewById(R.id.tvHeartRate)
        tvSpO2 = findViewById(R.id.tvSpO2)
        tvRiskLevel = findViewById(R.id.tvRiskLevel)
        tvRecommendation = findViewById(R.id.tvRecommendation)
        tvConnection = findViewById(R.id.tvConnection)
        tvLastAlert = findViewById(R.id.tvLastAlert)
        alertBanner = findViewById(R.id.alertBanner)

        findViewById<Button>(R.id.btnAlerts).setOnClickListener {
            startActivity(Intent(this, AlertListActivity::class.java))
        }

        requestPermissions()
        startServices()
        startBle()
        loadAIInsight()
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val needed = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }

    private fun startServices() {
        startForegroundService(Intent(this, SensorService::class.java))
        startService(Intent(this, AlertService::class.java))
    }

    private fun startBle() {
        // BLE 정상 상태 광고 시작 + 주변 스캔
        BleAlertService.startNormalAdvertise(this, SensorService.WORKER_ID)
        BleAlertService.startScanning(this)
    }

    private fun loadAIInsight() {
        scope.launch {
            while (isActive) {
                try {
                    val risk = ServerClient.getAIInsight()
                    if (risk != null) {
                        tvRiskLevel.text = risk.level
                        tvRiskLevel.setTextColor(when (risk.level) {
                            "위험" -> 0xFFE53935.toInt()
                            "경고" -> 0xFFFF9800.toInt()
                            "주의" -> 0xFF42A5F5.toInt()
                            else -> 0xFF66BB6A.toInt()
                        })
                        tvRecommendation.text = risk.recommendations.firstOrNull() ?: "정상 운영"
                        tvStatus.text = risk.summary
                    }
                } catch (_: Exception) {}
                delay(30000) // 30초마다 갱신
            }
        }
    }

    // P2P BLE 경보 수신
    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val workerId = intent.getStringExtra("workerId") ?: "?"
            val distance = intent.getDoubleExtra("distance", 99.0)
            val intensity = intent.getIntExtra("intensity", 0)

            alertBanner.visibility = View.VISIBLE
            alertBanner.setBackgroundColor(0xFFE53935.toInt())
            tvLastAlert.text = "🚨 $workerId 긴급! ${String.format("%.1f", distance)}m | 강도 $intensity%"
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(sensorReceiver, IntentFilter("com.dainon.safepulse.SENSOR_UPDATE"),
            RECEIVER_NOT_EXPORTED)
        registerReceiver(alertReceiver, IntentFilter("com.dainon.safepulse.NEW_ALERT"),
            RECEIVER_NOT_EXPORTED)
        registerReceiver(p2pReceiver, IntentFilter("com.dainon.safepulse.P2P_ALERT"),
            RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(sensorReceiver)
        unregisterReceiver(alertReceiver)
        unregisterReceiver(p2pReceiver)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
