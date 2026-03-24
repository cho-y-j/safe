package com.dainon.safepulse.data

import com.dainon.safepulse.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * SafePulse 서버와 HTTP 통신
 */
object ServerClient {

    private val gson = Gson()
    private val JSON_TYPE = "application/json".toMediaType()
    private val baseUrl = BuildConfig.SERVER_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 센서 데이터 전송 */
    suspend fun sendSensorData(data: SensorPayload): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(data)
            val request = Request.Builder()
                .url("$baseUrl/api/workers/${data.workerId}/sensor")
                .post(json.toRequestBody(JSON_TYPE))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 최근 알림 가져오기 */
    suspend fun getAlerts(limit: Int = 20): List<AlertMessage> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/alerts?limit=$limit")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<AlertMessage>>() {}.type
                gson.fromJson(body, type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** AI 인사이트 가져오기 */
    suspend fun getAIInsight(): RiskStatus? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/public-data/ai-insight")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                gson.fromJson(body, RiskStatus::class.java)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 긴급 알림 전송 (경로 2: 서버 경유) */
    suspend fun sendEmergencyAlert(
        workerId: String, heartRate: Int, spo2: Int, bodyTemp: Double
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf(
                "workerId" to workerId,
                "type" to "emergency",
                "level" to "danger",
                "heartRate" to heartRate,
                "spo2" to spo2,
                "bodyTemp" to bodyTemp,
                "message" to "🚨 작업자 $workerId 응급 상황 — 심박 ${heartRate}bpm, SpO₂ ${spo2}%, P2P 경보 발동됨",
                "timestamp" to System.currentTimeMillis()
            ))
            val request = Request.Builder()
                .url("$baseUrl/api/alerts/emergency")
                .post(json.toRequestBody(JSON_TYPE))
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 서버 헬스체크 */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/health")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
