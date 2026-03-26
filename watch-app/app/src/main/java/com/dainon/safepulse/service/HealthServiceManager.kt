package com.dainon.safepulse.service

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.*

/**
 * Health Services API — Samsung Health 데이터 읽기
 * 심박수 (HR) + SpO₂ (지원 시)
 */
class HealthServiceManager(private val context: Context) {

    companion object {
        const val TAG = "HealthSvc"
    }

    private var passiveClient: PassiveMonitoringClient? = null
    var onHeartRate: ((Int) -> Unit)? = null
    var onSpO2: ((Int) -> Unit)? = null

    private val callback = object : PassiveListenerCallback {
        override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
            // 심박수
            try {
                dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { dp ->
                    val hr = dp.value.toInt()
                    if (hr > 0) onHeartRate?.invoke(hr)
                }
            } catch (e: Exception) {
                Log.w(TAG, "HR read error: ${e.message}")
            }
        }
    }

    fun start() {
        try {
            val healthClient = HealthServices.getClient(context)
            passiveClient = healthClient.passiveMonitoringClient

            val passiveConfig = PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .build()

            passiveClient?.setPassiveListenerCallback(passiveConfig, callback)
            Log.d(TAG, "✅ Health Services passive monitoring started (HR)")
        } catch (e: Exception) {
            Log.e(TAG, "Health Services failed: ${e.message}")
        }
    }

    fun stop() {
        try {
            passiveClient?.clearPassiveListenerCallbackAsync()
        } catch (_: Exception) {}
    }
}
