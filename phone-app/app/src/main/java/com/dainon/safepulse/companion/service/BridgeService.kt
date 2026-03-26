package com.dainon.safepulse.companion.service

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import com.dainon.safepulse.companion.BuildConfig
import com.dainon.safepulse.companion.ui.MainActivity
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * BLE 브릿지 서비스
 * - Galaxy Watch의 BLE 광고를 수신
 * - 워치 데이터를 WiFi/LTE로 서버에 중계
 * - 워치가 WiFi 없어도 폰을 통해 서버 연결
 */
class BridgeService : Service() {

    companion object {
        const val TAG = "BridgeService"
        const val CHANNEL_ID = "safepulse_bridge"
        const val NOTIFICATION_ID = 100

        var lastWatchData: WatchData? = null
        var isWatchConnected = false
    }

    data class WatchData(
        val workerId: String,
        val status: String, // N=Normal, E=Emergency
        val rssi: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var scanner: BluetoothLeScanner? = null
    private val serverUrl get() = getSharedPreferences("safepulse_companion", MODE_PRIVATE)
        .getString("serverUrl", BuildConfig.SERVER_URL) ?: BuildConfig.SERVER_URL

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("워치 연결 대기 중..."))
        startBleScanning()
        startServerPolling()
    }

    // ═══ BLE 스캔 — 워치 신호 수신 ═══

    private fun startBleScanning() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            scanner = adapter.bluetoothLeScanner ?: return

            if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_SCAN not granted")
                return
            }

            val filter = ScanFilter.Builder()
                .setManufacturerData(0xDADA, byteArrayOf()) // SafePulse 워치
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner?.startScan(listOf(filter), settings, scanCallback)
            Log.d(TAG, "BLE scan started — looking for SafePulse watches")
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan failed: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val mfgData = result.scanRecord?.getManufacturerSpecificData(0xDADA) ?: return
            val payload = String(mfgData, Charsets.UTF_8)
            if (!payload.startsWith("SP")) return

            val workerId = payload.substring(2, payload.length - 1)
            val status = payload.last().toString()

            isWatchConnected = true
            lastWatchData = WatchData(workerId, status, result.rssi)

            // 긴급 신호 → 즉시 서버 전송
            if (status == "E") {
                Log.w(TAG, "🚨 EMERGENCY from watch $workerId!")
                scope.launch { forwardEmergencyToServer(workerId) }

                // 폰 진동
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
                }
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 1000), -1))
            }

            // 브로드캐스트
            sendBroadcast(Intent("com.dainon.safepulse.companion.WATCH_UPDATE").apply {
                putExtra("workerId", workerId)
                putExtra("status", status)
                putExtra("rssi", result.rssi)
            })

            updateNotification("워치 $workerId 연결 | ${if (status == "N") "정상" else "🚨 긴급"}")
        }
    }

    // ═══ 서버 중계 ═══

    private suspend fun forwardEmergencyToServer(workerId: String) {
        try {
            val json = gson.toJson(mapOf(
                "workerId" to workerId,
                "type" to "emergency",
                "level" to "danger",
                "message" to "🚨 $workerId 긴급 — 스마트폰 앱 경유 전송",
            ))
            val request = Request.Builder()
                .url("$serverUrl/api/alerts/emergency")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute()
            Log.d(TAG, "Emergency forwarded to server")
        } catch (e: Exception) {
            Log.e(TAG, "Server forward failed: ${e.message}")
        }
    }

    // ═══ 서버 폴링 — 관제 메시지 수신 ═══

    private fun startServerPolling() {
        scope.launch {
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url("$serverUrl/api/alerts?limit=5")
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        sendBroadcast(Intent("com.dainon.safepulse.companion.ALERTS_UPDATE").apply {
                            putExtra("alerts", body)
                        })
                    }
                } catch (_: Exception) {}
                delay(15000) // 15초마다
            }
        }
    }

    // ═══ 알림 ═══

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "워치 연결", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SafePulse")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
