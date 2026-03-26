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

    // 상태바
    private lateinit var tvConnection: TextView
    private lateinit var tvWorkTime: TextView
    private lateinit var tvStateLabel: TextView

    // 3화면
    private lateinit var idleView: LinearLayout
    private lateinit var workingView: LinearLayout
    private lateinit var restingView: LinearLayout

    // 대기 화면
    private lateinit var btnWorkStart: Button

    // 작업 화면
    private lateinit var tvHeartRate: TextView
    private lateinit var tvSpO2: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvRiskLevel: TextView
    private lateinit var tvRecommendation: TextView
    private lateinit var alertBanner: LinearLayout
    private lateinit var tvLastAlert: TextView
    private lateinit var btnRest: Button
    private lateinit var btnWorkEnd: Button
    private lateinit var btnAlerts: Button
    private lateinit var learningCard: LinearLayout
    private lateinit var baselineCard: LinearLayout
    private lateinit var tvLearningTitle: TextView
    private lateinit var tvLearningTime: TextView
    private lateinit var tvLearningSamples: TextView
    private lateinit var tvLearningNote: TextView
    private lateinit var pbLearning: ProgressBar
    private lateinit var tvBaselineHR: TextView
    private lateinit var tvBaselineUpdate: TextView

    // 휴식 화면
    private lateinit var tvRestTime: TextView
    private lateinit var tvRestTarget: TextView
    private lateinit var btnRestEnd: Button

    // 확인 오버레이
    private lateinit var ackOverlay: LinearLayout
    private lateinit var tvAckTitle: TextView
    private lateinit var tvAckMessage: TextView
    private lateinit var tvAckTimer: TextView
    private lateinit var btnAckOk: Button

    // 작업/휴식 상태
    enum class WorkMode { IDLE, WORKING, RESTING }
    private var workMode = WorkMode.IDLE
    private var workStartTime = 0L
    private var totalWorkMs = 0L       // 누적 작업 시간
    private var restStartTime = 0L
    private var totalRestMs = 0L       // 누적 휴식 시간
    private var lastRestResetTime = 0L // 마지막 휴식 리셋 시점

    // 법정 휴식: 4시간(240분) 근무 → 30분 휴식
    private val LEGAL_WORK_BEFORE_REST_MS = 4 * 60 * 60 * 1000L  // 4시간
    private val LEGAL_REST_DURATION_MS = 30 * 60 * 1000L          // 30분
    // 테스트: 3분 근무 → 1분 휴식
    // private val LEGAL_WORK_BEFORE_REST_MS = 3 * 60 * 1000L
    // private val LEGAL_REST_DURATION_MS = 1 * 60 * 1000L
    private var legalRestNotified = false

    // 학습
    private var learningStartTime = 0L
    private val LEARNING_DURATION_MS = 10 * 60 * 1000L
    private var isBaselineComplete = false

    // ═══════════════════════════════════════
    // 브로드캐스트 수신
    // ═══════════════════════════════════════

    private val sensorReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val hr = intent.getIntExtra("heartRate", 0)
            val spo2 = intent.getIntExtra("spo2", 0)
            val connected = intent.getBooleanExtra("connected", false)
            val baselineReady = intent.getBooleanExtra("baselineReady", false)
            val baselineHr = intent.getIntExtra("baselineHr", 0)
            val stateKr = intent.getStringExtra("stateKr") ?: "정상"

            tvHeartRate.text = if (hr > 0) "$hr" else "--"
            tvSpO2.text = if (spo2 > 0) "$spo2%" else "--%"
            tvConnection.text = if (connected) "●" else "○"
            tvConnection.setTextColor(if (connected) 0xFF66BB6A.toInt() else 0xFFFFB74D.toInt())

            tvStateLabel.text = stateKr
            val stateColor = when (intent.getStringExtra("state")) {
                "EMERGENCY" -> 0xFFE53935.toInt()
                "FALL_DETECTED", "MILD_ANOMALY", "WAITING_ACK" -> 0xFFFF9800.toInt()
                "WATCH_REMOVED" -> 0xFF5A7A96.toInt()
                else -> 0xFF43A047.toInt()
            }
            tvStateLabel.setTextColor(stateColor)

            // 확인 오버레이
            val state = intent.getStringExtra("state") ?: ""
            if (state == "WAITING_ACK" || state == "FALL_DETECTED") {
                showAckOverlay(state)
            } else {
                ackOverlay.visibility = View.GONE
            }

            updateLearningUI(baselineReady, baselineHr, hr)
        }
    }

    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val message = intent.getStringExtra("message") ?: ""
            val level = intent.getStringExtra("level") ?: "info"
            alertBanner.visibility = View.VISIBLE
            tvLastAlert.text = message
            alertBanner.setBackgroundColor(when (level) {
                "danger" -> 0xFFE53935.toInt()
                "warning" -> 0xFFFF9800.toInt()
                else -> 0xFF1E3044.toInt()
            })
        }
    }

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val workerId = intent.getStringExtra("workerId") ?: "?"
            val distance = intent.getDoubleExtra("distance", 99.0)
            val intensity = intent.getIntExtra("intensity", 0)
            alertBanner.visibility = View.VISIBLE
            alertBanner.setBackgroundColor(0xFFE53935.toInt())
            tvLastAlert.text = "🚨 $workerId 긴급! ${"%.0f".format(distance)}m"
        }
    }

    // ═══════════════════════════════════════
    // onCreate
    // ═══════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 상태바
        tvConnection = findViewById(R.id.tvConnection)
        tvWorkTime = findViewById(R.id.tvWorkTime)
        tvStateLabel = findViewById(R.id.tvStateLabel)

        // 3화면
        idleView = findViewById(R.id.idleView)
        workingView = findViewById(R.id.workingView)
        restingView = findViewById(R.id.restingView)

        // 대기
        btnWorkStart = findViewById(R.id.btnWorkStart)

        // 작업
        tvHeartRate = findViewById(R.id.tvHeartRate)
        tvSpO2 = findViewById(R.id.tvSpO2)
        tvStatus = findViewById(R.id.tvStatus)
        tvRiskLevel = findViewById(R.id.tvRiskLevel)
        tvRecommendation = findViewById(R.id.tvRecommendation)
        alertBanner = findViewById(R.id.alertBanner)
        tvLastAlert = findViewById(R.id.tvLastAlert)
        btnRest = findViewById(R.id.btnRest)
        btnWorkEnd = findViewById(R.id.btnWorkEnd)
        btnAlerts = findViewById(R.id.btnAlerts)
        learningCard = findViewById(R.id.learningCard)
        baselineCard = findViewById(R.id.baselineCard)
        tvLearningTitle = findViewById(R.id.tvLearningTitle)
        tvLearningTime = findViewById(R.id.tvLearningTime)
        tvLearningSamples = findViewById(R.id.tvLearningSamples)
        tvLearningNote = findViewById(R.id.tvLearningNote)
        pbLearning = findViewById(R.id.pbLearning)
        tvBaselineHR = findViewById(R.id.tvBaselineHR)
        tvBaselineUpdate = findViewById(R.id.tvBaselineUpdate)

        // 휴식
        tvRestTime = findViewById(R.id.tvRestTime)
        tvRestTarget = findViewById(R.id.tvRestTarget)
        btnRestEnd = findViewById(R.id.btnRestEnd)

        // 확인 오버레이
        ackOverlay = findViewById(R.id.ackOverlay)
        tvAckTitle = findViewById(R.id.tvAckTitle)
        tvAckMessage = findViewById(R.id.tvAckMessage)
        tvAckTimer = findViewById(R.id.tvAckTimer)
        btnAckOk = findViewById(R.id.btnAckOk)

        // ═══ 버튼 이벤트 ═══

        btnWorkStart.setOnClickListener { startWork() }
        btnRest.setOnClickListener { startRest() }
        btnWorkEnd.setOnClickListener { endWork() }
        btnRestEnd.setOnClickListener { endRest() }
        btnAlerts.setOnClickListener { startActivity(Intent(this, AlertListActivity::class.java)) }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java).putExtra("forceSetup", true))
        }
        btnAckOk.setOnClickListener {
            ackOverlay.visibility = View.GONE
            startService(Intent(this, SensorService::class.java).apply { action = SensorService.ACTION_ACKNOWLEDGE })
        }

        // 이전 상태 복원
        val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
        isBaselineComplete = prefs.getBoolean("baselineComplete", false)
        val savedWorkMode = prefs.getString("workMode", "IDLE")
        workStartTime = prefs.getLong("workStartTime", 0L)
        totalWorkMs = prefs.getLong("totalWorkMs", 0L)

        when (savedWorkMode) {
            "WORKING" -> { workMode = WorkMode.WORKING; showWorkingView() }
            "RESTING" -> { restStartTime = prefs.getLong("restStartTime", System.currentTimeMillis()); workMode = WorkMode.RESTING; showRestingView() }
            else -> showIdleView()
        }

        requestPermissions()
        loadAIInsight()
        startTimerLoop()
    }

    // ═══════════════════════════════════════
    // 작업/휴식 모드 전환
    // ═══════════════════════════════════════

    private fun startWork() {
        workMode = WorkMode.WORKING
        workStartTime = System.currentTimeMillis()
        totalWorkMs = 0L
        totalRestMs = 0L
        lastRestResetTime = System.currentTimeMillis()
        legalRestNotified = false
        learningStartTime = System.currentTimeMillis()

        val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
        prefs.edit()
            .putString("workMode", "WORKING")
            .putLong("workStartTime", workStartTime)
            .putLong("learningStart", learningStartTime)
            .apply()

        showWorkingView()
        startServices()
        startBle()

        if (!isBaselineComplete) startLearningTimer()
    }

    private fun startRest() {
        workMode = WorkMode.RESTING
        restStartTime = System.currentTimeMillis()
        // 작업 시간 누적
        totalWorkMs += System.currentTimeMillis() - (workStartTime.takeIf { it > 0 } ?: System.currentTimeMillis())

        getSharedPreferences("safepulse", MODE_PRIVATE).edit()
            .putString("workMode", "RESTING")
            .putLong("restStartTime", restStartTime)
            .putLong("totalWorkMs", totalWorkMs)
            .apply()

        showRestingView()
    }

    private fun endRest() {
        val restDuration = System.currentTimeMillis() - restStartTime
        totalRestMs += restDuration

        // 법정 휴식 30분 충족 → 작업 타이머 리셋
        if (totalRestMs >= LEGAL_REST_DURATION_MS) {
            lastRestResetTime = System.currentTimeMillis()
            totalWorkMs = 0L
            legalRestNotified = false
        }

        workMode = WorkMode.WORKING
        workStartTime = System.currentTimeMillis()

        getSharedPreferences("safepulse", MODE_PRIVATE).edit()
            .putString("workMode", "WORKING")
            .putLong("workStartTime", workStartTime)
            .putLong("totalWorkMs", totalWorkMs)
            .apply()

        showWorkingView()
    }

    private fun endWork() {
        workMode = WorkMode.IDLE
        getSharedPreferences("safepulse", MODE_PRIVATE).edit()
            .putString("workMode", "IDLE")
            .apply()
        showIdleView()
    }

    // ═══════════════════════════════════════
    // 화면 전환
    // ═══════════════════════════════════════

    private fun showIdleView() {
        idleView.visibility = View.VISIBLE
        workingView.visibility = View.GONE
        restingView.visibility = View.GONE
        tvWorkTime.text = ""
        tvStateLabel.text = "대기"
        tvStateLabel.setTextColor(0xFF7A8FA3.toInt())
    }

    private fun showWorkingView() {
        idleView.visibility = View.GONE
        workingView.visibility = View.VISIBLE
        restingView.visibility = View.GONE
    }

    private fun showRestingView() {
        idleView.visibility = View.GONE
        workingView.visibility = View.GONE
        restingView.visibility = View.VISIBLE
    }

    // ═══════════════════════════════════════
    // 타이머 루프 (1초마다)
    // ═══════════════════════════════════════

    private fun startTimerLoop() {
        scope.launch {
            while (isActive) {
                delay(1000)
                when (workMode) {
                    WorkMode.WORKING -> {
                        val elapsed = totalWorkMs + (System.currentTimeMillis() - workStartTime)
                        val hours = (elapsed / 3600000).toInt()
                        val mins = ((elapsed % 3600000) / 60000).toInt()
                        tvWorkTime.text = "${hours}:${String.format("%02d", mins)}"

                        // 법정 휴식 체크
                        if (elapsed >= LEGAL_WORK_BEFORE_REST_MS && !legalRestNotified) {
                            legalRestNotified = true
                            // 워치 알림
                            alertBanner.visibility = View.VISIBLE
                            alertBanner.setBackgroundColor(0xFFFF9800.toInt())
                            tvLastAlert.text = "⚖ 법정 휴식 시간입니다 (근로기준법 제54조)"
                        }

                        // 10분 전 사전 알림
                        val tenMinBefore = LEGAL_WORK_BEFORE_REST_MS - 10 * 60 * 1000
                        if (elapsed in tenMinBefore..(tenMinBefore + 2000) && !legalRestNotified) {
                            alertBanner.visibility = View.VISIBLE
                            alertBanner.setBackgroundColor(0xFF1E3044.toInt())
                            tvLastAlert.text = "⏰ 10분 후 법정 휴식 시간"
                        }
                    }
                    WorkMode.RESTING -> {
                        val elapsed = System.currentTimeMillis() - restStartTime
                        val mins = (elapsed / 60000).toInt()
                        val secs = ((elapsed % 60000) / 1000).toInt()
                        tvRestTime.text = "${mins}:${String.format("%02d", secs)}"

                        val remaining = LEGAL_REST_DURATION_MS - (totalRestMs + elapsed)
                        if (remaining > 0) {
                            val remMin = (remaining / 60000).toInt()
                            tvRestTarget.text = "목표까지 ${remMin}분"
                        } else {
                            tvRestTarget.text = "✅ 법정 휴식 충족!"
                        }
                    }
                    WorkMode.IDLE -> {}
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // 학습 UI
    // ═══════════════════════════════════════

    private fun startLearningTimer() {
        learningCard.visibility = View.VISIBLE
        baselineCard.visibility = View.GONE
        scope.launch {
            while (!isBaselineComplete && isActive) {
                val elapsed = System.currentTimeMillis() - learningStartTime
                val remaining = LEARNING_DURATION_MS - elapsed
                val progress = ((elapsed.toFloat() / LEARNING_DURATION_MS) * 100).toInt().coerceIn(0, 100)
                if (remaining <= 0) {
                    isBaselineComplete = true
                    getSharedPreferences("safepulse", MODE_PRIVATE).edit()
                        .putBoolean("baselineComplete", true)
                        .putInt("baselineHR", SensorService.lastBaselineHR)
                        .apply()
                    showBaselineComplete(SensorService.lastBaselineHR)
                    break
                }
                pbLearning.progress = progress
                tvLearningTime.text = "${(remaining/60000).toInt()}:${String.format("%02d", ((remaining%60000)/1000).toInt())}"
                tvLearningSamples.text = "${(elapsed/5000).toInt()} 샘플"
                tvLearningTitle.text = if (progress < 50) "학습 중..." else "패턴 분석..."
                delay(1000)
            }
        }
    }

    private fun showBaselineComplete(baselineHR: Int) {
        learningCard.visibility = View.GONE
        baselineCard.visibility = View.VISIBLE
        tvBaselineHR.text = "평균: ${if (baselineHR > 0) baselineHR else "--"}bpm"
    }

    private fun updateLearningUI(baselineReady: Boolean, baselineHr: Int, currentHr: Int) {
        if (baselineReady && baselineHr > 0) {
            if (!isBaselineComplete) {
                isBaselineComplete = true
                getSharedPreferences("safepulse", MODE_PRIVATE).edit()
                    .putBoolean("baselineComplete", true).putInt("baselineHR", baselineHr).apply()
            }
            showBaselineComplete(baselineHr)
            tvBaselineHR.text = "안정:${baselineHr} 현재:${currentHr}"
        }
    }

    private fun showAckOverlay(state: String) {
        ackOverlay.visibility = View.VISIBLE
        tvAckTitle.text = if (state == "FALL_DETECTED") "넘어지셨나요?" else "괜찮으세요?"
        tvAckMessage.text = if (state == "FALL_DETECTED") "낙상 감지" else "이상 징후 감지"
        tvAckTitle.setTextColor(if (state == "FALL_DETECTED") 0xFFE53935.toInt() else 0xFFFF9800.toInt())
        tvAckTimer.text = "5초 후 주변 경보"
    }

    // ═══════════════════════════════════════
    // 권한 + 서비스
    // ═══════════════════════════════════════

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val needed = perms.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        } else {
            // 권한 있으면 항상 BLE 시작 (폰이 워치를 찾을 수 있게)
            startBle()
            if (workMode == WorkMode.WORKING) startServices()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            startBle()  // 항상 BLE 광고
            if (workMode == WorkMode.WORKING) startServices()
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
                            "위험" -> 0xFFE53935.toInt(); "경고" -> 0xFFFF9800.toInt()
                            "주의" -> 0xFF42A5F5.toInt(); else -> 0xFF66BB6A.toInt()
                        })
                        tvRecommendation.text = risk.recommendations.firstOrNull() ?: ""
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
        try { unregisterReceiver(sensorReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(alertReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(p2pReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
