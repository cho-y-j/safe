package com.dainon.safepulse.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import com.google.gson.Gson

/**
 * Wearable Data Layer API — 워치↔폰 통신
 * 기존 블루투스 연결을 활용 (별도 BLE 광고/스캔 불필요)
 */
object WearableCommService {

    private const val TAG = "WearComm"
    private const val PATH_SENSOR = "/safepulse/sensor"
    private const val PATH_ALERT = "/safepulse/alert"
    private const val PATH_ACK = "/safepulse/ack"
    private const val PATH_STATUS = "/safepulse/status"

    private val gson = Gson()

    /** 센서 데이터를 폰에 전송 */
    fun sendSensorData(context: Context, data: Map<String, Any>) {
        try {
            val json = gson.toJson(data).toByteArray()
            Wearable.getMessageClient(context).sendMessage(
                getConnectedPhoneId(context) ?: return,
                PATH_SENSOR, json
            ).addOnSuccessListener {
                Log.d(TAG, "📱 Sensor data sent to phone")
            }.addOnFailureListener {
                Log.w(TAG, "Phone send failed: ${it.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "WearComm error: ${e.message}")
        }
    }

    /** 긴급 경보를 폰에 전송 */
    fun sendEmergency(context: Context, workerId: String, heartRate: Int, spo2: Int) {
        try {
            val data = mapOf("workerId" to workerId, "heartRate" to heartRate, "spo2" to spo2, "type" to "emergency")
            val json = gson.toJson(data).toByteArray()
            Wearable.getMessageClient(context).sendMessage(
                getConnectedPhoneId(context) ?: return,
                PATH_ALERT, json
            )
            Log.w(TAG, "🚨 Emergency sent to phone: $workerId")
        } catch (e: Exception) {
            Log.e(TAG, "Emergency send failed: ${e.message}")
        }
    }

    /** 확인(ACK)을 폰에 전송 */
    fun sendAck(context: Context, workerId: String) {
        try {
            val data = mapOf("workerId" to workerId, "action" to "ack")
            Wearable.getMessageClient(context).sendMessage(
                getConnectedPhoneId(context) ?: return,
                PATH_ACK, gson.toJson(data).toByteArray()
            )
        } catch (_: Exception) {}
    }

    /** 상태 업데이트를 폰에 전송 */
    fun sendStatus(context: Context, status: Map<String, Any>) {
        try {
            Wearable.getMessageClient(context).sendMessage(
                getConnectedPhoneId(context) ?: return,
                PATH_STATUS, gson.toJson(status).toByteArray()
            )
        } catch (_: Exception) {}
    }

    /** 연결된 폰 노드 ID 가져오기 */
    private fun getConnectedPhoneId(context: Context): String? {
        return try {
            val nodes = com.google.android.gms.tasks.Tasks.await(
                Wearable.getNodeClient(context).connectedNodes
            )
            nodes.firstOrNull()?.id
        } catch (e: Exception) {
            Log.w(TAG, "No connected phone: ${e.message}")
            null
        }
    }
}
