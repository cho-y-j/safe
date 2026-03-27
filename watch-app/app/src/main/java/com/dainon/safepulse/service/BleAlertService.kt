package com.dainon.safepulse.service

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * BLE P2P 거리비례 경보 서비스
 *
 * 특허 핵심: A(d) = A_max × (1 - d / D_max)
 *
 * [평상시]
 * - BLE Advertise: "SafePulse|W-001|NORMAL"
 * - BLE Scan: 주변 워치 존재 감지
 *
 * [응급 시]
 * - BLE Advertise: "SafePulse|W-001|EMERGENCY"
 * - 주변 워치가 수신 → RSSI로 거리 추정 → 거리에 비례해 진동 강도 조절
 *
 * [수신 측]
 * - EMERGENCY 광고 수신 → RSSI 측정 → 거리 계산 → 진동 강도 결정
 * - 가까울수록 강하게, 멀수록 약하게
 */
object BleAlertService {

    private const val TAG = "BleAlert"

    // BLE 광고 식별자
    private const val SAFEPULSE_PREFIX = "SP"  // 2바이트 접두어

    // 거리 계산 파라미터
    private const val D_MAX = 30.0           // 최대 경보 반경 (미터)
    private const val TX_POWER = -59          // 1m 거리 기준 RSSI (기기별 캘리브레이션 필요)
    private const val PATH_LOSS_N = 2.5       // 환경 계수 (실내 2.0~3.0)

    // 상태
    private var isEmergency = false
    private var emergencyWorkerId = ""
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var lastEmergencyBroadcastTime = 0L  // 디바운스용

    // 수신 경보 제어
    private val suppressedWorkerIds = mutableSetOf<String>()
    private val suppressedTimestamps = mutableMapOf<String, Long>()
    private var activeVibrator: Vibrator? = null
    private val activeEmergencyWorkers = mutableMapOf<String, Long>()  // workerId → 마지막 진동 시작 시각

    // ════════════════════════════════════════
    // 광고 (Advertise) — 내 상태를 주변에 알림
    // ════════════════════════════════════════

    /** 평상시: 정상 상태 광고 */
    fun startNormalAdvertise(context: Context, workerId: String) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            advertiser = adapter.bluetoothLeAdvertiser ?: return

            if (android.os.Build.VERSION.SDK_INT >= 31 &&
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_ADVERTISE permission not granted, skipping")
                return
            }

            val data = buildAdvertiseData(workerId, false)
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false)
                .setTimeout(0)
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
            Log.d(TAG, "Normal advertise started: $workerId")
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE advertise failed: ${e.message}")
        }
    }

    /** 응급 시: 긴급 경보 광고 (2초 디바운스) */
    fun broadcastEmergency(context: Context, workerId: String) {
        val now = System.currentTimeMillis()
        if (now - lastEmergencyBroadcastTime < 2000) return  // 2초 이내 재호출 무시
        lastEmergencyBroadcastTime = now

        try {
            isEmergency = true
            emergencyWorkerId = workerId

            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            advertiser = adapter.bluetoothLeAdvertiser ?: return

            if (android.os.Build.VERSION.SDK_INT >= 31 &&
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_ADVERTISE permission not granted")
                return
            }

            advertiser?.stopAdvertising(advertiseCallback)

            val data = buildAdvertiseData(workerId, true)
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0)
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
            Log.w(TAG, "EMERGENCY advertise started: $workerId")
        } catch (e: SecurityException) {
            Log.e(TAG, "Emergency advertise failed: ${e.message}")
        }
    }

    /** 경보 해제 (발신자) */
    fun cancelEmergency(context: Context) {
        isEmergency = false
        lastEmergencyBroadcastTime = 0L
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: SecurityException) {}
        // 정상 모드로 복귀
        startNormalAdvertise(context, emergencyWorkerId)
        Log.d(TAG, "Emergency cancelled, back to normal")
    }

    /** 수신 경보 해제 (수신자가 "응답함" 또는 "도움불가" 탭) */
    fun dismissReceivedAlert(workerId: String) {
        suppressedWorkerIds.add(workerId)
        suppressedTimestamps[workerId] = System.currentTimeMillis()
        activeEmergencyWorkers.remove(workerId)
        activeVibrator?.cancel()
        Log.d(TAG, "Dismissed received alert from $workerId")
    }

    /** 수신 경보 억제 해제 (30초 후 자동) */
    private fun cleanupSuppressed() {
        val now = System.currentTimeMillis()
        val expired = suppressedTimestamps.filter { now - it.value > 30_000 }.keys
        expired.forEach {
            suppressedWorkerIds.remove(it)
            suppressedTimestamps.remove(it)
        }
    }

    private fun buildAdvertiseData(workerId: String, emergency: Boolean): AdvertiseData {
        // 광고 데이터: "SP" + workerId(5bytes) + status(1byte)
        // 예: "SPW-001E" (Emergency) 또는 "SPW-001N" (Normal)
        val status = if (emergency) "E" else "N"
        val payload = "$SAFEPULSE_PREFIX$workerId$status".toByteArray(Charsets.UTF_8)

        return AdvertiseData.Builder()
            .addManufacturerData(0xDADA, payload)  // 0xDADA = 다인온 커스텀 ID
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(true)
            .build()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "Advertise started")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertise failed: $errorCode")
        }
    }

    // ════════════════════════════════════════
    // 스캔 (Scan) — 주변 워치 경보 수신
    // ════════════════════════════════════════

    /** 주변 SafePulse 기기 스캔 시작 */
    fun startScanning(context: Context) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            scanner = adapter.bluetoothLeScanner ?: return

            if (android.os.Build.VERSION.SDK_INT >= 31 &&
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_SCAN permission not granted, skipping")
                return
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val filter = ScanFilter.Builder()
                .setManufacturerData(0xDADA, byteArrayOf())
                .build()

            scanner?.startScan(listOf(filter), settings, scanCallback(context))
            isScanning = true
            Log.d(TAG, "BLE scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan failed: ${e.message}")
        }
    }

    fun stopScanning() {
        if (isScanning) {
            scanner?.stopScan(scanCallbackInstance)
            isScanning = false
        }
    }

    private var scanCallbackInstance: ScanCallback? = null

    private fun scanCallback(context: Context): ScanCallback {
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                processScanResult(context, result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { processScanResult(context, it) }
            }
        }
        scanCallbackInstance = cb
        return cb
    }

    private fun processScanResult(context: Context, result: ScanResult) {
        val mfgData = result.scanRecord?.getManufacturerSpecificData(0xDADA) ?: return
        val payload = String(mfgData, Charsets.UTF_8)

        // 파싱: "SPW-001E" → prefix="SP", workerId="W-001", status="E"
        if (!payload.startsWith(SAFEPULSE_PREFIX)) return
        val workerId = payload.substring(2, payload.length - 1)
        val status = payload.last()

        // 만료된 suppress 정리
        cleanupSuppressed()

        if (status == 'E') {
            // 수신자가 이미 해제한 경보는 무시
            if (workerId in suppressedWorkerIds) return

            val rssi = result.rssi
            val distance = estimateDistance(rssi)
            val alertIntensity = calculateAlertIntensity(distance)
            val now = System.currentTimeMillis()

            // 거리 구간 분류 (연구 기반: MobileHCI 2020)
            val zone = when {
                rssi > -60 || distance < 10.0 -> "IMMEDIATE"
                rssi > -75 || distance < 30.0 -> "NEAR"
                else -> "FAR"
            }

            // 중복 알림 방지: 같은 작업자 30초 이내 재수신이면 스킵
            val lastVibTime = activeEmergencyWorkers[workerId] ?: 0L
            val shouldVibrate = now - lastVibTime > 30000

            if (shouldVibrate) {
                activeEmergencyWorkers[workerId] = now
                Log.w(TAG, "🚨 EMERGENCY from $workerId | RSSI=$rssi, dist=${"%.1f".format(distance)}m, zone=$zone")
                vibrateByDistance(context, alertIntensity, zone)
                // 긴급 비프음 (수신 측: 삐-삐-삐)
                try {
                    val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
                    for (i in 0 until 3) {
                        android.os.Handler(context.mainLooper).postDelayed({
                            try { toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200) } catch (_: Exception) {}
                        }, (i * 400).toLong())
                    }
                    android.os.Handler(context.mainLooper).postDelayed({ try { toneGen.release() } catch (_: Exception) {} }, 1400)
                } catch (_: Exception) {}
            }

            // fullScreenIntent로 P2pAlertActivity 실행 (BAL_BLOCK 우회)
            val appCtx = context.applicationContext
            val prefs = appCtx.getSharedPreferences("safepulse", Context.MODE_PRIVATE)
            val registry = prefs.getString("workerRegistry", "") ?: ""
            val workerName = registry.split(",")
                .mapNotNull { e -> val p = e.split(":"); if (p.size == 2 && p[0].trim() == workerId) p[1].trim() else null }
                .firstOrNull() ?: workerId

            try {
                val alertIntent = Intent(appCtx, com.dainon.safepulse.ui.P2pAlertActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("workerId", workerId)
                    putExtra("workerName", workerName)
                    putExtra("distance", distance)
                    putExtra("zone", zone)
                }
                val pi = android.app.PendingIntent.getActivity(
                    appCtx, 0, alertIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val notification = androidx.core.app.NotificationCompat.Builder(appCtx, "safepulse_alert")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("주변 긴급")
                    .setContentText("$workerName ${"%.0f".format(distance)}m")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(pi, true)
                    .setAutoCancel(true)
                    .setTimeoutAfter(1000)
                    .build()

                val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(999, notification)
            } catch (e: Exception) {
                Log.e(TAG, "P2P alert failed: ${e.message}")
            }
        } else if (status == 'N') {
            // 정상으로 복귀 → 진동 취소 + suppress 해제
            if (workerId in activeEmergencyWorkers) {
                activeVibrator?.cancel()
                activeEmergencyWorkers.remove(workerId)
                Log.d(TAG, "Worker $workerId back to normal → vibration cancelled")
            }
            suppressedWorkerIds.remove(workerId)
            suppressedTimestamps.remove(workerId)
        }
    }

    // ════════════════════════════════════════
    // 거리 추정 + 경보 강도 계산 (특허 핵심)
    // ════════════════════════════════════════

    /**
     * RSSI → 거리 추정 (미터)
     * 경로손실모델: d = 10 ^ ((TX_POWER - RSSI) / (10 * N))
     */
    private fun estimateDistance(rssi: Int): Double {
        if (rssi >= 0) return 0.1
        val distance = 10.0.pow((TX_POWER - rssi) / (10.0 * PATH_LOSS_N))
        return distance.coerceIn(0.1, D_MAX + 10)
    }

    /**
     * 특허 수식: A(d) = A_max × max(0, 1 - d / D_max)
     * 가까울수록 강하게 (100%), 멀수록 약하게 (0%)
     *
     * @return 진동 강도 0~100 (%)
     */
    private fun calculateAlertIntensity(distance: Double): Int {
        val intensity = 100.0 * max(0.0, 1.0 - distance / D_MAX)
        return intensity.toInt().coerceIn(0, 100)
    }

    /**
     * 3구간 거리비례 반복 진동 (MobileHCI 2020 연구 기반)
     *
     * 핵심: inter-pulse interval이 긴급도 인지에 가장 효과적
     * - 즉시근접(<10m): 쉴 틈 없이 빠른 연속 → 즉시 행동
     * - 근처(10-30m): 3연타 + 2초 휴식 → 높은 긴급도
     * - 멀리(>30m): 2연타 + 5초 휴식 → 인지만
     *
     * 반복(repeat≥0)으로 사용자가 직접 해제해야 멈춤
     */
    private fun vibrateByDistance(context: Context, intensityPercent: Int, zone: String = "NEAR") {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        activeVibrator = vibrator  // dismiss 시 cancel 가능하도록 저장

        when (zone) {
            "IMMEDIATE" -> {
                // 즉시근접: 100ms on/50ms off × 연속 반복 (최대 강도)
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 100, 50, 100, 50, 100, 50, 100, 50, 100, 300),
                    intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0),
                    0  // 처음부터 반복
                ))
            }
            "NEAR" -> {
                // 근처: 150ms × 3연타 + 2초 휴식, 반복
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 150, 100, 150, 100, 150, 2000),
                    intArrayOf(0, 200, 0, 200, 0, 200, 0),
                    0  // 반복
                ))
            }
            "FAR" -> {
                // 멀리: 200ms × 2연타 + 5초 휴식, 반복
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 150, 200, 5000),
                    intArrayOf(0, 150, 0, 150, 0),
                    0  // 반복
                ))
            }
        }
    }
}
