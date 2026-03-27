package com.dainon.safepulse.phone.service

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import com.dainon.safepulse.BuildConfig
import com.dainon.safepulse.phone.ui.EmergencyMapActivity
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
        const val NOTIFICATION_ID = 100

        var lastWatchData: WatchData? = null
        var isWatchConnected = false
        var lastEmergencyVibTime = 0L
        var activeVibrator: Vibrator? = null  // MainActivity에서 cancel 가능
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
                    // 즉시 MainActivity 실행 + P2P 카드 표시
                    try {
                        startActivity(Intent(this@BridgeService, com.dainon.safepulse.phone.ui.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("p2p_emergency", true)
                            putExtra("workerId", workerId)
                        })
                    } catch (_: Exception) {}
                    // 브로드캐스트도 전송 (화면 켜져 있을 때)
                    sendBroadcast(Intent("com.dainon.safepulse.companion.P2P_EMERGENCY").apply {
                        putExtra("workerId", workerId)
                    })
                    // 백그라운드로 서버 조회 → 지도 실행
                    scope.launch { launchEmergencyMap(workerId) }
                }

                scope.launch { forwardEmergencyToServer(workerId) }

                // 중복 진동 방지 (10초 이내 재수신 무시)
                if (now - lastEmergencyVibTime < 10000) return
                lastEmergencyVibTime = now

                val vibEnabled = prefs.getBoolean("vibrationEnabled", true)
                val soundEnabled = prefs.getBoolean("soundEnabled", true)

                // 폰 진동 (설정 체크)
                if (!vibEnabled && !soundEnabled) return
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
                }
                activeVibrator = vibrator
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 1000), -1))
                // 비프음 (삐-삐-삐)
                try {
                    val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
                    for (i in 0 until 3) {
                        android.os.Handler(mainLooper).postDelayed({
                            try { toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200) } catch (_: Exception) {}
                        }, (i * 400).toLong())
                    }
                    android.os.Handler(mainLooper).postDelayed({ try { toneGen.release() } catch (_: Exception) {} }, 1400)
                } catch (_: Exception) {}
            }

            // BLE에서는 상태 브로드캐스트 안 함 (Wearable Data Layer에서만 상태 수신)
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

    /** 타 작업자 긴급 → 서버에서 위치 조회 → 지도 Activity 실행 */
    private suspend fun launchEmergencyMap(workerId: String) {
        try {
            // 서버에서 작업자 정보 + 위치 조회
            val request = Request.Builder()
                .url("$serverUrl/api/workers/$workerId")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return
                val data = gson.fromJson(body, Map::class.java) as? Map<String, Any> ?: return
                val worker = data["worker"] as? Map<String, Any>

                val name = worker?.get("name")?.toString() ?: workerId
                val zone = worker?.get("zone")?.toString() ?: ""
                val location = worker?.get("location")?.toString() ?: ""

                // 최신 센서 데이터에서 위치 추출
                val sensorHistory = data["sensorHistory"] as? List<Map<String, Any>>
                val latest = sensorHistory?.firstOrNull()
                val lat = (latest?.get("latitude") as? Number)?.toDouble() ?: 37.4602
                val lng = (latest?.get("longitude") as? Number)?.toDouble() ?: 126.4407

                Log.d(TAG, "Emergency worker location: $name at $lat, $lng ($zone)")

                // Activity 실행 (FLAG_ACTIVITY_NEW_TASK: Service에서 시작)
                val intent = Intent(this@BridgeService, EmergencyMapActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("workerId", workerId)
                    putExtra("workerName", name)
                    putExtra("lat", lat)
                    putExtra("lng", lng)
                    putExtra("zone", "$location ($zone)")
                    putExtra("distance", 0.0) // BLE 거리는 별도 계산
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Emergency map launch failed: ${e.message}")
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY  // 앱 종료 시 자동 재시작
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
