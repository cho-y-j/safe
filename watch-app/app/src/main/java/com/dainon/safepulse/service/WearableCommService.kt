package com.dainon.safepulse.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import com.google.gson.Gson

/**
 * Wearable Data Layer API — 워치↔폰 통신
 * Tasks.await() 대신 완전 비동기 처리
 */
object WearableCommService {

    private const val TAG = "WearComm"
    private const val PATH_STATUS = "/safepulse/status"
    private const val PATH_ALERT = "/safepulse/alert"
    private const val PATH_ACK = "/safepulse/ack"

    private val gson = Gson()

    /** 상태 업데이트를 폰에 전송 */
    fun sendStatus(context: Context, status: Map<String, Any>) {
        sendToPhone(context, PATH_STATUS, gson.toJson(status).toByteArray())
    }

    /** 긴급 경보를 폰에 전송 */
    fun sendEmergency(context: Context, workerId: String, heartRate: Int, spo2: Int) {
        val data = mapOf("workerId" to workerId, "heartRate" to heartRate, "spo2" to spo2, "type" to "emergency")
        sendToPhone(context, PATH_ALERT, gson.toJson(data).toByteArray())
    }

    /** 확인(ACK)을 폰에 전송 */
    fun sendAck(context: Context, workerId: String) {
        sendToPhone(context, PATH_ACK, gson.toJson(mapOf("workerId" to workerId, "action" to "ack")).toByteArray())
    }

    /** 비동기로 폰에 메시지 전송 (Tasks.await 사용 안 함) */
    private fun sendToPhone(context: Context, path: String, data: ByteArray) {
        try {
            Wearable.getNodeClient(context).connectedNodes
                .addOnSuccessListener { nodes ->
                    val phoneNode = nodes.firstOrNull()
                    if (phoneNode != null) {
                        Wearable.getMessageClient(context).sendMessage(phoneNode.id, path, data)
                            .addOnSuccessListener {
                                Log.d(TAG, "📱 Sent to phone: $path (${data.size}bytes)")
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Send failed: $path → ${e.message}")
                            }
                    } else {
                        Log.w(TAG, "No phone connected")
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "getNodes failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "WearComm error: ${e.message}")
        }
    }
}
