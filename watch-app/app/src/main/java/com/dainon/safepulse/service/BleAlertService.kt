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

    // ════════════════════════════════════════
    // 광고 (Advertise) — 내 상태를 주변에 알림
    // ════════════════════════════════════════

    /** 평상시: 정상 상태 광고 */
    fun startNormalAdvertise(context: Context, workerId: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        advertiser = adapter.bluetoothLeAdvertiser ?: return

        val data = buildAdvertiseData(workerId, false)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)  // 저전력
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .setConnectable(false)
            .setTimeout(0)  // 무한
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        Log.d(TAG, "Normal advertise started: $workerId")
    }

    /** 응급 시: 긴급 경보 광고 */
    fun broadcastEmergency(context: Context, workerId: String) {
        isEmergency = true
        emergencyWorkerId = workerId

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        advertiser = adapter.bluetoothLeAdvertiser ?: return

        // 기존 광고 중지 후 긴급 모드로 재시작
        advertiser?.stopAdvertising(advertiseCallback)

        val data = buildAdvertiseData(workerId, true)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)  // 최대 빈도
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)     // 최대 출력
            .setConnectable(false)
            .setTimeout(0)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        Log.w(TAG, "🚨 EMERGENCY advertise started: $workerId")
    }

    /** 경보 해제 */
    fun cancelEmergency(context: Context) {
        isEmergency = false
        advertiser?.stopAdvertising(advertiseCallback)
        // 정상 모드로 복귀
        startNormalAdvertise(context, emergencyWorkerId)
        Log.d(TAG, "Emergency cancelled, back to normal")
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
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        scanner = adapter.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // 필터: Manufacturer ID 0xDADA
        val filter = ScanFilter.Builder()
            .setManufacturerData(0xDADA, byteArrayOf())
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback(context))
        isScanning = true
        Log.d(TAG, "BLE scan started")
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

        if (status == 'E') {
            // 🚨 긴급 경보 수신!
            val rssi = result.rssi
            val distance = estimateDistance(rssi)
            val alertIntensity = calculateAlertIntensity(distance)

            Log.w(TAG, "🚨 EMERGENCY from $workerId | RSSI=$rssi, dist=${distance}m, intensity=${alertIntensity}%")

            // 거리비례 진동!
            vibrateByDistance(context, alertIntensity)

            // UI에 알림
            context.sendBroadcast(Intent("com.dainon.safepulse.P2P_ALERT").apply {
                putExtra("workerId", workerId)
                putExtra("rssi", rssi)
                putExtra("distance", distance)
                putExtra("intensity", alertIntensity)
            })
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
     * 거리에 비례하는 진동 출력
     * 가까이 = 강하고 길게, 멀리 = 약하고 짧게
     */
    private fun vibrateByDistance(context: Context, intensityPercent: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val amplitude = (intensityPercent * 2.55).toInt().coerceIn(1, 255)  // 0~100% → 1~255
        val duration = (intensityPercent * 5L + 100).coerceIn(100, 1000)    // 100ms~1000ms

        if (intensityPercent >= 70) {
            // 가까이: 강한 반복 진동
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, duration, 150, duration, 150, duration),
                intArrayOf(0, amplitude, 0, amplitude, 0, amplitude),
                -1
            ))
        } else if (intensityPercent >= 30) {
            // 중간: 2회 진동
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, duration, 200, duration),
                intArrayOf(0, amplitude, 0, amplitude),
                -1
            ))
        } else if (intensityPercent > 0) {
            // 멀리: 1회 약한 진동
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        }
    }
}
