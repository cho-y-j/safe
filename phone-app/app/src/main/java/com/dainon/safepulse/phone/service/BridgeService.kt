package com.dainon.safepulse.phone.service

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import com.dainon.safepulse.BuildConfig
import com.dainon.safepulse.phone.ui.MainActivity
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
        const val CHANNEL_ALERT = "safepulse_p2p_alert"  // ★ P2P 긴급 알림 채널
        const val NOTIFICATION_ID = 100
        const val P2P_NOTIFICATION_ID = 200  // ★ P2P 긴급 알림 ID

        var lastWatchData: WatchData? = null
        var isWatchConnected = false
        var lastEmergencyVibTime = 0L
        var activeVibrator: Vibrator? = null
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
        try {
            startForeground(NOTIFICATION_ID, buildNotification("워치 연결 대기 중..."))
        } catch (e: Exception) {
            Log.e(TAG, "Foreground failed: ${e.message}")
        }
        try {
            startBleScanning()
        } catch (e: Exception) {
            Log.e(TAG, "BLE scan failed: ${e.message}")
        }
        startServerPolling()
        Log.d(TAG, "BridgeService started")
    }

    // ═══ BLE 스캔 — 워치 신호 수신 ═══

    private fun startBleScanning() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) { Log.e(TAG, "BLE: BluetoothAdapter is null"); return }
            if (!adapter.isEnabled) { Log.e(TAG, "BLE: Bluetooth is disabled"); return }

            scanner = adapter.bluetoothLeScanner
            if (scanner == null) { Log.e(TAG, "BLE: bluetoothLeScanner is null"); return }

            // Android 12+ (API 31): BLUETOOTH_SCAN 필요
            // Android 10~11 (API 29~30): ACCESS_FINE_LOCATION 필요
            if (Build.VERSION.SDK_INT >= 31) {
                if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLE: BLUETOOTH_SCAN not granted (API ${Build.VERSION.SDK_INT})")
                    return
                }
            } else {
                if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLE: ACCESS_FINE_LOCATION not granted (API ${Build.VERSION.SDK_INT})")
                    return
                }
            }

            val filter = ScanFilter.Builder()
                .setManufacturerData(0xDADA, byteArrayOf()) // SafePulse 워치
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner?.startScan(listOf(filter), settings, scanCallback)
            Log.d(TAG, "✅ BLE scan started (API ${Build.VERSION.SDK_INT})")
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan SecurityException: ${e.message}")
        } catch (e: Exception) {
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
            Log.d(TAG, "BLE scan: $workerId status=$status RSSI=${result.rssi}")
            val prefs = getSharedPreferences("safepulse_companion", MODE_PRIVATE)
            val myWorkerId = prefs.getString("workerId", "") ?: ""
            val isMyWatch = myWorkerId.isNotBlank() && workerId == myWorkerId

            // BLE는 P2P 긴급 수신 전용 — 내 워치 상태는 Wearable Data Layer로만 수신
            // (BLE 브로드캐스트는 모든 워치가 수신되므로 상태 표시에 사용하면 왔다갔다함)

            // 긴급 신호 처리
            if (status == "E") {
                val now = System.currentTimeMillis()

                if (isMyWatch) {
                    Log.w(TAG, "🚨 MY watch emergency: $workerId")
                } else {
                    Log.w(TAG, "🚨 OTHER worker emergency: $workerId (I am $myWorkerId)")

                    // ★ notification으로 먼저 알림 (지도 자동 안 뜸)
                    // 탭하면 MainActivity → P2P 카드 → "위치 보기" 선택 시 지도
                    showP2pNotification(workerId)

                    // 브로드캐스트 (앱 열려있으면 P2P 카드 표시)
                    sendBroadcast(Intent("com.dainon.safepulse.companion.P2P_EMERGENCY").apply {
                        putExtra("workerId", workerId)
                    })
                }

                scope.launch { forwardEmergencyToServer(workerId) }

                // 중복 진동 방지 (10초 이내 재수신 무시)
                if (now - lastEmergencyVibTime < 10000) return
                lastEmergencyVibTime = now

                val vibEnabled = prefs.getBoolean("vibrationEnabled", true)
                val soundEnabled = prefs.getBoolean("soundEnabled", true)
                if (!vibEnabled && !soundEnabled) return

                // 폰 진동
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
                }
                activeVibrator = vibrator
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 1000), -1))

                // 비프음
                try {
                    val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
                    for (i in 0 until 3) {
                        Handler(mainLooper).postDelayed({
                            try { toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200) } catch (_: Exception) {}
                        }, (i * 400).toLong())
                    }
                    Handler(mainLooper).postDelayed({ try { toneGen.release() } catch (_: Exception) {} }, 1400)
                } catch (_: Exception) {}

            } else if (status == "N") {
                // ★ 발신자 해제 → notification 제거 + 진동 정지
                if (!isMyWatch) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(P2P_NOTIFICATION_ID)
                    activeVibrator?.cancel()
                    Log.d(TAG, "P2P alert cleared for $workerId")
                }
            }
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

    /** ★ P2P 긴급 notification — 탭하면 MainActivity로 이동 (지도 자동 안 뜸) */
    private fun showP2pNotification(workerId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("p2p_emergency", true)
            putExtra("workerId", workerId)
        }
        val pi = PendingIntent.getActivity(this, workerId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = Notification.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("주변 긴급 신호")
            .setContentText("$workerId 이상 징후 — 탭하여 확인")
            .setFullScreenIntent(pi, true)  // 화면 꺼져있어도 뜸
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(P2P_NOTIFICATION_ID, notification)
        Log.d(TAG, "🚨 P2P notification posted: $workerId")
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
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "워치 연결", NotificationManager.IMPORTANCE_LOW))
        // ★ P2P 긴급 알림 채널 (높은 우선순위)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "주변 긴급 알림", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY  // 앱 종료 시 자동 재시작
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        // ★ OS가 죽여도 5초 후 자동 재시작
        Log.w(TAG, "BridgeService destroyed → scheduling restart")
        val restartIntent = Intent(applicationContext, BridgeService::class.java)
        val pi = android.app.PendingIntent.getForegroundService(
            applicationContext, 999, restartIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        am.setExactAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 5000, pi
        )
        super.onDestroy()
    }
}
