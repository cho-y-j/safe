package com.dainon.safepulse.phone.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.dainon.safepulse.R
import com.dainon.safepulse.phone.service.BridgeService
import com.dainon.safepulse.phone.ui.EmergencyMapActivity
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity() {

    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvWorkerId: TextView
    private lateinit var tvWatchStatus: TextView
    private lateinit var tvSignal: TextView
    private lateinit var btnAck: Button
    private lateinit var tvAlerts: TextView
    private lateinit var etServerUrl: EditText

    // 실시간 바이탈
    private lateinit var tvPhoneHR: TextView
    private lateinit var tvPhoneSpO2: TextView
    private lateinit var tvPhoneTemp: TextView
    private lateinit var tvPhoneStress: TextView
    private lateinit var tvPhoneBaseline: TextView

    // 긴급 카드
    private lateinit var emergencyCard: LinearLayout
    private lateinit var tvEmergencyHR: TextView
    private lateinit var tvEmergencySpO2: TextView
    private lateinit var tvEmergencyState: TextView
    private lateinit var tvEmergencyElapsed: TextView
    private lateinit var btnPhoneAck: Button
    private lateinit var btnCall119: Button
    private var emergencyStartTime = 0L
    private var isEmergencyActive = false
    private var watchNodeId: String? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = OkHttpClient()
    private val gson = Gson()

    // BLE watchReceiver — 사용 안 함
    private val watchReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {}
    }

    // 수신자용: P2P 긴급 수신 (BridgeService에서 발송)
    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val emergencyWorkerId = intent.getStringExtra("workerId") ?: "?"
            // P2P 카드 표시
            findViewById<LinearLayout>(R.id.p2pCard).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvP2pTitle).text = "🚨 주변 긴급! $emergencyWorkerId"

            // 위치 보기 버튼
            findViewById<Button>(R.id.btnP2pMap).setOnClickListener {
                val prefs = getSharedPreferences("safepulse_companion", MODE_PRIVATE)
                val url = prefs.getString("serverUrl", "http://192.168.0.9:4000") ?: return@setOnClickListener
                scope.launch(Dispatchers.IO) {
                    try {
                        val resp = client.newCall(okhttp3.Request.Builder().url("$url/api/workers/$emergencyWorkerId").build()).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: return@launch
                            val data = gson.fromJson(body, Map::class.java) as? Map<String, Any> ?: return@launch
                            val worker = data["worker"] as? Map<String, Any>
                            val history = data["sensorHistory"] as? List<Map<String, Any>>
                            val latest = history?.firstOrNull()
                            val lat = (latest?.get("latitude") as? Number)?.toDouble() ?: 37.4602
                            val lng = (latest?.get("longitude") as? Number)?.toDouble() ?: 126.4407
                            val name = worker?.get("name")?.toString() ?: emergencyWorkerId
                            withContext(Dispatchers.Main) {
                                startActivity(Intent(this@MainActivity, EmergencyMapActivity::class.java).apply {
                                    putExtra("workerId", emergencyWorkerId)
                                    putExtra("workerName", name)
                                    putExtra("lat", lat)
                                    putExtra("lng", lng)
                                })
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // 확인 (알림 종료) 버튼
            findViewById<Button>(R.id.btnP2pDismiss).setOnClickListener {
                findViewById<LinearLayout>(R.id.p2pCard).visibility = View.GONE
                // 진동 멈추기
                BridgeService.activeVibrator?.cancel()
            }
        }
    }

    private val alertsReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val alertsJson = intent.getStringExtra("alerts") ?: return
            try {
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val alerts: List<Map<String, Any>> = gson.fromJson(alertsJson, type)
                val text = alerts.take(3).joinToString("\n") { alert ->
                    val level = alert["level"] ?: ""
                    val msg = alert["message"] ?: ""
                    "$level: $msg"
                }
                tvAlerts.text = if (text.isNotBlank()) text else "알림 없음"
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvWorkerId = findViewById(R.id.tvWorkerId)
        tvWatchStatus = findViewById(R.id.tvWatchStatus)
        tvSignal = findViewById(R.id.tvSignal)
        btnAck = findViewById(R.id.btnAck)
        tvAlerts = findViewById(R.id.tvAlerts)
        etServerUrl = findViewById(R.id.etServerUrl)

        // 실시간 바이탈
        tvPhoneHR = findViewById(R.id.tvPhoneHR)
        tvPhoneSpO2 = findViewById(R.id.tvPhoneSpO2)
        tvPhoneTemp = findViewById(R.id.tvPhoneTemp)
        tvPhoneStress = findViewById(R.id.tvPhoneStress)
        tvPhoneBaseline = findViewById(R.id.tvPhoneBaseline)

        // 긴급 카드
        emergencyCard = findViewById(R.id.emergencyCard)
        tvEmergencyHR = findViewById(R.id.tvEmergencyHR)
        tvEmergencySpO2 = findViewById(R.id.tvEmergencySpO2)
        tvEmergencyState = findViewById(R.id.tvEmergencyState)
        tvEmergencyElapsed = findViewById(R.id.tvEmergencyElapsed)
        btnPhoneAck = findViewById(R.id.btnPhoneAck)
        btnCall119 = findViewById(R.id.btnCall119)

        val prefs = getSharedPreferences("safepulse_companion", MODE_PRIVATE)
        etServerUrl.setText(prefs.getString("serverUrl", "http://192.168.0.10:4000"))

        // 워치 노드 ID 조회 (ACK 전송용)
        try {
            Wearable.getNodeClient(this).connectedNodes
                .addOnSuccessListener { nodes -> watchNodeId = nodes.firstOrNull()?.id }
        } catch (_: Exception) {}

        // ═══ 문제 없음 (경보 해제) — 워치에 직접 ACK 전송 ═══
        btnPhoneAck.setOnClickListener {
            dismissEmergency()
            // 워치에 ACK 전송 (Wearable Data Layer)
            val nodeId = watchNodeId
            if (nodeId != null) {
                Wearable.getMessageClient(this)
                    .sendMessage(nodeId, "/safepulse/phone_ack", "ack".toByteArray())
                    .addOnSuccessListener { android.util.Log.d("PhoneAck", "ACK sent to watch") }
                    .addOnFailureListener { android.util.Log.e("PhoneAck", "ACK failed: ${it.message}") }
            }
            // 서버에도 ACK 전송
            scope.launch(Dispatchers.IO) {
                try {
                    val url = prefs.getString("serverUrl", "http://192.168.0.10:4000") ?: return@launch
                    val json = gson.toJson(mapOf("action" to "acknowledge", "source" to "phone"))
                    client.newCall(Request.Builder()
                        .url("$url/api/alerts/acknowledge")
                        .post(json.toRequestBody("application/json".toMediaType()))
                        .build()).execute()
                } catch (_: Exception) {}
            }
        }

        // 119 신고
        btnCall119.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:119")))
            } catch (_: Exception) {}
        }

        // 소리/진동 설정 Switch
        val switchVib = findViewById<android.widget.Switch>(R.id.switchVibration)
        val switchSound = findViewById<android.widget.Switch>(R.id.switchSound)
        switchVib.isChecked = prefs.getBoolean("vibrationEnabled", true)
        switchSound.isChecked = prefs.getBoolean("soundEnabled", true)
        switchVib.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("vibrationEnabled", checked).apply() }
        switchSound.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("soundEnabled", checked).apply() }

        // 레거시 확인 버튼
        btnAck.setOnClickListener {
            btnAck.visibility = View.GONE
            tvWatchStatus.text = "확인됨"
            tvWatchStatus.setTextColor(0xFF43A047.toInt())
        }

        // 긴급 경과 시간 타이머
        startEmergencyTimer()

        // 내 건강 리포트
        findViewById<Button>(R.id.btnHealth).setOnClickListener {
            startActivity(Intent(this, HealthReportActivity::class.java))
        }

        // 설정 저장
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit().putString("serverUrl", etServerUrl.text.toString()).apply()
            Toast.makeText(this, "저장됨", Toast.LENGTH_SHORT).show()
        }

        requestPermissions()
        // 워치 메시지 직접 수신 (MessageClient)
        startWatchListener()
        handleP2pIntent(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            try { startForegroundService(Intent(this, BridgeService::class.java)) } catch (e: Exception) {
                android.util.Log.e("Main", "Service start failed: ${e.message}")
            }
        }
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val needed = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        } else {
            try { startForegroundService(Intent(this, BridgeService::class.java)) } catch (e: Exception) {
                android.util.Log.e("Main", "Service start failed: ${e.message}")
            }
        }
    }

    // ═══ Wearable MessageClient (워치 메시지 직접 수신) ═══

    private val messageListener = MessageClient.OnMessageReceivedListener { event ->
        val data = String(event.data, Charsets.UTF_8)
        android.util.Log.d("WatchMsg", "📩 ${event.path}: $data")

        when {
            event.path.startsWith("/safepulse/status") -> {
                try {
                    val map = gson.fromJson(data, Map::class.java) as Map<String, Any>
                    runOnUiThread {
                        tvConnectionStatus.text = "● 워치 연결됨"
                        tvConnectionStatus.setTextColor(0xFF43A047.toInt())
                        val receivedWorkerId = map["workerId"]?.toString() ?: "--"
                        tvWorkerId.text = receivedWorkerId
                        // 내 워치 ID 저장 (BridgeService에서 발신자/수신자 구분용)
                        if (receivedWorkerId != "--") {
                            getSharedPreferences("safepulse_companion", MODE_PRIVATE).edit()
                                .putString("workerId", receivedWorkerId).apply()
                        }
                        val stateKr = map["stateKr"]?.toString() ?: "정상"
                        val state = map["state"]?.toString() ?: "NORMAL"
                        tvWatchStatus.text = stateKr
                        tvWatchStatus.setTextColor(
                            if (stateKr.contains("응급") || stateKr.contains("위험")) 0xFFE53935.toInt()
                            else if (stateKr.contains("이상") || stateKr.contains("낙상")) 0xFFFF9800.toInt()
                            else 0xFF43A047.toInt()
                        )
                        val hr = (map["heartRate"] as? Number)?.toInt() ?: 0
                        val spo2 = (map["spo2"] as? Number)?.toInt() ?: 0
                        val bodyTemp = (map["bodyTemp"] as? Number)?.toDouble() ?: 0.0
                        val stress = (map["stress"] as? Number)?.toInt() ?: 0
                        val restHrMean = (map["restHrMean"] as? Number)?.toInt() ?: 0
                        val activeHrMean = (map["activeHrMean"] as? Number)?.toInt() ?: 0

                        // 6칸 바이탈 업데이트
                        tvPhoneHR.text = if (hr > 0) "$hr" else "--"
                        tvPhoneSpO2.text = if (spo2 > 0) "$spo2%" else "--%"
                        tvPhoneTemp.text = if (bodyTemp > 30) "${"%.1f".format(bodyTemp)}°" else "--°"
                        tvPhoneStress.text = if (stress > 0) "$stress" else "--"
                        tvPhoneBaseline.text = if (restHrMean > 0) "$restHrMean" else "--"
                        tvSignal.text = if (activeHrMean > 0) "$activeHrMean" else "--"

                        // 긴급 카드 업데이트
                        if (state == "EMERGENCY" || state == "WAITING_ACK" || state == "FALL_DETECTED") {
                            showEmergency(hr, spo2, stateKr)
                        } else if (state == "NORMAL" || state == "ACKNOWLEDGED") {
                            dismissEmergency()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WatchMsg", "Parse error: ${e.message}")
                }
            }
            event.path.startsWith("/safepulse/alert") -> {
                runOnUiThread {
                    showEmergency(0, 0, "긴급")
                }
            }
        }
    }

    private fun startWatchListener() {
        try {
            android.util.Log.d("WatchMsg", "Registering listener...")
            Wearable.getMessageClient(this).addListener(messageListener)
                .addOnSuccessListener { android.util.Log.d("WatchMsg", "✅ Listener OK!") }
                .addOnFailureListener { android.util.Log.e("WatchMsg", "❌ Listener FAIL: ${it.message}") }

            Wearable.getNodeClient(this).connectedNodes
                .addOnSuccessListener { nodes -> android.util.Log.d("WatchMsg", "Nodes: ${nodes.map { "${it.displayName}(${it.id})" }}") }
                .addOnFailureListener { android.util.Log.e("WatchMsg", "❌ Nodes FAIL: ${it.message}") }
        } catch (e: Exception) {
            android.util.Log.e("WatchMsg", "❌ Exception: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleP2pIntent(intent)
    }

    private fun handleP2pIntent(intent: Intent) {
        if (intent.getBooleanExtra("p2p_emergency", false)) {
            val emergencyWorkerId = intent.getStringExtra("workerId") ?: "?"
            findViewById<LinearLayout>(R.id.p2pCard).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvP2pTitle).text = "🚨 주변 긴급! $emergencyWorkerId"
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(watchReceiver, IntentFilter("com.dainon.safepulse.companion.WATCH_UPDATE"), RECEIVER_NOT_EXPORTED)
        registerReceiver(alertsReceiver, IntentFilter("com.dainon.safepulse.companion.ALERTS_UPDATE"), RECEIVER_NOT_EXPORTED)
        registerReceiver(p2pReceiver, IntentFilter("com.dainon.safepulse.companion.P2P_EMERGENCY"), RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(watchReceiver)
        unregisterReceiver(alertsReceiver)
        try { unregisterReceiver(p2pReceiver) } catch (_: Exception) {}
    }

    // ═══ 긴급 카드 제어 ═══

    private fun showEmergency(hr: Int, spo2: Int, stateKr: String) {
        if (!isEmergencyActive) {
            isEmergencyActive = true
            emergencyStartTime = System.currentTimeMillis()
        }
        emergencyCard.visibility = View.VISIBLE
        btnAck.visibility = View.GONE  // 레거시 버튼 숨김
        if (hr > 0) tvEmergencyHR.text = "$hr"
        if (spo2 > 0) tvEmergencySpO2.text = "$spo2%"
        tvEmergencyState.text = stateKr
    }

    private fun dismissEmergency() {
        isEmergencyActive = false
        emergencyCard.visibility = View.GONE
        btnAck.visibility = View.GONE
        tvWatchStatus.text = "확인됨"
        tvWatchStatus.setTextColor(0xFF43A047.toInt())
    }

    private fun startEmergencyTimer() {
        scope.launch {
            while (isActive) {
                delay(1000)
                if (isEmergencyActive && emergencyStartTime > 0) {
                    val elapsed = (System.currentTimeMillis() - emergencyStartTime) / 1000
                    tvEmergencyElapsed.text = "${elapsed}��"
                }
            }
        }
    }

    override fun onDestroy() {
        try { Wearable.getMessageClient(this).removeListener(messageListener) } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }
}
