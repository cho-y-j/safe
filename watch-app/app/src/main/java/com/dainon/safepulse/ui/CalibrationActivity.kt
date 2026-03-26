package com.dainon.safepulse.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.dainon.safepulse.R
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 캘리브레이션 — 10분간 평소처럼 작업하며 개인 베이스라인 측정
 */
class CalibrationActivity : AppCompatActivity(), SensorEventListener {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var sensorManager: SensorManager

    // UI
    private lateinit var calibratingView: LinearLayout
    private lateinit var completeView: LinearLayout
    private lateinit var pbCalibration: ProgressBar
    private lateinit var tvRemaining: TextView
    private lateinit var tvCurrentHR: TextView
    private lateinit var tvRestMean: TextView
    private lateinit var tvRestCount: TextView
    private lateinit var tvActiveMean: TextView
    private lateinit var tvActiveCount: TextView
    private lateinit var tvFinalRest: TextView
    private lateinit var tvFinalActive: TextView
    private lateinit var tvAlertRange: TextView

    // 데이터 수집
    private val restSamples = mutableListOf<Int>()
    private val activeSamples = mutableListOf<Int>()
    private var currentHR = 0
    private var activityLevel = 0f
    private var activitySum = 0f
    private var activityCount = 0

    private val CALIBRATION_MS = 10 * 60 * 1000L  // 10분
    private val ACTIVITY_THRESHOLD = 1.5f
    private var startTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        calibratingView = findViewById(R.id.calibratingView)
        completeView = findViewById(R.id.completeView)
        pbCalibration = findViewById(R.id.pbCalibration)
        tvRemaining = findViewById(R.id.tvRemaining)
        tvCurrentHR = findViewById(R.id.tvCurrentHR)
        tvRestMean = findViewById(R.id.tvRestMean)
        tvRestCount = findViewById(R.id.tvRestCount)
        tvActiveMean = findViewById(R.id.tvActiveMean)
        tvActiveCount = findViewById(R.id.tvActiveCount)
        tvFinalRest = findViewById(R.id.tvFinalRest)
        tvFinalActive = findViewById(R.id.tvFinalActive)
        tvAlertRange = findViewById(R.id.tvAlertRange)

        findViewById<Button>(R.id.btnStartWork).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // 권한 확인
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION), 100)
        } else {
            startCalibration()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startCalibration()
    }

    private fun startCalibration() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // 심박 센서
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
            sensorManager.registerListener(this, it, 3000000)
        }
        // 가속도계
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        startTime = System.currentTimeMillis()
        startTimer()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values[0].toInt()
                if (hr > 30) {
                    currentHR = hr

                    // 활동량 계산
                    activityLevel = if (activityCount > 0) activitySum / activityCount else 0f
                    activitySum = 0f
                    activityCount = 0

                    // 활동/안정 분류하여 저장
                    if (activityLevel > ACTIVITY_THRESHOLD) {
                        activeSamples.add(hr)
                    } else {
                        restSamples.add(hr)
                    }
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val mag = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
                activitySum += abs(mag - 9.81f)
                activityCount++
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startTimer() {
        scope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = CALIBRATION_MS - elapsed
                val progress = ((elapsed.toFloat() / CALIBRATION_MS) * 100).toInt().coerceIn(0, 100)

                if (remaining <= 0) {
                    onCalibrationComplete()
                    break
                }

                val min = (remaining / 60000).toInt()
                val sec = ((remaining % 60000) / 1000).toInt()

                pbCalibration.progress = progress
                tvRemaining.text = "남은 시간: ${min}:${String.format("%02d", sec)}"
                tvCurrentHR.text = "💓 ${if (currentHR > 0) currentHR else "--"}"

                if (restSamples.isNotEmpty()) {
                    tvRestMean.text = "${restSamples.average().toInt()}"
                    tvRestCount.text = "안정 ${restSamples.size}건"
                }
                if (activeSamples.isNotEmpty()) {
                    tvActiveMean.text = "${activeSamples.average().toInt()}"
                    tvActiveCount.text = "활동 ${activeSamples.size}건"
                }

                delay(1000)
            }
        }
    }

    private fun onCalibrationComplete() {
        sensorManager.unregisterListener(this)

        val restMean = if (restSamples.size >= 5) restSamples.average().toInt() else 75
        val activeMean = if (activeSamples.size >= 5) activeSamples.average().toInt() else 95

        val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
        val alertRange = prefs.getInt("alertRangeUpper", 55)

        val upperAlert = restMean + alertRange
        val lowerAlert = restMean - 30

        // 결과 저장
        prefs.edit()
            .putInt("baselineHR", restMean)
            .putFloat("presetRestMean", restMean.toFloat())
            .putFloat("presetActiveMean", activeMean.toFloat())
            .putBoolean("baselineComplete", true)
            .putBoolean("calibrationDone", true)
            .apply()

        // 완료 화면 표시
        calibratingView.visibility = View.GONE
        completeView.visibility = View.VISIBLE

        tvFinalRest.text = "$restMean bpm"
        tvFinalActive.text = "$activeMean bpm"
        tvAlertRange.text = "경보: ${upperAlert}↑ / ${lowerAlert}↓"
    }

    override fun onDestroy() {
        try { sensorManager.unregisterListener(this) } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }
}
