package com.dainon.safepulse.phone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 폰 부팅 시 BridgeService 자동 시작
 * 앱을 한번이라도 실행한 적 있으면 이후 부팅마다 자동 작동
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Boot completed → starting BridgeService")
            try {
                context.startForegroundService(Intent(context, BridgeService::class.java))
            } catch (e: Exception) {
                Log.e("BootReceiver", "BridgeService start failed: ${e.message}")
            }
        }
    }
}
