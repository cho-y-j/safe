package com.dainon.safepulse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 워치 부팅 시 SensorService 자동 시작
 * 작업 중이었으면(workMode==WORKING) 서비스 자동 복원
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs = context.getSharedPreferences("safepulse", Context.MODE_PRIVATE)
            val workMode = prefs.getString("workMode", "IDLE")

            if (workMode == "WORKING" || workMode == "RESTING") {
                Log.d("BootReceiver", "Boot completed, workMode=$workMode → starting SensorService")
                try {
                    context.startForegroundService(Intent(context, SensorService::class.java))
                    context.startService(Intent(context, AlertService::class.java))
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Service start failed: ${e.message}")
                }
            } else {
                Log.d("BootReceiver", "Boot completed, workMode=$workMode → not starting (IDLE)")
            }
        }
    }
}
