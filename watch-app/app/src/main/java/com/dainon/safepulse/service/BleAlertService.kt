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
    private const val TX_POWER = -65          // 1m 거리 기준 RSSI (Galaxy Watch 캘리브레이션)
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
    private val activeEmergencyWorkers = mutableMapOf<String, Long>()
    private var currentZone = ""           // 현재 거리 구간 (실시간 추적)
    private var pendingZone = ""          // ★ Zone 전환 대기 중
    private var pendingZoneTime = 0L      // ★ Zone 전환 시작 시각
    private val ZONE_HOLD_MS = 1000L      // ★ Zone 1초 유지해야 진짜 전환
    private var smoothedDistance = 0.0    // ★ EMA 스무딩 거리
    private var alertActivityLaunched = false  // P2pAlertActivity 실행 여부
    @Volatile var isReceivingAlert = false  // ★ P2P 진동 수신 중 (SensorService에서 낙상 감지 차단용)

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

    /** 모든 BLE 활동 정지 (작업 종료 시) */
    fun stopAll() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: SecurityException) {}
        stopScanning()
        isEmergency = false
        lastEmergencyBroadcastTime = 0L
        activeVibrator?.cancel()
        Log.d(TAG, "All BLE stopped")
    }

    /** 수신 경보 해제 (수신자가 "응답함" 또는 "도움불가" 탭) */
    fun dismissReceivedAlert(workerId: String) {
        suppressedWorkerIds.add(workerId)
        suppressedTimestamps[workerId] = System.currentTimeMillis()
        activeEmergencyWorkers.remove(workerId)
        activeVibrator?.cancel()
        currentZone = ""
        alertActivityLaunched = false
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

    /** 주변 SafePulse 기기 스캔 시작 (기존 스캔 확실히 정리 후 재시작) */
    fun startScanning(context: Context) {
        try {
            // ★ 기존 스캔 확실히 정리
            if (scanCallbackInstance != null) {
                try { scanner?.stopScan(scanCallbackInstance) } catch (_: Exception) {}
                scanCallbackInstance = null
            }
            isScanning = false

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
            Log.d(TAG, "BLE scan started (clean restart)")
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan failed: ${e.message}")
        }
    }

    fun stopScanning() {
        try { scanner?.stopScan(scanCallbackInstance) } catch (_: Exception) {}
        scanCallbackInstance = null
        isScanning = false
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
            val rawDistance = estimateDistance(rssi)
            val now = System.currentTimeMillis()

            // ★ EMA 스무딩 (노이즈 제거, 부드러운 거리 변화)
            smoothedDistance = if (smoothedDistance <= 0.0) rawDistance
                else smoothedDistance * 0.7 + rawDistance * 0.3
            val distance = smoothedDistance

            val alertIntensity = calculateAlertIntensity(distance)

            // 5구간 거리 분류 (스무딩된 거리 기반)
            val rawZone = when {
                distance < 1.5 -> "ZONE1"    // ~1m 즉시
                distance < 3.5 -> "ZONE2"    // ~3m 매우 가까움
                distance < 6.0 -> "ZONE3"    // ~5m 가까움
                distance < 12.0 -> "ZONE4"   // ~10m 중간
                else -> "ZONE5"               // 10m+ 멀리
            }

            // ★ Zone 전환: 1초 유지해야 진짜 전환 (노이즈 튐 무시)
            val zone: String
            if (rawZone != currentZone) {
                if (rawZone != pendingZone) {
                    pendingZone = rawZone
                    pendingZoneTime = now
                }
                zone = if (now - pendingZoneTime >= ZONE_HOLD_MS) {
                    pendingZone  // 1초 유지 → 진짜 전환
                } else {
                    currentZone  // 아직 유지
                }
            } else {
                pendingZone = ""
                zone = currentZone
            }

            val appCtx = context.applicationContext
            val prefs = appCtx.getSharedPreferences("safepulse", Context.MODE_PRIVATE)
            val registry = prefs.getString("workerRegistry", "") ?: ""
            val workerName = registry.split(",")
                .mapNotNull { e -> val p = e.split(":"); if (p.size == 2 && p[0].trim() == workerId) p[1].trim() else null }
                .firstOrNull() ?: workerId

            // 1) 거리 브로드캐스트 (매 수신마다) — 스무딩된 거리로 실시간 갱신
            appCtx.sendBroadcast(Intent("com.dainon.safepulse.P2P_DISTANCE").apply {
                setPackage("com.dainon.safepulse")
                putExtra("distance", distance.toDouble())
                putExtra("zone", zone)
                putExtra("workerId", workerId)
            })

            // 2) zone 전환 시 진동+비프 패턴 교체 (1초 유지 후에만)
            if (zone != currentZone) {
                currentZone = zone
                pendingZone = ""
                activeEmergencyWorkers[workerId] = now
                Log.w(TAG, "🚨 Zone changed: $zone (${"%.1f".format(distance)}m) — vibration pattern update")
                vibrateByDistance(context, alertIntensity, zone)
            }

            // 3) P2pAlertActivity 첫 1회만 실행 (이후 브로드캐스트로 거리 갱신)
            if (!alertActivityLaunched) {
                alertActivityLaunched = true
                isReceivingAlert = true  // ★ 진동 시작 → 낙상 감지 차단
                activeEmergencyWorkers[workerId] = now
                vibrateByDistance(context, alertIntensity, zone)
                // ★ SensorService(Foreground Service)를 통해 P2P 화면 발행
                //   object에서 직접 notification 발행하면 fullScreenIntent 차단됨
                try {
                    val launchIntent = Intent(appCtx, SensorService::class.java).apply {
                        action = SensorService.ACTION_LAUNCH_P2P
                        putExtra("workerId", workerId)
                        putExtra("workerName", workerName)
                        putExtra("distance", distance)
                        putExtra("zone", zone)
                    }
                    appCtx.startService(launchIntent)
                    Log.d(TAG, "🚨 P2P alert → SensorService for $workerName")
                } catch (e: Exception) {
                    Log.e(TAG, "P2P alert via SensorService failed: ${e.message}")
                }
            }
        } else if (status == 'N') {
            // 정상으로 복귀 → 진동 취소 + suppress 해제 + P2P 화면 닫기
            if (workerId in activeEmergencyWorkers) {
                activeVibrator?.cancel()
                isReceivingAlert = false  // ★ 진동 종료
                activeEmergencyWorkers.remove(workerId)
                currentZone = ""
                alertActivityLaunched = false
                stopBeep()
                // ★ P2pAlertActivity 닫기 브로드캐스트
                context.applicationContext.sendBroadcast(Intent("com.dainon.safepulse.CLOSE_P2P").apply {
                    setPackage("com.dainon.safepulse")
                })
                Log.d(TAG, "Worker $workerId back to normal → vibration cancelled + P2P closed")
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
     * 5구간 거리별 진동 간격 (간격이 핵심, 세기는 모두 MAX)
     * 가까울수록 빠르게, 멀수록 느리게
     */
    private fun vibrateByDistance(context: Context, intensityPercent: Int, zone: String = "ZONE3") {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        activeVibrator = vibrator

        // 설정 체크 (진동 끄기)
        val prefs = context.applicationContext.getSharedPreferences("safepulse", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("vibrationEnabled", true)) return

        when (zone) {
            "ZONE1" -> // ~1m: 100ms on, 50ms off 연속
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 100, 50), intArrayOf(0, 255, 0), 0))
            "ZONE2" -> // ~3m: 200ms on, 300ms off
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 300), intArrayOf(0, 255, 0), 0))
            "ZONE3" -> // ~5m: 200ms on, 1초 off
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 1000), intArrayOf(0, 255, 0), 0))
            "ZONE4" -> // ~10m: 200ms on, 2초 off
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 2000), intArrayOf(0, 255, 0), 0))
            "ZONE5" -> // 10m+: 200ms on, 4초 off
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 4000), intArrayOf(0, 255, 0), 0))
        }

        // 거리별 비프음도 반복 (설정 체크)
        if (prefs.getBoolean("soundEnabled", true)) {
            startZoneBeep(context, zone)
        }
    }

    /** 구간별 반복 비프음 */
    private var beepHandler: android.os.Handler? = null
    private var beepRunnable: Runnable? = null

    private fun startZoneBeep(context: Context, zone: String) {
        stopBeep()
        val interval = when (zone) {
            "ZONE1" -> 200L    // ~1m: 빠른 연속
            "ZONE2" -> 500L    // ~3m
            "ZONE3" -> 1000L   // ~5m
            "ZONE4" -> 2000L   // ~10m
            "ZONE5" -> 4000L   // 10m+
            else -> 1000L
        }
        beepHandler = android.os.Handler(context.mainLooper)
        val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
        beepRunnable = object : Runnable {
            override fun run() {
                try { toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150) } catch (_: Exception) {}
                beepHandler?.postDelayed(this, interval)
            }
        }
        beepHandler?.post(beepRunnable!!)
    }

    private fun stopBeep() {
        beepRunnable?.let { beepHandler?.removeCallbacks(it) }
        beepHandler = null
        beepRunnable = null
    }

    /** P2P 수신 중인지 확인 */
    fun isReceivingEmergency(): Boolean = activeEmergencyWorkers.isNotEmpty()

    /** 수신 경보 해제 시 비프도 정지 */
    fun dismissReceivedAlertFull(workerId: String) {
        dismissReceivedAlert(workerId)
        stopBeep()
    }
}
