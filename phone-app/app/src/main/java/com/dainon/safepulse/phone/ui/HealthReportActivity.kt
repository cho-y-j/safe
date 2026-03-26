package com.dainon.safepulse.phone.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dainon.safepulse.R
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request

class HealthReportActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = OkHttpClient()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health)

        val prefs = getSharedPreferences("safepulse_companion", MODE_PRIVATE)
        val serverUrl = prefs.getString("serverUrl", "http://192.168.0.10:4000") ?: ""
        val workerId = "W-001" // TODO: 실제 워치에서 가져오기

        findViewById<TextView>(R.id.tvWorkerId).text = "작업자: $workerId"

        loadBaseline(serverUrl, workerId)
    }

    private fun loadBaseline(serverUrl: String, workerId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverUrl/api/baseline/restore/$workerId")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@launch
                val data = gson.fromJson(body, Map::class.java) as Map<String, Any>

                withContext(Dispatchers.Main) {
                    if (data["found"] == true) {
                        val restHr = (data["restHrMean"] as? Number)?.toInt() ?: 0
                        val restStd = (data["restHrStd"] as? Number)?.toInt() ?: 8
                        val activeHr = (data["activeHrMean"] as? Number)?.toInt() ?: 0
                        val activeStd = (data["activeHrStd"] as? Number)?.toInt() ?: 12
                        val days = (data["totalDays"] as? Number)?.toInt() ?: 0
                        val records = (data["totalRecords"] as? Number)?.toInt() ?: 0

                        findViewById<TextView>(R.id.tvRestHr).text = "$restHr bpm"
                        findViewById<TextView>(R.id.tvRestRange).text = "범위: ${restHr - restStd * 2}~${restHr + restStd * 2}"
                        findViewById<TextView>(R.id.tvActiveHr).text = "$activeHr bpm"
                        findViewById<TextView>(R.id.tvActiveRange).text = "범위: ${activeHr - activeStd * 2}~${activeHr + activeStd * 2}"

                        findViewById<TextView>(R.id.tvLearningDays).text = "총 학습 기간: ${days}일"
                        findViewById<TextView>(R.id.tvLearningRecords).text = "기록 수: ${records}건"

                        val sensitivity = when {
                            records < 15 -> "관대 (학습 초기, 4σ)"
                            records < 75 -> "보통 (학습 중, 3σ)"
                            else -> "정밀 (충분 학습, 2.5σ)"
                        }
                        findViewById<TextView>(R.id.tvSensitivity).text = "현재 민감도: $sensitivity"

                        // 이상 알림 기준
                        val restThreshold = restHr + restStd * 3
                        val activeThreshold = activeHr + activeStd * 3
                        findViewById<TextView>(R.id.tvAlertThreshold).text =
                            "안정 시 이상: ${restThreshold} bpm 초과\n" +
                            "활동 시 이상: ${activeThreshold} bpm 초과\n" +
                            "절대 상한: 안정 130 / 활동 150 bpm\n" +
                            "SpO₂: 93% 이하\n" +
                            "낙상: 자유낙하+충격 패턴 감지"

                        // 시간대별
                        val bySlot = data["byTimeSlot"] as? Map<String, Any>
                        if (bySlot != null) {
                            updateSlot("morning", "🌅 오전 (06~12)", bySlot, R.id.tvMorning)
                            updateSlot("afternoon", "☀ 오후 (12~18)", bySlot, R.id.tvAfternoon)
                            updateSlot("evening", "🌆 저녁 (18~22)", bySlot, R.id.tvEvening)
                            updateSlot("night", "🌙 야간 (22~06)", bySlot, R.id.tvNight)
                        }
                    } else {
                        findViewById<TextView>(R.id.tvRestHr).text = "학습 전"
                        findViewById<TextView>(R.id.tvActiveHr).text = "학습 전"
                        findViewById<TextView>(R.id.tvLearningDays).text = "아직 학습 데이터가 없습니다"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.tvLearningDays).text = "서버 연결 실패: ${e.message}"
                }
            }
        }
    }

    private fun updateSlot(key: String, label: String, slots: Map<String, Any>, viewId: Int) {
        val slot = slots[key] as? Map<String, Any>
        if (slot != null) {
            val rest = (slot["restHrMean"] as? Number)?.toInt() ?: 0
            val active = (slot["activeHrMean"] as? Number)?.toInt() ?: 0
            val samples = (slot["samples"] as? Number)?.toInt() ?: 0
            findViewById<TextView>(viewId).text = "$label: 안정 ${rest} / 활동 ${active} (${samples}건)"
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
