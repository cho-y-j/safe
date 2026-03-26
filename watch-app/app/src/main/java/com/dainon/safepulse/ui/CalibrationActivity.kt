package com.dainon.safepulse.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.dainon.safepulse.R
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

class CalibrationActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        const val TAG = "Calibration"
        private const val ABSOLUTE_MIN = 40
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var sensorManager: SensorManager
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var readyView: LinearLayout
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

    private val restSamples = mutableListOf<Int>()
    private val activeSamples = mutableListOf<Int>()
    private var currentHR = 0
    private var activitySum = 0f
    private var activityCount = 0

    private val CALIBRATION_MS = 10 * 60 * 1000L
    private val ACTIVITY_THRESHOLD = 1.5f
    private var startTime = 0L
    private var isCalibrating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 화면 켜짐 유지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_calibration)

        readyView = findViewById(R.id.readyView)
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

        // "학습 시작" 버튼
        findViewById<Button>(R.id.btnStartCalibration).setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION), 100)
            } else {
                beginCalibration()
            }
        }

        // "작업 시작" 버튼
        findViewById<Button>(R.id.btnStartWork).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // 대기 화면 표시
        readyView.visibility = View.VISIBLE
        calibratingView.visibility = View.GONE
        completeView.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) beginCalibration()
    }

    private fun beginCalibration() {
        Log.d(TAG, "▶ Calibration started")
        isCalibrating = true

        readyView.visibility = View.GONE
        calibratingView.visibility = View.VISIBLE
        completeView.visibility = View.GONE

        // WakeLock으로 화면 꺼져도 센서 유지
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "safepulse:calibration")
        wakeLock?.acquire(15 * 60 * 1000L) // 15분

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
            sensorManager.registerListener(this, it, 3000000)
            Log.d(TAG, "HR sensor registered")
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        startTime = System.currentTimeMillis()
        startTimer()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isCalibrating) return
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values[0].toInt()
                if (hr > 30) {
                    currentHR = hr
                    val actLevel = if (activityCount > 0) activitySum / activityCount else 0f
                    activitySum = 0f
                    activityCount = 0
                    if (actLevel > ACTIVITY_THRESHOLD) activeSamples.add(hr) else restSamples.add(hr)
                    Log.d(TAG, "💓 HR=$hr, rest=${restSamples.size}, active=${activeSamples.size}, actLevel=${"%.1f".format(actLevel)}")
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
            while (isActive && isCalibrating) {
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
                tvRemaining.text = "${min}:${String.format("%02d", sec)}"
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
        isCalibrating = false
        sensorManager.unregisterListener(this)
        wakeLock?.release()

        val restMean = if (restSamples.size >= 3) restSamples.average().toInt() else 75
        val activeMean = if (activeSamples.size >= 3) activeSamples.average().toInt() else 95

        val prefs = getSharedPreferences("safepulse", MODE_PRIVATE)
        val alertRange = prefs.getInt("alertRangeUpper", 55)
        val upperAlert = restMean + alertRange
        val lowerAlert = (restMean - 30).coerceAtLeast(ABSOLUTE_MIN)

        Log.d(TAG, "✅ Complete! rest=$restMean(${restSamples.size}건), active=$activeMean(${activeSamples.size}건), alert=${upperAlert}↑/${lowerAlert}↓")

        // 저장
        prefs.edit()
            .putInt("baselineHR", restMean)
            .putFloat("presetRestMean", restMean.toFloat())
            .putFloat("presetActiveMean", activeMean.toFloat())
            .putBoolean("baselineComplete", true)
            .putBoolean("calibrationDone", true)
            .apply()

        // 완료 화면
        readyView.visibility = View.GONE
        calibratingView.visibility = View.GONE
        completeView.visibility = View.VISIBLE

        tvFinalRest.text = "$restMean bpm"
        tvFinalActive.text = "$activeMean bpm"
        tvAlertRange.text = "경보: ${upperAlert}↑ / ${lowerAlert}↓"
    }

    override fun onDestroy() {
        try { sensorManager.unregisterListener(this) } catch (_: Exception) {}
        try { wakeLock?.release() } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

}
