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
    var baseUrl = BuildConfig.SERVER_URL

    fun updateUrl(url: String) { if (url.isNotBlank()) baseUrl = url }

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

    /** 베이스라인 서버 동기화 */
    suspend fun syncBaseline(data: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(data)
            val request = Request.Builder()
                .url("$baseUrl/api/baseline/sync")
                .post(json.toRequestBody(JSON_TYPE))
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /** 베이스라인 서버에서 복원 */
    suspend fun restoreBaseline(workerId: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/baseline/restore/$workerId")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                gson.fromJson(body, Map::class.java) as Map<String, Any>
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /** 작업자 레지스트리 로드 (P2P 이름 표시용) → "W-001:조영진,W-002:이서준" 형태 */
    suspend fun getWorkerRegistry(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/workers")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext ""
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val workers: List<Map<String, Any>> = gson.fromJson(body, type)
                workers.joinToString(",") { "${it["id"]}:${it["name"]}" }
            } else ""
        } catch (e: Exception) {
            ""
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
