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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = OkHttpClient()
    private val gson = Gson()

    private val watchReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val workerId = intent.getStringExtra("workerId") ?: "--"
            val status = intent.getStringExtra("status") ?: "N"
            val rssi = intent.getIntExtra("rssi", 0)

            tvConnectionStatus.text = "● 워치 연결됨"
            tvConnectionStatus.setTextColor(0xFF43A047.toInt())
            tvWorkerId.text = workerId

            if (status == "E") {
                tvWatchStatus.text = "긴급!"
                tvWatchStatus.setTextColor(0xFFE53935.toInt())
                btnAck.visibility = View.VISIBLE // 확인 버튼 표시
            } else {
                tvWatchStatus.text = "정상"
                tvWatchStatus.setTextColor(0xFF43A047.toInt())
                btnAck.visibility = View.GONE
            }

            val signalStr = when {
                rssi > -50 -> "강함"
                rssi > -70 -> "보통"
                else -> "약함"
            }
            tvSignal.text = signalStr
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

        val prefs = getSharedPreferences("safepulse_companion", MODE_PRIVATE)
        etServerUrl.setText(prefs.getString("serverUrl", "http://192.168.0.10:4000"))

        // 확인 버튼 — 워치의 경보 해제
        btnAck.setOnClickListener {
            btnAck.visibility = View.GONE
            tvWatchStatus.text = "확인됨"
            tvWatchStatus.setTextColor(0xFF43A047.toInt())
            // 서버에 확인 전송
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
                        tvWorkerId.text = map["workerId"]?.toString() ?: "--"
                        val stateKr = map["stateKr"]?.toString() ?: "정상"
                        tvWatchStatus.text = stateKr
                        tvWatchStatus.setTextColor(
                            if (stateKr.contains("응급") || stateKr.contains("위험")) 0xFFE53935.toInt()
                            else if (stateKr.contains("이상") || stateKr.contains("낙상")) 0xFFFF9800.toInt()
                            else 0xFF43A047.toInt()
                        )
                        val hr = (map["heartRate"] as? Number)?.toInt() ?: 0
                        tvSignal.text = if (hr > 0) "💓$hr" else "--"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WatchMsg", "Parse error: ${e.message}")
                }
            }
            event.path.startsWith("/safepulse/alert") -> {
                runOnUiThread {
                    btnAck.visibility = View.VISIBLE
                    tvWatchStatus.text = "🚨 긴급"
                    tvWatchStatus.setTextColor(0xFFE53935.toInt())
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

    override fun onResume() {
        super.onResume()
        registerReceiver(watchReceiver, IntentFilter("com.dainon.safepulse.companion.WATCH_UPDATE"), RECEIVER_NOT_EXPORTED)
        registerReceiver(alertsReceiver, IntentFilter("com.dainon.safepulse.companion.ALERTS_UPDATE"), RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(watchReceiver)
        unregisterReceiver(alertsReceiver)
    }

    override fun onDestroy() {
        try { Wearable.getMessageClient(this).removeListener(messageListener) } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }
}
