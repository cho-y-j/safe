package com.dainon.safepulse.phone.service

import android.content.Intent
import android.os.*
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Wearable Data Layer 수신 — 워치에서 오는 메시지 처리
 * 기존 BT 연결 활용 (별도 BLE 불필요)
 */
class WearableListenerService : com.google.android.gms.wearable.WearableListenerService() {

    companion object {
        const val TAG = "WearListener"
    }

    private val gson = Gson()
    private val client = OkHttpClient()

    override fun onMessageReceived(event: MessageEvent) {
        val data = String(event.data, Charsets.UTF_8)
        Log.d(TAG, "📩 Message from watch: ${event.path} → $data")

        when (event.path) {
            "/safepulse/sensor" -> handleSensorData(data)
            "/safepulse/alert" -> handleAlert(data)
            "/safepulse/ack" -> handleAck(data)
            "/safepulse/status" -> handleStatus(data)
        }
    }

    private fun handleSensorData(json: String) {
        // 서버로 중계
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverUrl = getSharedPreferences("safepulse_companion", MODE_PRIVATE)
                    .getString("serverUrl", "http://192.168.0.10:4000") ?: return@launch

                val type = object : TypeToken<Map<String, Any>>() {}.type
                val data: Map<String, Any> = gson.fromJson(json, type)
                val workerId = data["workerId"] ?: return@launch

                val request = Request.Builder()
                    .url("$serverUrl/api/workers/$workerId/sensor")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute()
            } catch (e: Exception) {
                Log.w(TAG, "Server relay failed: ${e.message}")
            }
        }

        // 앱 UI에 전달
        sendBroadcast(Intent("com.dainon.safepulse.companion.WATCH_UPDATE").apply {
            try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val data: Map<String, Any> = gson.fromJson(json, type)
                putExtra("workerId", data["workerId"]?.toString() ?: "")
                putExtra("status", "N")
                putExtra("rssi", 0)
            } catch (_: Exception) {}
        })
    }

    private fun handleAlert(json: String) {
        Log.w(TAG, "🚨 Emergency from watch!")

        // 폰 진동
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 1000), -1))

        // 서버로 중계
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverUrl = getSharedPreferences("safepulse_companion", MODE_PRIVATE)
                    .getString("serverUrl", "http://192.168.0.10:4000") ?: return@launch
                val request = Request.Builder()
                    .url("$serverUrl/api/alerts/emergency")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute()
            } catch (_: Exception) {}
        }

        // 앱 UI
        sendBroadcast(Intent("com.dainon.safepulse.companion.WATCH_UPDATE").apply {
            putExtra("status", "E")
        })
    }

    private fun handleAck(json: String) {
        Log.d(TAG, "✅ ACK from watch")
    }

    private fun handleStatus(json: String) {
        // ★ 서버로 센서 데이터 중계 (워치 WiFi 없을 때 폰이 LTE로 대신 전송)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverUrl = getSharedPreferences("safepulse_companion", MODE_PRIVATE)
                    .getString("serverUrl", "http://192.168.0.10:4000") ?: return@launch

                val type = object : TypeToken<Map<String, Any>>() {}.type
                val data: Map<String, Any> = gson.fromJson(json, type)
                val workerId = data["workerId"] ?: return@launch

                // 워치 status 데이터를 서버 센서 API 형식으로 중계
                val sensorPayload = mapOf(
                    "heartRate" to (data["heartRate"] ?: 0),
                    "spo2" to (data["spo2"] ?: 98),
                    "bodyTemp" to (data["bodyTemp"] ?: 36.5),
                    "stress" to (data["stress"] ?: 0),
                    "status" to (data["state"]?.toString()?.let { state ->
                        when (state) {
                            "EMERGENCY" -> "danger"
                            "FALL_DETECTED", "WAITING_ACK", "MILD_ANOMALY" -> "caution"
                            else -> "normal"
                        }
                    } ?: "normal")
                )

                val request = Request.Builder()
                    .url("$serverUrl/api/workers/$workerId/sensor")
                    .post(gson.toJson(sensorPayload).toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute()
            } catch (e: Exception) {
                Log.w(TAG, "Status relay to server failed: ${e.message}")
            }
        }

        // 앱 UI에 전달
        sendBroadcast(Intent("com.dainon.safepulse.companion.WATCH_STATUS").apply {
            putExtra("data", json)
        })
    }
}
