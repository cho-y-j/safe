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
    private lateinit var tvBodyTemp: TextView
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

    // P2P 수신 경보 오버레이
    private lateinit var p2pOverlay: LinearLayout
    private lateinit var tvP2pWorkerName: TextView
    private lateinit var tvP2pAlertType: TextView
    private lateinit var tvP2pDistance: TextView
    private lateinit var btnP2pRespond: Button
    private lateinit var btnP2pCantHelp: Button
    private var currentP2pWorkerId = ""

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

            val bodyTemp = intent.getDoubleExtra("bodyTemp", 0.0)

            tvHeartRate.text = if (hr > 0) "$hr" else "--"
            tvSpO2.text = if (spo2 > 0) "$spo2%" else "--%"
            tvBodyTemp.text = if (bodyTemp > 30) "${"%.1f".format(bodyTemp)}°" else "--°"
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

            // 확인 오버레이 — EMERGENCY에서도 표시 (경보 해제 가능)
            val state = intent.getStringExtra("state") ?: ""
            if (state == "WAITING_ACK" || state == "FALL_DETECTED" || state == "EMERGENCY") {
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

    // ★ 자동 작업 종료 수신기 (10분 미착용)
    private val autoEndWorkReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            android.util.Log.d("MainActivity", "📴 Auto end work received")
            if (workMode != WorkMode.IDLE) {
                endWork()
                alertBanner.visibility = View.VISIBLE
                alertBanner.setBackgroundColor(0xFF5A7A96.toInt())
                tvLastAlert.text = "워치 미착용 10분 → 작업 자동 종료"
            }
        }
    }

    // ★ 워치 재착용 수신기
    private val watchBackOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            android.util.Log.d("MainActivity", "⌚ Watch back on!")
            if (workMode == WorkMode.IDLE) {
                showIdleView()
                alertBanner.visibility = View.VISIBLE
                alertBanner.setBackgroundColor(0xFF43A047.toInt())
                tvLastAlert.text = "워치 착용 감지 — 작업 시작을 눌러주세요"
            } else if (workMode == WorkMode.WORKING) {
                alertBanner.visibility = View.VISIBLE
                alertBanner.setBackgroundColor(0xFF1E88E5.toInt())
                tvLastAlert.text = "센서 안정화 중 (10초)..."
            }
        }
    }

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val workerId = intent.getStringExtra("workerId") ?: "?"
            val distance = intent.getDoubleExtra("distance", 99.0)
            val intensity = intent.getIntExtra("intensity", 0)
            val zone = intent.getStringExtra("zone") ?: "NEAR"
            currentP2pWorkerId = workerId

            // 작업자 이름 해결 (SharedPreferences workerRegistry)
            val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
            val registry = prefs.getString("workerRegistry", "") ?: ""
            val workerName = resolveWorkerName(workerId, registry)

            // 풀스크린 P2P 오버레이 표시
            p2pOverlay.visibility = View.VISIBLE

            tvP2pWorkerName.text = workerName
            tvP2pAlertType.text = "이상 징후 감지"

            // 거리 구간별 표시 (연구 기반)
            when (zone) {
                "IMMEDIATE" -> {
                    tvP2pDistance.text = "⚡ ${"%.0f".format(distance)}m 즉시근접"
                    tvP2pDistance.setTextColor(0xFFFF1744.toInt())
                }
                "NEAR" -> {
                    tvP2pDistance.text = "📍 ${"%.0f".format(distance)}m 근처"
                    tvP2pDistance.setTextColor(0xFFFFEB3B.toInt())
                }
                "FAR" -> {
                    tvP2pDistance.text = "📡 ${"%.0f".format(distance)}m 멀리"
                    tvP2pDistance.setTextColor(0xFFBDBDBD.toInt())
                }
            }

            // 배너에도 표시
            alertBanner.visibility = View.VISIBLE
            alertBanner.setBackgroundColor(0xFFE53935.toInt())
            tvLastAlert.text = "🚨 $workerName 긴급! ${"%.0f".format(distance)}m"
        }
    }

    /** workerId → 이름 해결 (JSON: "W-001:조영진,W-002:이서준") */
    private fun resolveWorkerName(workerId: String, registry: String): String {
        if (registry.isBlank()) return workerId
        return registry.split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2 && parts[0].trim() == workerId) parts[1].trim() else null
            }
            .firstOrNull() ?: workerId
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
        tvBodyTemp = findViewById(R.id.tvBodyTemp)
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

        // P2P 수신 경보 오버레이
        p2pOverlay = findViewById(R.id.p2pOverlay)
        tvP2pWorkerName = findViewById(R.id.tvP2pWorkerName)
        tvP2pAlertType = findViewById(R.id.tvP2pAlertType)
        tvP2pDistance = findViewById(R.id.tvP2pDistance)
        btnP2pRespond = findViewById(R.id.btnP2pRespond)
        btnP2pCantHelp = findViewById(R.id.btnP2pCantHelp)

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
        findViewById<Button>(R.id.btnAckDismiss).setOnClickListener {
            ackOverlay.visibility = View.GONE
            startService(Intent(this, SensorService::class.java).apply { action = SensorService.ACTION_DISMISS })
        }
        findViewById<Button>(R.id.btnAckEmergency).setOnClickListener {
            ackOverlay.visibility = View.GONE
            startService(Intent(this, SensorService::class.java).apply { action = SensorService.ACTION_MANUAL_EMERGENCY })
        }

        // P2P 수신 경보 버튼
        btnP2pRespond.setOnClickListener {
            BleAlertService.dismissReceivedAlert(currentP2pWorkerId)
            p2pOverlay.visibility = View.GONE
        }
        btnP2pCantHelp.setOnClickListener {
            BleAlertService.dismissReceivedAlert(currentP2pWorkerId)
            p2pOverlay.visibility = View.GONE
        }

        // 이전 상태 복원
        val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
        isBaselineComplete = prefs.getBoolean("baselineComplete", false)
        var savedWorkMode = prefs.getString("workMode", "IDLE")
        workStartTime = prefs.getLong("workStartTime", 0L)
        totalWorkMs = prefs.getLong("totalWorkMs", 0L)

        // ★ 서비스가 죽어있으면 IDLE로 강제 전환 (10분 자동종료 못한 경우)
        val sensorRunning = try {
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.getRunningServices(100).any { it.service.className == "com.dainon.safepulse.service.SensorService" }
        } catch (_: Exception) { false }

        if (savedWorkMode != "IDLE" && !sensorRunning) {
            android.util.Log.d("MainActivity", "서비스 죽어있음 → IDLE 강제 전환")
            savedWorkMode = "IDLE"
            prefs.edit().putString("workMode", "IDLE").apply()
        }

        when (savedWorkMode) {
            "WORKING" -> {
                workMode = WorkMode.WORKING
                showWorkingView()
                requestPermissions()
                loadAIInsight()
                startTimerLoop()
                return
            }
            "RESTING" -> { restStartTime = prefs.getLong("restStartTime", System.currentTimeMillis()); workMode = WorkMode.RESTING; showRestingView() }
            else -> showIdleView()
        }

        requestPermissions()
        loadAIInsight()
        startTimerLoop()
        handleIntentExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent) {
        // P2P 긴급 수신 → 오버레이 직접 표시
        if (intent.getBooleanExtra("p2p_alert", false)) {
            val workerId = intent.getStringExtra("workerId") ?: "?"
            val distance = intent.getDoubleExtra("distance", 99.0)
            val zone = intent.getStringExtra("zone") ?: "NEAR"
            currentP2pWorkerId = workerId

            val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
            val registry = prefs.getString("workerRegistry", "") ?: ""
            val workerName = resolveWorkerName(workerId, registry)

            p2pOverlay.visibility = View.VISIBLE
            tvP2pWorkerName.text = workerName
            when (zone) {
                "IMMEDIATE" -> { tvP2pDistance.text = "⚡ ${"%.0f".format(distance)}m"; tvP2pDistance.setTextColor(0xFFFF1744.toInt()) }
                "NEAR" -> { tvP2pDistance.text = "📍 ${"%.0f".format(distance)}m"; tvP2pDistance.setTextColor(0xFFFFEB3B.toInt()) }
                "FAR" -> { tvP2pDistance.text = "📡 ${"%.0f".format(distance)}m"; tvP2pDistance.setTextColor(0xFFBDBDBD.toInt()) }
            }
        }
        // 발신자 EMERGENCY → ACK 오버레이
        if (intent.getBooleanExtra("emergency", false)) {
            showAckOverlay("EMERGENCY")
        }
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

        // 프리셋이 있으면 학습 타이머 생략
        val hasPreset = prefs.getFloat("presetRestMean", 0f) > 0
        if (!isBaselineComplete && !hasPreset) {
            startLearningTimer()
        } else if (!isBaselineComplete) {
            // 프리셋 또는 이전 학습 데이터 있음 → 바로 완료 처리
            showBaselineComplete(prefs.getFloat("presetRestMean", 75f).toInt())
            isBaselineComplete = true
            prefs.edit().putBoolean("baselineComplete", true).apply()
        }
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

        // ★ 서비스 완전 정지 — 워치 벗으면 BLE도 정지 (동료 위험은 폰이 수신)
        stopService(Intent(this, SensorService::class.java))
        stopService(Intent(this, AlertService::class.java))
        BleAlertService.stopAll()

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
        when (state) {
            "FALL_DETECTED" -> {
                tvAckTitle.text = "넘어지셨나요?"
                tvAckMessage.text = "낙상 감지"
                tvAckTitle.setTextColor(0xFFE53935.toInt())
                tvAckTimer.text = "무응답 시 자동 경보"
                btnAckOk.text = "✅ 괜찮아요"
            }
            "EMERGENCY" -> {
                tvAckTitle.text = "긴급 상황"
                tvAckMessage.text = "주변에 경보 전파 중"
                tvAckTitle.setTextColor(0xFFE53935.toInt())
                tvAckTimer.text = "괜찮으시면 해제하세요"
                btnAckOk.text = "✅ 괜찮아요 (경보 해제)"
            }
            else -> {
                tvAckTitle.text = "괜찮으세요?"
                tvAckMessage.text = "이상 징후 감지"
                tvAckTitle.setTextColor(0xFFFF9800.toInt())
                tvAckTimer.text = "무응답 5분 시 자동 경보"
                btnAckOk.text = "✅ 괜찮아요"
            }
        }
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
            // 권한 있으면 항상 BLE 시작
            startBle()
            // WORKING/RESTING이면 서비스도 시작
            if (workMode == WorkMode.WORKING || workMode == WorkMode.RESTING) startServices()
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
        // SharedPreferences에서 workerId 로드 (SensorService 미시작 시에도 정확한 ID 사용)
        val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
        val workerId = prefs.getString("workerId", "W-001") ?: "W-001"
        SensorService.WORKER_ID = workerId
        BleAlertService.startNormalAdvertise(this, workerId)
        BleAlertService.startScanning(this)
        android.util.Log.d("MainActivity", "BLE started: advertise=$workerId, scanning=true")
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
        registerReceiver(autoEndWorkReceiver, IntentFilter("com.dainon.safepulse.AUTO_END_WORK"), RECEIVER_NOT_EXPORTED)
        registerReceiver(watchBackOnReceiver, IntentFilter("com.dainon.safepulse.WATCH_BACK_ON"), RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(sensorReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(alertReceiver) } catch (_: Exception) {}
        // p2pReceiver는 해제하지 않음 (화면 꺼져도 수신)
        try { unregisterReceiver(autoEndWorkReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(watchBackOnReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
