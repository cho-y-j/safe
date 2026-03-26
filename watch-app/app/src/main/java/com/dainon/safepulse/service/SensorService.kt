package com.dainon.safepulse.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dainon.safepulse.data.SensorPayload
import com.dainon.safepulse.data.ServerClient
import com.dainon.safepulse.ui.MainActivity
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SafePulse 핵심 센서 서비스
 *
 * 6단계 흐름:
 * 1. 개인 베이스라인 학습 (3~6시간, 이후 지속 업데이트)
 * 2. 내 베이스라인 대비 실시간 이상 감지
 * 3. 본인에게 먼저 알림 (휴식 권고 + 확인 버튼)
 * 4. 잠 vs 응급 AI 구분
 * 5. 주변 동료 P2P BLE 경보 + 서버 전송
 * 6. 무응답 시 119 자동 연동
 */
class SensorService : Service(), SensorEventListener {

    companion object {
        const val TAG = "SensorService"
        const val CHANNEL_ID = "safepulse_sensor"
        const val CHANNEL_ALERT = "safepulse_alert"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2

        var WORKER_ID = "W-001"

        // 학습 상태 (Activity에서 접근)
        var lastBaselineHR = 0

        // 확인 버튼 액션
        const val ACTION_ACKNOWLEDGE = "com.dainon.safepulse.ACKNOWLEDGE"
    }

    private lateinit var sensorManager: SensorManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ──── 센서 현재값 ────
    private var heartRate = 0
    private var spo2 = 0
    private var bodyTemp = 36.5
    private var accelX = 0f; private var accelY = 0f; private var accelZ = 0f
    private var activityLevel = 0f  // 현재 활동량 (0=안정, 1+=활동)

    // ──── 활동 상태별 베이스라인 (단계 1) ────
    // 안정 시 (앉아있을 때)
    private val restHrHistory = mutableListOf<Int>()
    private var restHrMean = 72.0
    private var restHrStd = 8.0
    // 활동 시 (걸어다닐 때)
    private val activeHrHistory = mutableListOf<Int>()
    private var activeHrMean = 90.0
    private var activeHrStd = 12.0
    // 공통
    private var baselineTempMean = 36.5
    private var baselineTempStd = 0.3
    private var baselineReady = false
    private var learningMinutes = 0
    private var totalSamples = 0
    private val BASELINE_MIN_SAMPLES = 60  // 최소 60개 (5초 간격 = 5분)

    // 활동 판정 기준
    private val ACTIVITY_THRESHOLD = 1.5f
    private var recentActivitySum = 0f
    private var recentActivityCount = 0

    // ──── 낙상 감지 ────
    private var lastAccelMagnitude = 9.81f
    private var freeFallDetected = false       // 자유낙하 감지
    private var freeFallTime = 0L              // 자유낙하 시작 시각
    private var impactDetected = false         // 충격 감지
    private val FREE_FALL_THRESHOLD = 3.0f     // 이 이하면 자유낙하 (정상 9.81)
    private val IMPACT_THRESHOLD = 25.0f       // 이 이상이면 충격
    private val FALL_WINDOW_MS = 2000L         // 낙하→충격 2초 이내

    // ──── 워치 탈착 감지 ────
    private var lastValidHeartRate = 0         // 마지막 유효 심박
    private var heartRateZeroCount = 0         // 연속 0 카운트
    private var wasAnomalyBeforeZero = false   // 0 되기 전 이상 상태였나

    // ──── 상태 관리 (단계 2~4) ────
    enum class WorkerState {
        NORMAL,           // 정상
        MILD_ANOMALY,     // 경미한 이상 → 본인 알림
        WAITING_ACK,      // 확인 버튼 대기 중 (5초)
        ACKNOWLEDGED,     // 확인 눌렀지만 추적 감시 중
        SLEEP_SUSPECTED,  // 수면 의심
        WATCH_REMOVED,    // 워치 벗음 (정상 상태에서 심박 0)
        FALL_DETECTED,    // 낙상 감지 → 5초 확인 대기
        EMERGENCY         // 응급 → P2P + 서버
    }

    private var currentState = WorkerState.NORMAL
    private var anomalyStartTime = 0L
    private var ackWaitStartTime = 0L
    private var lastMovementTime = 0L
    private var noMovementSeconds = 0

    private val ACK_TIMEOUT_SEC = 5        // P2P 전 확인 대기: 5초!
    private val SLEEP_VS_EMERGENCY_SEC = 30
    private val EMERGENCY_ESCALATION_SEC = 90

    // ──── GPS 위치 ────
    private var latitude = 37.4602
    private var longitude = 126.4407

    // ──── 모니터링 주기 (특허: 이중 모드 전력 관리) ────
    private var monitorIntervalMs = 30000L   // 저전력 모드: 30초
    private val NORMAL_INTERVAL = 30000L     // 평상시 30초 (배터리 24~48시간)
    private val WATCH_INTERVAL = 10000L      // 추적 감시: 10초
    private val ALERT_INTERVAL = 3000L       // 이상 감지 시: 3초
    private val EMERGENCY_INTERVAL = 1000L   // 응급: 1초

    // 서버 전송 주기 (센서 읽기보다 덜 자주)
    private var serverSendCounter = 0
    private val SERVER_SEND_EVERY_N = 2      // N번 센서 읽기마다 1번 전송

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildStatusNotification("센서 초기화 중..."))

        // 이전 베이스라인 복원
        val prefs = applicationContext.getSharedPreferences("safepulse", MODE_PRIVATE)
        WORKER_ID = prefs.getString("workerId", "W-001") ?: "W-001"
        val savedHR = prefs.getInt("baselineHR", 0)
        if (prefs.getBoolean("baselineComplete", false) && savedHR > 0) {
            restHrMean = savedHR.toDouble()
            baselineReady = true
            lastBaselineHR = savedHR
            Log.d(TAG, "📂 Baseline restored: restHR=$savedHR, worker=$WORKER_ID")
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        registerSensors()
        startGPS()
        lastMovementTime = System.currentTimeMillis()
        startMonitoringLoop()
    }

    // ════════════════════════════════════════
    // 센서 등록 + 읽기
    // ════════════════════════════════════════

    private fun registerSensors() {
        // 심박수
        // 심박수 — 저전력 모드 (3초 간격 수신, OS가 자체 관리)
        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (hrSensor != null) {
            sensorManager.registerListener(this, hrSensor, 3000000) // 3초 마이크로초
            Log.d(TAG, "✅ Heart rate sensor registered (low-power): ${hrSensor.name}")
        } else {
            Log.e(TAG, "❌ Heart rate sensor NOT FOUND")
        }

        // 가속도계 — 움직임만 감지하면 되므로 저전력
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelSensor != null) {
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI) // ~60ms
            Log.d(TAG, "✅ Accelerometer registered (low-power)")
        } else {
            Log.e(TAG, "❌ Accelerometer NOT FOUND")
        }

        // SpO₂ (Samsung 전용)
        val spo2Sensor = sensorManager.getSensorList(Sensor.TYPE_ALL).find {
            it.stringType.contains("spo2", true) || it.stringType.contains("oxygen", true)
        }
        if (spo2Sensor != null) {
            sensorManager.registerListener(this, spo2Sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "✅ SpO₂ sensor registered: ${spo2Sensor.stringType}")
        } else {
            Log.w(TAG, "⚠ SpO₂ sensor not found (Samsung Health SDK 필요)")
        }

        // 사용 가능한 전체 센서 목록 로그
        Log.d(TAG, "Available sensors: ${sensorManager.getSensorList(Sensor.TYPE_ALL).map { "${it.name}(${it.stringType})" }.joinToString(", ")}")
    }

    private fun startGPS() {
        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 60초마다 GPS 업데이트 (저전력)
                locationManager.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER,
                    60000L, 10f, // 60초 또는 10m 이동
                    object : android.location.LocationListener {
                        override fun onLocationChanged(loc: android.location.Location) {
                            latitude = loc.latitude
                            longitude = loc.longitude
                            Log.d(TAG, "📍 GPS: $latitude, $longitude")
                        }
                    }
                )
                // 마지막 알려진 위치
                locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)?.let {
                    latitude = it.latitude
                    longitude = it.longitude
                }
                Log.d(TAG, "✅ GPS started")
            } else {
                Log.w(TAG, "⚠ GPS permission not granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "GPS error: ${e.message}")
        }
    }

    private var hrLogCount = 0

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values[0].toInt()
                if (hr > 0) {
                    // 유효한 심박 → 정상 동작
                    lastValidHeartRate = hr
                    heartRateZeroCount = 0
                    heartRate = hr
                    hrLogCount++
                    if (hrLogCount % 10 == 1) {
                        Log.d(TAG, "💓 HR=$hr, samples=$totalSamples, baselineReady=$baselineReady")
                    }
                } else {
                    // 심박 0 → 워치 벗었거나 센서 접촉 불량
                    heartRateZeroCount++
                    if (heartRateZeroCount >= 3) { // 연속 3번 0이면
                        handleHeartRateZero()
                    }
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]

                val magnitude = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
                val deviation = abs(magnitude - 9.81f)

                // 활동량 누적
                recentActivitySum += deviation
                recentActivityCount++

                if (deviation > ACTIVITY_THRESHOLD) {
                    lastMovementTime = System.currentTimeMillis()
                    noMovementSeconds = 0
                }

                // ──── 낙상 감지 (자유낙하 → 충격 패턴) ────
                val now = System.currentTimeMillis()

                // 1단계: 자유낙하 감지 (가속도 ≈ 0)
                if (magnitude < FREE_FALL_THRESHOLD && !freeFallDetected) {
                    freeFallDetected = true
                    freeFallTime = now
                    Log.w(TAG, "⚡ Free fall detected! magnitude=${"%.1f".format(magnitude)}")
                }

                // 2단계: 충격 감지 (자유낙하 후 2초 이내 큰 충격)
                if (freeFallDetected && !impactDetected) {
                    if (now - freeFallTime > FALL_WINDOW_MS) {
                        // 2초 지나면 리셋 (낙상 아님)
                        freeFallDetected = false
                    } else if (magnitude > IMPACT_THRESHOLD) {
                        impactDetected = true
                        Log.w(TAG, "💥 FALL IMPACT! magnitude=${"%.1f".format(magnitude)}, time=${now - freeFallTime}ms")

                        // 낙상 감지 → 5초 확인 대기
                        if (currentState != WorkerState.EMERGENCY) {
                            currentState = WorkerState.FALL_DETECTED
                            ackWaitStartTime = now
                            monitorIntervalMs = EMERGENCY_INTERVAL
                            notifyWorker("⚠ 넘어지셨나요? 괜찮으시면 5초 내 확인을 눌러주세요!")
                        }

                        // 리셋
                        freeFallDetected = false
                        impactDetected = false
                    }
                }

                lastAccelMagnitude = magnitude
            }
        }
        // SpO₂
        if (event.sensor.stringType.contains("spo2", true) || event.sensor.stringType.contains("oxygen", true)) {
            val v = event.values[0].toInt()
            if (v in 70..100) spo2 = v
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** 심박 0 처리: 항상 먼저 물어보기 (양치기 효과 방지) */
    private fun handleHeartRateZero() {
        val wasAnomaly = currentState == WorkerState.MILD_ANOMALY ||
            currentState == WorkerState.WAITING_ACK ||
            currentState == WorkerState.FALL_DETECTED

        wasAnomalyBeforeZero = wasAnomaly

        if (wasAnomaly) {
            // 이상 상태에서 심박 0 → 위험 가능성 높지만, 먼저 물어봄
            Log.w(TAG, "⚠ HR=0 during anomaly ($currentState) → asking first")
            currentState = WorkerState.WAITING_ACK
            ackWaitStartTime = System.currentTimeMillis()
            monitorIntervalMs = EMERGENCY_INTERVAL
            notifyWorker("심박 감지가 안 됩니다. 괜찮으시면 확인을 눌러주세요!")
        } else {
            // 정상에서 심박 0 → 벗었을 가능성 높지만, 그래도 물어봄
            Log.d(TAG, "⌚ HR=0 from normal → likely removed, asking")
            currentState = WorkerState.WATCH_REMOVED
            monitorIntervalMs = NORMAL_INTERVAL
            heartRate = 0
            // 워치 벗은 건 조용히 처리 (진동 안 함)
        }
    }

    // ════════════════════════════════════════
    // 메인 모니터링 루프
    // ════════════════════════════════════════

    private fun startMonitoringLoop() {
        scope.launch {
            while (isActive) {
                delay(monitorIntervalMs)

                // 무움직임 시간 갱신
                noMovementSeconds = ((System.currentTimeMillis() - lastMovementTime) / 1000).toInt()

                // 단계 1: 베이스라인 학습/업데이트
                updateBaseline()

                // 단계 2~4: 상태 판단
                evaluateState()

                // 서버 전송 — 매번이 아닌 N번에 1번 (배터리 절약)
                serverSendCounter++
                if (serverSendCounter >= SERVER_SEND_EVERY_N) {
                    serverSendCounter = 0
                    sendToServer()
                }

                // UI 업데이트
                broadcastStatus()
            }
        }
    }

    // ════════════════════════════════════════
    // 단계 1: 개인 베이스라인 학습
    // ════════════════════════════════════════

    private fun updateBaseline() {
        if (heartRate <= 0) return

        totalSamples++

        // 현재 활동량 계산 (최근 가속도 평균)
        activityLevel = if (recentActivityCount > 0) recentActivitySum / recentActivityCount else 0f
        recentActivitySum = 0f
        recentActivityCount = 0

        val isActive = activityLevel > ACTIVITY_THRESHOLD
        val alpha = 0.05  // EMA 가중치 (천천히 업데이트)

        // 활동 상태에 따라 다른 히스토리에 저장
        if (isActive) {
            activeHrHistory.add(heartRate)
            if (activeHrHistory.size > 2000) activeHrHistory.removeAt(0)
        } else {
            restHrHistory.add(heartRate)
            if (restHrHistory.size > 2000) restHrHistory.removeAt(0)
        }

        // 충분한 샘플이 모이면 베이스라인 갱신
        val totalHistorySize = restHrHistory.size + activeHrHistory.size
        if (totalHistorySize >= BASELINE_MIN_SAMPLES) {

            // 안정 시 베이스라인
            if (restHrHistory.size >= 20) {
                val mean = restHrHistory.average()
                val std = restHrHistory.map { (it - mean) * (it - mean) }.average().let { sqrt(it) }.coerceAtLeast(5.0)
                restHrMean = restHrMean * (1 - alpha) + mean * alpha
                restHrStd = restHrStd * (1 - alpha) + std * alpha
            }

            // 활동 시 베이스라인
            if (activeHrHistory.size >= 20) {
                val mean = activeHrHistory.average()
                val std = activeHrHistory.map { (it - mean) * (it - mean) }.average().let { sqrt(it) }.coerceAtLeast(8.0)
                activeHrMean = activeHrMean * (1 - alpha) + mean * alpha
                activeHrStd = activeHrStd * (1 - alpha) + std * alpha
            }

            if (!baselineReady) {
                baselineReady = true
                Log.d(TAG, "✅ Baseline ready: rest=${restHrMean.toInt()}±${restHrStd.toInt()}, active=${activeHrMean.toInt()}±${activeHrStd.toInt()}")
            }
            lastBaselineHR = restHrMean.toInt()

            // SharedPreferences에 저장 (앱 재시작 시 복원용)
            try {
                applicationContext.getSharedPreferences("safepulse", MODE_PRIVATE).edit()
                    .putInt("baselineHR", restHrMean.toInt())
                    .putBoolean("baselineComplete", true)
                    .apply()
            } catch (_: Exception) {}
        }

        // 로그 (20샘플마다)
        if (totalSamples % 20 == 0) {
            Log.d(TAG, "📊 Learning: total=$totalSamples, rest=${restHrHistory.size}, active=${activeHrHistory.size}, " +
                "restMean=${restHrMean.toInt()}, activeMean=${activeHrMean.toInt()}, " +
                "curActivity=${String.format("%.1f", activityLevel)}, isActive=$isActive, HR=$heartRate")
        }
    }

    // ════════════════════════════════════════
    // 단계 2~4: 상태 평가
    // ════════════════════════════════════════

    private fun evaluateState() {
        if (!baselineReady || heartRate <= 0) return

        // 현재 활동 상태에 맞는 기준 선택
        val isActive = activityLevel > ACTIVITY_THRESHOLD
        val expectedMean = if (isActive) activeHrMean else restHrMean
        val expectedStd = if (isActive) activeHrStd else restHrStd
        val hrDeviation = (heartRate - expectedMean) / expectedStd

        val now = System.currentTimeMillis()

        when (currentState) {
            WorkerState.NORMAL -> {
                // 활동 상태별 기준 대비 2.5σ 이상 벗어나면 이상 감지
                // (활동 중이면 활동 기준, 안정 시면 안정 기준)
                val isHrAnomaly = hrDeviation > 2.5 || hrDeviation < -2.5
                val isSpo2Anomaly = spo2 in 1..93
                val isAbsoluteHigh = heartRate > 130  // 절대 상한 (어떤 상태든)

                if (isAbsoluteHigh || isSpo2Anomaly || (isHrAnomaly && !isActive)) {
                    // 안정 시 이상 또는 절대 상한 초과 또는 SpO₂ 저하
                    currentState = WorkerState.MILD_ANOMALY
                    anomalyStartTime = now
                    monitorIntervalMs = ALERT_INTERVAL
                    Log.d(TAG, "⚠ Anomaly: HR=$heartRate (expected=${expectedMean.toInt()}±${expectedStd.toInt()}), " +
                        "deviation=${"%.1f".format(hrDeviation)}σ, active=$isActive, SpO2=$spo2")
                    notifyWorker("컨디션 이상 감지. 휴식을 권고합니다.")
                } else if (isHrAnomaly && isActive) {
                    // 활동 중 심박 높음 — 바로 알림 아닌 경고만 (과로 주의)
                    Log.d(TAG, "💡 Activity HR high but within active range: HR=$heartRate, deviation=${"%.1f".format(hrDeviation)}σ")
                }
            }

            WorkerState.MILD_ANOMALY -> {
                // 본인에게 알렸으니 확인 대기
                currentState = WorkerState.WAITING_ACK
                ackWaitStartTime = now
            }

            WorkerState.WAITING_ACK -> {
                val waitSec = (now - ackWaitStartTime) / 1000

                if (waitSec >= ACK_TIMEOUT_SEC) {
                    // 5초 내 확인 안 누름 → P2P 경보 + 서버 전송!
                    Log.w(TAG, "⏰ No ACK in ${ACK_TIMEOUT_SEC}s → escalating!")
                    currentState = if (isSleeping()) {
                        WorkerState.SLEEP_SUSPECTED
                    } else {
                        WorkerState.EMERGENCY
                    }
                }
            }

            WorkerState.FALL_DETECTED -> {
                // 낙상 후 5초 확인 대기
                val waitSec = (now - ackWaitStartTime) / 1000
                if (waitSec >= ACK_TIMEOUT_SEC) {
                    // 5초 내 확인 안 누름 → 즉시 응급!
                    Log.w(TAG, "💥 Fall + no ACK → EMERGENCY!")
                    currentState = WorkerState.EMERGENCY
                }
            }

            WorkerState.WATCH_REMOVED -> {
                // 워치 벗은 상태 — 다시 착용하면 복귀
                if (heartRate > 0) {
                    Log.d(TAG, "⌚ Watch back on! HR=$heartRate → NORMAL")
                    currentState = WorkerState.NORMAL
                    monitorIntervalMs = NORMAL_INTERVAL
                    heartRateZeroCount = 0
                }
            }

            WorkerState.ACKNOWLEDGED -> {
                // 확인 눌렀지만 계속 추적 감시
                monitorIntervalMs = WATCH_INTERVAL  // 10초

                // 절대 상한 초과 또는 SpO₂ 심각 → 재알림
                if (heartRate > 140 || (spo2 in 1..90)) {
                    currentState = WorkerState.MILD_ANOMALY
                    anomalyStartTime = now
                    notifyWorker("이상 징후가 지속됩니다. 즉시 휴식하세요.")
                }

                // 정상 범위로 돌아오면 복귀
                if (abs(hrDeviation) < 1.5 && (spo2 == 0 || spo2 > 95)) {
                    currentState = WorkerState.NORMAL
                    monitorIntervalMs = NORMAL_INTERVAL
                    Log.d(TAG, "✅ Back to normal: HR=$heartRate")
                }
            }

            WorkerState.SLEEP_SUSPECTED -> {
                // 부드러운 반복 알림
                notifyWorker("움직임이 없습니다. 괜찮으신가요?")

                // 바이탈이 악화되면 응급 전환
                if (hrDeviation > 3.0 || hrDeviation < -3.0 || (spo2 in 1..90)) {
                    currentState = WorkerState.EMERGENCY
                }

                // 움직임 감지되면 정상 복귀
                if (noMovementSeconds < 5) {
                    currentState = WorkerState.NORMAL
                    monitorIntervalMs = NORMAL_INTERVAL
                }
            }

            WorkerState.EMERGENCY -> {
                // 🚨 단계 5: P2P BLE 경보 + 서버 알림 (고출력 모드)
                monitorIntervalMs = EMERGENCY_INTERVAL  // 1초 간격
                Log.w(TAG, "🚨 EMERGENCY: HR=$heartRate, SpO2=$spo2, noMovement=${noMovementSeconds}s")

                // P2P BLE 경보 발동
                BleAlertService.broadcastEmergency(this, WORKER_ID)

                // 서버에 긴급 알림
                scope.launch {
                    ServerClient.sendEmergencyAlert(WORKER_ID, heartRate, spo2, bodyTemp)
                }

                // 강한 진동 + 소리
                emergencyVibrate()
                notifyWorker("🚨 긴급 상황 감지! 주변 동료에게 알림을 전송했습니다.")

                // 90초 후 119 연동 (단계 6)
                val emergencySec = (now - anomalyStartTime) / 1000
                if (emergencySec >= EMERGENCY_ESCALATION_SEC) {
                    Log.w(TAG, "119 auto-dial triggered")
                    // TODO: 119 자동 전화
                }

                // 움직임 + 확인 → 해제
                if (noMovementSeconds < 5) {
                    // 움직였지만 바이탈 확인 필요
                }
            }
        }
    }

    /** 잠 vs 응급 구분 (단계 4 핵심) */
    private fun isSleeping(): Boolean {
        if (noMovementSeconds < SLEEP_VS_EMERGENCY_SEC) return false

        // 안정 시 기준으로 정상 범위 체크 (움직임 없으니까)
        val hrNormal = heartRate > 0 && abs(heartRate - restHrMean) < restHrStd * 2.0
        val tempNormal = abs(bodyTemp - baselineTempMean) < baselineTempStd * 2
        val spo2Normal = spo2 == 0 || spo2 > 94

        val sleeping = hrNormal && tempNormal && spo2Normal
        Log.d(TAG, "😴 Sleep check: noMove=${noMovementSeconds}s, hrNormal=$hrNormal(HR=$heartRate, restMean=${restHrMean.toInt()}), sleeping=$sleeping")
        return sleeping
    }

    // ════════════════════════════════════════
    // 확인 버튼 처리
    // ════════════════════════════════════════

    fun onAcknowledge() {
        Log.d(TAG, "Worker acknowledged alert")
        currentState = WorkerState.ACKNOWLEDGED
        monitorIntervalMs = 2000L  // 추적 감시 모드 (2초)

        // P2P 경보 중이었다면 해제
        BleAlertService.cancelEmergency(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ACKNOWLEDGE) {
            onAcknowledge()
        }
        return START_STICKY
    }

    // ════════════════════════════════════════
    // 알림 + 진동
    // ════════════════════════════════════════

    private fun notifyWorker(message: String) {
        // 진동
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

        // 확인 버튼이 있는 알림
        val ackIntent = Intent(this, SensorService::class.java).apply {
            action = ACTION_ACKNOWLEDGE
        }
        val ackPending = PendingIntent.getService(this, 0, ackIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setContentTitle("SafePulse")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(android.R.drawable.ic_menu_send, "✅ 확인 (괜찮아요)", ackPending)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(ALERT_NOTIFICATION_ID, notification)

        // 브로드캐스트
        sendBroadcast(Intent("com.dainon.safepulse.ALERT").apply {
            putExtra("message", message)
            putExtra("state", currentState.name)
        })
    }

    private fun emergencyVibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        // 강한 연속 진동 패턴
        vibrator.vibrate(VibrationEffect.createWaveform(
            longArrayOf(0, 500, 200, 500, 200, 1000), -1
        ))
    }

    // ════════════════════════════════════════
    // 서버 전송 + UI 브로드캐스트
    // ════════════════════════════════════════

    private suspend fun sendToServer() {
        val payload = SensorPayload(
            workerId = WORKER_ID,
            heartRate = heartRate,
            spo2 = if (spo2 > 0) spo2 else 98,
            bodyTemp = bodyTemp,
            stress = calculateStress(),
            latitude = latitude, longitude = longitude,
        )
        ServerClient.sendSensorData(payload)
    }

    private fun broadcastStatus() {
        val stateKr = when (currentState) {
            WorkerState.NORMAL -> "정상"
            WorkerState.MILD_ANOMALY -> "이상 감지"
            WorkerState.WAITING_ACK -> "확인 대기"
            WorkerState.ACKNOWLEDGED -> "추적 감시"
            WorkerState.SLEEP_SUSPECTED -> "수면 의심"
            WorkerState.WATCH_REMOVED -> "미착용"
            WorkerState.FALL_DETECTED -> "낙상 감지"
            WorkerState.EMERGENCY -> "응급"
        }

        sendBroadcast(Intent("com.dainon.safepulse.SENSOR_UPDATE").apply {
            putExtra("heartRate", heartRate)
            putExtra("spo2", if (spo2 > 0) spo2 else 98)
            putExtra("bodyTemp", bodyTemp)
            putExtra("state", currentState.name)
            putExtra("stateKr", stateKr)
            putExtra("baselineReady", baselineReady)
            putExtra("baselineHr", restHrMean.toInt())
            putExtra("activeHr", activeHrMean.toInt())
            putExtra("activityLevel", activityLevel)
            putExtra("totalSamples", totalSamples)
            putExtra("noMovementSec", noMovementSeconds)
            putExtra("connected", true)
        })

        // 상태바 업데이트
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val isActive = activityLevel > ACTIVITY_THRESHOLD
        val statusText = if (baselineReady)
            "$stateKr | 💓$heartRate (${if (isActive) "활동${activeHrMean.toInt()}" else "안정${restHrMean.toInt()}"}) | $WORKER_ID"
        else
            "학습 중 (${totalSamples}샘플) | $WORKER_ID"
        nm.notify(NOTIFICATION_ID, buildStatusNotification(statusText))
    }

    private fun calculateStress(): Int {
        if (!baselineReady) return 20
        val isActive = activityLevel > ACTIVITY_THRESHOLD
        val expectedMean = if (isActive) activeHrMean else restHrMean
        val expectedStd = if (isActive) activeHrStd else restHrStd
        val deviation = abs(heartRate - expectedMean) / expectedStd
        return (deviation * 20).toInt().coerceIn(0, 100)
    }

    // ════════════════════════════════════════
    // 알림 채널
    // ════════════════════════════════════════

    private fun createNotificationChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "센서 모니터링", NotificationManager.IMPORTANCE_LOW
        ))
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ALERT, "긴급 알림", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
    }

    private fun buildStatusNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafePulse")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        scope.cancel()
        super.onDestroy()
    }

    private fun sqrt(d: Double): Double = kotlin.math.sqrt(d)
}
