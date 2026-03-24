package com.dainon.safepulse.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import com.dainon.safepulse.data.AlertMessage
import com.dainon.safepulse.data.ServerClient
import kotlinx.coroutines.*

/**
 * 관제센터 알림 수신 서비스
 *
 * 10초마다 서버에서 알림을 폴링하고,
 * 새 알림이 있으면 진동 + 브로드캐스트합니다.
 */
class AlertService : Service() {

    companion object {
        const val TAG = "AlertService"
        const val POLL_INTERVAL_MS = 10000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastAlertId: String? = null
    private val alertHistory = mutableListOf<AlertMessage>()

    override fun onCreate() {
        super.onCreate()
        startPolling()
    }

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                try {
                    val alerts = ServerClient.getAlerts(20)
                    if (alerts.isNotEmpty()) {
                        val newAlerts = if (lastAlertId != null) {
                            alerts.takeWhile { it.id != lastAlertId }
                        } else {
                            alerts.take(3) // 첫 로드 시 최근 3개만
                        }

                        if (newAlerts.isNotEmpty()) {
                            lastAlertId = alerts.first().id
                            alertHistory.addAll(0, newAlerts)
                            if (alertHistory.size > 50) {
                                alertHistory.subList(50, alertHistory.size).clear()
                            }

                            // 새 알림 진동
                            for (alert in newAlerts) {
                                vibrateByLevel(alert.level)
                                broadcastAlert(alert)
                                Log.d(TAG, "New alert: [${alert.level}] ${alert.message}")
                            }
                        }

                        // 전체 알림 내역 브로드캐스트
                        sendBroadcast(Intent("com.dainon.safepulse.ALERT_LIST_UPDATE").apply {
                            putExtra("count", alertHistory.size)
                        })
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** 위험도별 진동 패턴 */
    private fun vibrateByLevel(level: String) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = when (level) {
            "danger" -> longArrayOf(0, 300, 100, 300, 100, 500) // 강한 연속 진동
            "warning" -> longArrayOf(0, 200, 100, 200)           // 중간 진동
            "caution" -> longArrayOf(0, 150)                      // 짧은 진동
            else -> longArrayOf(0, 100)                           // 약한 진동
        }

        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun broadcastAlert(alert: AlertMessage) {
        sendBroadcast(Intent("com.dainon.safepulse.NEW_ALERT").apply {
            putExtra("level", alert.level)
            putExtra("message", alert.message)
            putExtra("timestamp", alert.timestamp)
        })
    }

    fun getAlertHistory(): List<AlertMessage> = alertHistory.toList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
