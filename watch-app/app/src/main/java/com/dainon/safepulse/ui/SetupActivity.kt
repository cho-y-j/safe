package com.dainon.safepulse.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.dainon.safepulse.R
import com.dainon.safepulse.service.SensorService

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
        if (prefs.getBoolean("setupDone", false) && !intent.getBooleanExtra("forceSetup", false)) {
            SensorService.WORKER_ID = prefs.getString("workerId", "W-001") ?: "W-001"
            // 캘리브레이션 완료 여부 확인
            if (!prefs.getBoolean("calibrationDone", false)) {
                startActivity(Intent(this, CalibrationActivity::class.java))
                finish()
                return
            }
            goToMain()
            return
        }

        setContentView(R.layout.activity_setup)

        val etWorkerId = findViewById<EditText>(R.id.etWorkerId)
        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)
        val rgWorkType = findViewById<RadioGroup>(R.id.rgWorkType)
        val btnNewUser = findViewById<Button>(R.id.btnNewUser)
        val btnKeepData = findViewById<Button>(R.id.btnKeepData)

        etWorkerId.setText(prefs.getString("workerId", "W-001"))
        etServerUrl.setText(prefs.getString("serverUrl", "http://192.168.0.10:4000"))

        // 새 착용자
        btnNewUser.setOnClickListener {
            val workType = when (rgWorkType.checkedRadioButtonId) {
                R.id.rbOffice -> "office"
                R.id.rbLight -> "light"
                R.id.rbHeavy -> "heavy"
                R.id.rbOutdoor -> "outdoor"
                else -> "light"
            }
            saveSettings(etWorkerId.text.toString(), etServerUrl.text.toString(), workType)

            // 작업 유형별 초기 베이스라인 프리셋
            val (restMean, restStd, activeMean, activeStd) = when (workType) {
                "office"  -> arrayOf(70.0, 10.0, 85.0, 12.0)   // 사무직
                "light"   -> arrayOf(75.0, 12.0, 95.0, 15.0)   // 경량
                "heavy"   -> arrayOf(78.0, 12.0, 105.0, 18.0)  // 중량
                "outdoor" -> arrayOf(80.0, 15.0, 115.0, 20.0)  // 야외 고강도
                else      -> arrayOf(75.0, 12.0, 95.0, 15.0)
            }

            // 경보 범위 저장
            val alertRange = when (workType) {
                "office"  -> 40
                "light"   -> 55
                "heavy"   -> 65
                "outdoor" -> 80
                else -> 55
            }

            prefs.edit()
                .putBoolean("baselineComplete", false)
                .putBoolean("calibrationDone", false)
                .putInt("alertRangeUpper", alertRange)
                .putFloat("presetRestMean", restMean.toFloat())
                .putFloat("presetActiveMean", activeMean.toFloat())
                .putString("workType", workType)
                .apply()

            // 캘리브레이션 화면으로 이동
            startActivity(Intent(this, CalibrationActivity::class.java))
            finish()
        }

        // 기존 유지
        btnKeepData.setOnClickListener {
            saveSettings(etWorkerId.text.toString(), etServerUrl.text.toString(),
                prefs.getString("workType", "light") ?: "light")
            goToMain()
        }
    }

    private fun saveSettings(workerId: String, serverUrl: String, workType: String) {
        val id = workerId.ifBlank { "W-001" }
        val url = serverUrl.ifBlank { "http://192.168.0.10:4000" }

        getSharedPreferences("safepulse", MODE_PRIVATE).edit()
            .putString("workerId", id)
            .putString("serverUrl", url)
            .putString("workType", workType)
            .putBoolean("setupDone", true)
            .apply()

        SensorService.WORKER_ID = id
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
