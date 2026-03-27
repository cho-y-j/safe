package com.dainon.safepulse.phone.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dainon.safepulse.R
import kotlinx.coroutines.*

/**
 * 수신자 폰 — 긴급 작업자 위치를 지도에 표시
 * WebView + OpenStreetMap(Leaflet) 사용 (API 키 불필요)
 */
class EmergencyMapActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var startTime = System.currentTimeMillis()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_map)

        val workerId = intent.getStringExtra("workerId") ?: "?"
        val workerName = intent.getStringExtra("workerName") ?: workerId
        val lat = intent.getDoubleExtra("lat", 37.4602)
        val lng = intent.getDoubleExtra("lng", 126.4407)
        val zone = intent.getStringExtra("zone") ?: ""
        val distance = intent.getDoubleExtra("distance", 0.0)

        val tvWorkerName = findViewById<TextView>(R.id.tvMapWorkerName)
        val tvDistance = findViewById<TextView>(R.id.tvMapDistance)
        val tvZone = findViewById<TextView>(R.id.tvMapZone)
        val tvElapsed = findViewById<TextView>(R.id.tvMapElapsed)
        val mapWebView = findViewById<WebView>(R.id.mapWebView)
        val btnNavigate = findViewById<Button>(R.id.btnNavigate)
        val btnBack = findViewById<Button>(R.id.btnMapBack)

        tvWorkerName.text = workerName
        tvDistance.text = if (distance > 0) "~${"%.0f".format(distance)}m" else ""
        tvZone.text = zone

        // WebView 설정 + Leaflet 지도
        mapWebView.settings.javaScriptEnabled = true
        mapWebView.settings.domStorageEnabled = true
        mapWebView.loadDataWithBaseURL(null, buildMapHtml(lat, lng, workerName), "text/html", "UTF-8", null)

        // Google Maps 길 안내
        btnNavigate.setOnClickListener {
            val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=w")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                // Google Maps 없으면 브라우저로
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking")))
            }
        }

        btnBack.setOnClickListener { finish() }

        // 경과 시간 타이머
        scope.launch {
            while (isActive) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                tvElapsed.text = "${elapsed}초 전"
            }
        }
    }

    private fun buildMapHtml(lat: Double, lng: Double, name: String): String {
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
body { margin: 0; padding: 0; }
#map { width: 100%; height: 100vh; }
.emergency-icon {
    background: #E53935;
    border: 3px solid white;
    border-radius: 50%;
    width: 20px;
    height: 20px;
    box-shadow: 0 0 15px rgba(229,57,53,0.8);
    animation: pulse 1s infinite;
}
@keyframes pulse {
    0%, 100% { box-shadow: 0 0 15px rgba(229,57,53,0.8); }
    50% { box-shadow: 0 0 30px rgba(229,57,53,1); }
}
</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map').setView([$lat, $lng], 17);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: 'OSM',
    maxZoom: 19
}).addTo(map);

var emergencyIcon = L.divIcon({
    className: 'emergency-icon',
    iconSize: [20, 20],
    iconAnchor: [10, 10]
});

L.marker([$lat, $lng], {icon: emergencyIcon}).addTo(map)
    .bindPopup('<b style="color:#E53935">🚨 ${name.replace("'", "\\'")}</b><br>긴급 상황').openPopup();

L.circle([$lat, $lng], {
    radius: 30,
    color: '#E53935',
    fillColor: '#E53935',
    fillOpacity: 0.1,
    dashArray: '5,5'
}).addTo(map);
</script>
</body>
</html>
""".trimIndent()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
