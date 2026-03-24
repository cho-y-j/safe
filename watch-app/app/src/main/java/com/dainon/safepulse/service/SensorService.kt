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

    // ──── 개인 베이스라인 (단계 1) ────
    private val hrHistory = mutableListOf<Int>()          // 심박 이력
    private val tempHistory = mutableListOf<Double>()      // 체온 이력
    private val activityHistory = mutableListOf<Float>()   // 활동량 이력
    private var baselineHrMean = 0.0     // 내 평균 심박
    private var baselineHrStd = 0.0      // 내 심박 표준편차
    private var baselineTempMean = 36.5
    private var baselineTempStd = 0.2
    private var baselineReady = false    // 베이스라인 학습 완료 여부
    private var learningMinutes = 0      // 학습 경과 시간(분)
    private val BASELINE_MIN_MINUTES = 30 // 최소 30분 후 베이스라인 활성화 (시연용, 실제는 180분)

    // ──── 상태 관리 (단계 2~4) ────
    enum class WorkerState {
        NORMAL,           // 정상
        MILD_ANOMALY,     // 경미한 이상 → 본인 알림
        WAITING_ACK,      // 확인 버튼 대기 중
        ACKNOWLEDGED,     // 확인 눌렀지만 추적 감시 중
        SLEEP_SUSPECTED,  // 수면 의심
        EMERGENCY         // 응급 → P2P + 서버
    }

    private var currentState = WorkerState.NORMAL
    private var anomalyStartTime = 0L      // 이상 시작 시각
    private var ackWaitStartTime = 0L      // 확인 대기 시작 시각
    private var lastMovementTime = 0L      // 마지막 움직임 시각
    private var noMovementSeconds = 0      // 연속 무움직임 초

    private val ACK_TIMEOUT_SEC = 15       // 확인 버튼 대기 시간
    private val SLEEP_VS_EMERGENCY_SEC = 30 // 잠/응급 판단 시간
    private val EMERGENCY_ESCALATION_SEC = 90 // 119 연동 시간

    // ──── 모니터링 주기 ────
    private var monitorIntervalMs = 5000L  // 기본 5초
    private val NORMAL_INTERVAL = 5000L
    private val ALERT_INTERVAL = 1000L     // 이상 시 1초

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildStatusNotification("센서 초기화 중..."))

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        registerSensors()
        lastMovementTime = System.currentTimeMillis()
        startMonitoringLoop()
    }

    // ════════════════════════════════════════
    // 센서 등록 + 읽기
    // ════════════════════════════════════════

    private fun registerSensors() {
        // 심박수
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        // 가속도계 (움직임 감지용)
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        // SpO₂ (Samsung 전용)
        sensorManager.getSensorList(Sensor.TYPE_ALL).find {
            it.stringType.contains("spo2", true) || it.stringType.contains("oxygen", true)
        }?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values[0].toInt()
                if (hr > 0) heartRate = hr
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]

                // 움직임 감지: 가속도 변화량
                val magnitude = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
                if (abs(magnitude - 9.81f) > 1.5f) {
                    lastMovementTime = System.currentTimeMillis()
                    noMovementSeconds = 0
                }
            }
        }
        // SpO₂
        if (event.sensor.stringType.contains("spo2", true) || event.sensor.stringType.contains("oxygen", true)) {
            val v = event.values[0].toInt()
            if (v in 70..100) spo2 = v
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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

                // 서버 전송 (경로 2)
                sendToServer()

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

        hrHistory.add(heartRate)
        tempHistory.add(bodyTemp)

        val actMagnitude = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
        activityHistory.add(actMagnitude)

        // 최근 데이터만 유지 (6시간 = 4320개 @5초 간격)
        if (hrHistory.size > 4320) hrHistory.removeAt(0)
        if (tempHistory.size > 4320) tempHistory.removeAt(0)
        if (activityHistory.size > 4320) activityHistory.removeAt(0)

        learningMinutes = hrHistory.size * (monitorIntervalMs / 60000).toInt().coerceAtLeast(1)

        if (hrHistory.size >= (BASELINE_MIN_MINUTES * 60 / (monitorIntervalMs / 1000)).toInt()) {
            // 평균 + 표준편차 계산 (EMA 방식으로 점진 업데이트)
            val alpha = 0.1 // EMA 가중치

            val newMean = hrHistory.average()
            val newStd = hrHistory.map { (it - newMean) * (it - newMean) }
                .average().let { sqrt(it) }.coerceAtLeast(3.0)

            if (baselineReady) {
                // 점진적 업데이트 (매일 자동)
                baselineHrMean = baselineHrMean * (1 - alpha) + newMean * alpha
                baselineHrStd = baselineHrStd * (1 - alpha) + newStd * alpha
                lastBaselineHR = baselineHrMean.toInt()
            } else {
                baselineHrMean = newMean
                baselineHrStd = newStd
                baselineReady = true
                lastBaselineHR = baselineHrMean.toInt()
                Log.d(TAG, "Baseline ready: HR mean=${baselineHrMean.toInt()}, std=${baselineHrStd.toInt()}")
            }

            val tMean = tempHistory.average()
            val tStd = tempHistory.map { (it - tMean) * (it - tMean) }
                .average().let { sqrt(it) }.coerceAtLeast(0.1)
            baselineTempMean = baselineTempMean * (1 - alpha) + tMean * alpha
            baselineTempStd = baselineTempStd * (1 - alpha) + tStd * alpha
        }
    }

    // ════════════════════════════════════════
    // 단계 2~4: 상태 평가
    // ════════════════════════════════════════

    private fun evaluateState() {
        if (!baselineReady || heartRate <= 0) return

        val hrDeviation = (heartRate - baselineHrMean) / baselineHrStd  // 표준편차 몇 배?
        val now = System.currentTimeMillis()

        when (currentState) {
            WorkerState.NORMAL -> {
                // 베이스라인 대비 2σ 이상 벗어나면 이상 감지
                if (hrDeviation > 2.0 || hrDeviation < -2.0 || (spo2 in 1..93)) {
                    currentState = WorkerState.MILD_ANOMALY
                    anomalyStartTime = now
                    monitorIntervalMs = ALERT_INTERVAL  // 모니터링 주기 강화
                    Log.d(TAG, "Anomaly detected: HR deviation=${hrDeviation}, SpO2=$spo2")
                    notifyWorker("컨디션 이상 감지. 휴식을 권고합니다.")
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
                    // 확인 안 누름 → 잠 vs 응급 판단
                    currentState = if (isSleeping()) {
                        WorkerState.SLEEP_SUSPECTED
                    } else {
                        WorkerState.EMERGENCY
                    }
                }
            }

            WorkerState.ACKNOWLEDGED -> {
                // 확인 눌렀지만 계속 추적 감시
                monitorIntervalMs = 2000L  // 2초 간격 감시

                // 여전히 이상이면 다시 알림
                if (hrDeviation > 3.0 || (spo2 in 1..90)) {
                    currentState = WorkerState.MILD_ANOMALY
                    anomalyStartTime = now
                    notifyWorker("이상 징후가 지속됩니다. 즉시 휴식하세요.")
                }

                // 정상 복귀하면
                if (hrDeviation < 1.5 && (spo2 == 0 || spo2 > 95)) {
                    currentState = WorkerState.NORMAL
                    monitorIntervalMs = NORMAL_INTERVAL
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
                // 🚨 단계 5: P2P BLE 경보 + 서버 알림
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
        if (noMovementSeconds < SLEEP_VS_EMERGENCY_SEC) return false // 아직 판단 이름

        val hrNormal = heartRate > 0 &&
            abs(heartRate - baselineHrMean) < baselineHrStd * 1.5  // 심박 정상 범위
        val tempNormal = abs(bodyTemp - baselineTempMean) < baselineTempStd * 2
        val spo2Normal = spo2 == 0 || spo2 > 94

        // 바이탈 다 정상 + 무움직임 = 잠
        // 바이탈 이상 + 무움직임 = 응급
        return hrNormal && tempNormal && spo2Normal
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
            latitude = 37.4602, longitude = 126.4407,
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
            WorkerState.EMERGENCY -> "응급"
        }

        sendBroadcast(Intent("com.dainon.safepulse.SENSOR_UPDATE").apply {
            putExtra("heartRate", heartRate)
            putExtra("spo2", if (spo2 > 0) spo2 else 98)
            putExtra("bodyTemp", bodyTemp)
            putExtra("state", currentState.name)
            putExtra("stateKr", stateKr)
            putExtra("baselineReady", baselineReady)
            putExtra("baselineHr", baselineHrMean.toInt())
            putExtra("noMovementSec", noMovementSeconds)
            putExtra("connected", true)
        })

        // 상태바 업데이트
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val statusText = if (baselineReady)
            "$stateKr | 💓$heartRate (기준:${baselineHrMean.toInt()}) | $WORKER_ID"
        else
            "베이스라인 학습 중... (${hrHistory.size}샘플) | $WORKER_ID"
        nm.notify(NOTIFICATION_ID, buildStatusNotification(statusText))
    }

    private fun calculateStress(): Int {
        if (!baselineReady) return 20
        val deviation = abs(heartRate - baselineHrMean) / baselineHrStd
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
