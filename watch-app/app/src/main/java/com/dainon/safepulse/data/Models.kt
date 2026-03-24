package com.dainon.safepulse.data

/** 서버에 전송할 센서 데이터 */
data class SensorPayload(
    val workerId: String,
    val heartRate: Int,
    val spo2: Int,
    val bodyTemp: Double,
    val stress: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/** 서버에서 수신하는 알림 */
data class AlertMessage(
    val id: String,
    val level: String,       // info, caution, warning, danger
    val message: String,
    val workerId: String?,
    val zone: String?,
    val timestamp: String
)

/** 서버에서 수신하는 AI 위험도 */
data class RiskStatus(
    val totalScore: Int,
    val level: String,        // 안전, 주의, 경고, 위험
    val summary: String,
    val recommendations: List<String>
)

/** 워치에 표시할 개인 상태 */
data class MyStatus(
    val riskLevel: String,    // 안전, 주의, 경고, 위험
    val fatigueLevel: String, // 낮음, 보통, 높음
    val recommendation: String,
    val heartRate: Int,
    val spo2: Int,
    val workMinutes: Int
)
