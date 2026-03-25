package com.dainon.safepulse.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
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

    // 학습 UI
    private lateinit var learningCard: LinearLayout
    private lateinit var baselineCard: LinearLayout
    private lateinit var tvLearningTitle: TextView
    private lateinit var tvLearningTime: TextView
    private lateinit var tvLearningSamples: TextView
    private lateinit var tvLearningNote: TextView
    private lateinit var pbLearning: ProgressBar
    private lateinit var tvBaselineHR: TextView
    private lateinit var tvBaselineUpdate: TextView

    private val sensorReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val hr = intent.getIntExtra("heartRate", 0)
            val spo2 = intent.getIntExtra("spo2", 0)
            val connected = intent.getBooleanExtra("connected", false)
            val baselineReady = intent.getBooleanExtra("baselineReady", false)
            val baselineHr = intent.getIntExtra("baselineHr", 0)
            val stateKr = intent.getStringExtra("stateKr") ?: "정상"
            val noMovementSec = intent.getIntExtra("noMovementSec", 0)

            // 심박/SpO₂
            tvHeartRate.text = if (hr > 0) "$hr" else "--"
            tvSpO2.text = if (spo2 > 0) "$spo2%" else "--%"
            tvConnection.text = if (connected) "● 서버 연결" else "○ 연결 대기"
            tvConnection.setTextColor(if (connected) 0xFF66BB6A.toInt() else 0xFFFFB74D.toInt())

            // 학습 상태 업데이트
            updateLearningUI(baselineReady, baselineHr, hr)
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

    // 학습 타이머
    private var learningStartTime = 0L
    private val LEARNING_DURATION_MS = 10 * 60 * 1000L  // 10분 (테스트용)
    private var isBaselineComplete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 기본 UI
        tvStatus = findViewById(R.id.tvStatus)
        tvHeartRate = findViewById(R.id.tvHeartRate)
        tvSpO2 = findViewById(R.id.tvSpO2)
        tvRiskLevel = findViewById(R.id.tvRiskLevel)
        tvRecommendation = findViewById(R.id.tvRecommendation)
        tvConnection = findViewById(R.id.tvConnection)
        tvLastAlert = findViewById(R.id.tvLastAlert)
        alertBanner = findViewById(R.id.alertBanner)

        // 학습 UI
        learningCard = findViewById(R.id.learningCard)
        baselineCard = findViewById(R.id.baselineCard)
        tvLearningTitle = findViewById(R.id.tvLearningTitle)
        tvLearningTime = findViewById(R.id.tvLearningTime)
        tvLearningSamples = findViewById(R.id.tvLearningSamples)
        tvLearningNote = findViewById(R.id.tvLearningNote)
        pbLearning = findViewById(R.id.pbLearning)
        tvBaselineHR = findViewById(R.id.tvBaselineHR)
        tvBaselineUpdate = findViewById(R.id.tvBaselineUpdate)

        findViewById<Button>(R.id.btnAlerts).setOnClickListener {
            startActivity(Intent(this, AlertListActivity::class.java))
        }

        // 이전 학습 완료 상태 복원
        val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
        isBaselineComplete = prefs.getBoolean("baselineComplete", false)
        learningStartTime = prefs.getLong("learningStart", System.currentTimeMillis())

        if (isBaselineComplete) {
            showBaselineComplete(prefs.getInt("baselineHR", 75))
        } else {
            if (learningStartTime == 0L) {
                learningStartTime = System.currentTimeMillis()
                prefs.edit().putLong("learningStart", learningStartTime).apply()
            }
            startLearningTimer()
        }

        requestPermissions()
        loadAIInsight()
    }

    // ═══ 학습 타이머 ═══

    private fun startLearningTimer() {
        learningCard.visibility = View.VISIBLE
        baselineCard.visibility = View.GONE

        scope.launch {
            while (!isBaselineComplete && isActive) {
                val elapsed = System.currentTimeMillis() - learningStartTime
                val remaining = LEARNING_DURATION_MS - elapsed
                val progress = ((elapsed.toFloat() / LEARNING_DURATION_MS) * 100).toInt().coerceIn(0, 100)

                if (remaining <= 0) {
                    // 학습 완료!
                    isBaselineComplete = true
                    val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("baselineComplete", true)
                        .putInt("baselineHR", SensorService.lastBaselineHR)
                        .apply()
                    showBaselineComplete(SensorService.lastBaselineHR)
                    break
                }

                val min = (remaining / 60000).toInt()
                val sec = ((remaining % 60000) / 1000).toInt()
                val samples = (elapsed / 5000).toInt()  // 5초 간격

                pbLearning.progress = progress
                tvLearningTime.text = "남은 시간: ${min}분 ${String.format("%02d", sec)}초"
                tvLearningSamples.text = "수집된 샘플: ${samples}개"

                if (progress < 30) {
                    tvLearningTitle.text = "베이스라인 학습 중..."
                    tvLearningNote.text = "학습 전에는 기본 데이터로 작동합니다"
                } else if (progress < 70) {
                    tvLearningTitle.text = "패턴 분석 중..."
                    tvLearningNote.text = "개인 생체 패턴을 추출하고 있습니다"
                } else {
                    tvLearningTitle.text = "베이스라인 확정 중..."
                    tvLearningNote.text = "95% 신뢰구간을 계산하고 있습니다"
                }

                delay(1000)
            }
        }
    }

    private fun showBaselineComplete(baselineHR: Int) {
        learningCard.visibility = View.GONE
        baselineCard.visibility = View.VISIBLE
        tvBaselineHR.text = "내 평균 심박: ${if (baselineHR > 0) baselineHR else "--"}bpm"
        tvBaselineUpdate.text = "매일 자동 업데이트됩니다 (EMA 방식)"
    }

    private fun updateLearningUI(baselineReady: Boolean, baselineHr: Int, currentHr: Int) {
        // 베이스라인 완료 시 → 학습 카드 숨기고 결과 표시
        if (baselineReady && baselineHr > 0) {
            if (!isBaselineComplete) {
                isBaselineComplete = true
                val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("baselineComplete", true)
                    .putInt("baselineHR", baselineHr)
                    .apply()
            }
            showBaselineComplete(baselineHr)
            // 실시간 업데이트
            tvBaselineHR.text = "안정 시 평균: ${baselineHr}bpm (현재: ${currentHr})"
        }
    }

    // ═══ 권한 + 서비스 ═══

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val needed = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        } else {
            startServices()
            startBle()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            startServices()
            startBle()
        }
    }

    private fun startServices() {
        startForegroundService(Intent(this, SensorService::class.java))
        startService(Intent(this, AlertService::class.java))
    }

    private fun startBle() {
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
                delay(30000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(sensorReceiver, IntentFilter("com.dainon.safepulse.SENSOR_UPDATE"), RECEIVER_NOT_EXPORTED)
        registerReceiver(alertReceiver, IntentFilter("com.dainon.safepulse.NEW_ALERT"), RECEIVER_NOT_EXPORTED)
        registerReceiver(p2pReceiver, IntentFilter("com.dainon.safepulse.P2P_ALERT"), RECEIVER_NOT_EXPORTED)
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
