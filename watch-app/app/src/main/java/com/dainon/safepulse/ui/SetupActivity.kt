package com.dainon.safepulse.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.dainon.safepulse.R
import com.dainon.safepulse.service.SensorService

/**
 * 작업자 설정 화면
 * - 첫 실행 또는 착용자 변경 시 표시
 * - Worker ID, 서버 주소 설정
 * - 새 착용자: 베이스라인 초기화
 * - 기존 유지: 이전 학습 데이터 유지
 */
class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 이미 설정 완료면 바로 메인으로 (설정 변경은 메인에서 길게 누르기)
        val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
        if (prefs.getBoolean("setupDone", false) && !intent.getBooleanExtra("forceSetup", false)) {
            SensorService.WORKER_ID = prefs.getString("workerId", "W-001") ?: "W-001"
            goToMain()
            return
        }

        setContentView(R.layout.activity_setup)

        val etWorkerId = findViewById<EditText>(R.id.etWorkerId)
        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)
        val btnNewUser = findViewById<Button>(R.id.btnNewUser)
        val btnKeepData = findViewById<Button>(R.id.btnKeepData)

        // 이전 설정 불러오기
        etWorkerId.setText(prefs.getString("workerId", "W-001"))
        etServerUrl.setText(prefs.getString("serverUrl", "http://192.168.0.10:4000"))

        // 새 착용자 — 베이스라인 초기화
        btnNewUser.setOnClickListener {
            saveSettings(etWorkerId.text.toString(), etServerUrl.text.toString())
            // 베이스라인 초기화
            prefs.edit()
                .putBoolean("baselineComplete", false)
                .putLong("learningStart", System.currentTimeMillis())
                .putInt("baselineHR", 0)
                .remove("restHrMean").remove("restHrStd")
                .remove("activeHrMean").remove("activeHrStd")
                .apply()

            goToMain()
        }

        // 기존 유지
        btnKeepData.setOnClickListener {
            saveSettings(etWorkerId.text.toString(), etServerUrl.text.toString())
            goToMain()
        }
    }

    private fun saveSettings(workerId: String, serverUrl: String) {
        val id = workerId.ifBlank { "W-001" }
        val url = serverUrl.ifBlank { "http://192.168.0.10:4000" }

        getSharedPreferences("safepulse", MODE_PRIVATE).edit()
            .putString("workerId", id)
            .putString("serverUrl", url)
            .putBoolean("setupDone", true)
            .apply()

        // 서비스에 반영
        SensorService.WORKER_ID = id
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
