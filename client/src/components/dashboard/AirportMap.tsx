import React from 'react';
import { MapContainer, TileLayer, Marker, Popup, Circle, LayerGroup } from 'react-leaflet';
import { Card, CardContent, Typography, Box, Chip } from '@mui/material';
import L from 'leaflet';
import { useWorkerStore } from '../../stores/workerStore';
import { usePrivacyStore } from '../../stores/privacyStore';

// 인천공항 중심 좌표
const ICN_CENTER: [number, number] = [37.4563, 126.4407];
const ICN_ZOOM = 14;

// 작업자 상태별 마커 아이콘
function workerIcon(status: string) {
  const color = status === 'danger' ? '#E53935' : status === 'caution' ? '#FF9800' : '#43A047';
  return L.divIcon({
    className: '',
    html: `<div style="
      width:28px;height:28px;border-radius:50%;
      background:${color};border:3px solid #fff;
      box-shadow:0 2px 8px rgba(0,0,0,0.4);
      display:flex;align-items:center;justify-content:center;
      font-size:12px;color:#fff;font-weight:700;
      ${status === 'danger' ? 'animation:pulse 1s infinite;' : ''}
    ">👷</div>
    <style>@keyframes pulse{0%,100%{transform:scale(1)}50%{transform:scale(1.3)}}</style>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
}

export default function AirportMap() {
  const workers = useWorkerStore((s) => s.workers);
  const selectWorker = useWorkerStore((s) => s.selectWorker);
  const privacyMode = usePrivacyStore((s) => s.privacyMode);

  // 위험 구역 (위험 상태 작업자가 있는 구역)
  const dangerZones = workers
    .filter((w) => w.status === 'danger')
    .map((w) => ({ lat: w.lat, lng: w.lng, zone: w.zone }));

  const cautionZones = workers
    .filter((w) => w.status === 'caution')
    .map((w) => ({ lat: w.lat, lng: w.lng, zone: w.zone }));

  return (
    <Card sx={{ flex: 1, overflow: 'hidden' }}>
      <CardContent sx={{ p: '0 !important', height: '100%', position: 'relative' }}>
        {/* 지도 위 제목 */}
        <Box
          sx={{
            position: 'absolute', top: 12, left: 12, zIndex: 1000,
            background: 'rgba(10,17,24,0.85)', borderRadius: 2, px: 2, py: 1,
            border: '1px solid #1E3044',
          }}
        >
          <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700 }}>
            🗺 인천공항 실시간 관제 지도
          </Typography>
          <Typography variant="caption" sx={{ color: '#7A8FA3' }}>
            작업자 {workers.length}명 | 위험 {dangerZones.length} | 주의 {cautionZones.length}
          </Typography>
        </Box>

        {/* 층 선택 버튼 */}
        <Box
          sx={{
            position: 'absolute', top: 12, right: 12, zIndex: 1000,
            display: 'flex', flexDirection: 'column', gap: 0.5,
          }}
        >
          {['활주로', '1F', '2F', '3F', 'B1'].map((floor) => (
            <Chip
              key={floor}
              label={floor}
              size="small"
              sx={{
                background: 'rgba(10,17,24,0.85)',
                color: '#8CB4D8',
                border: '1px solid #1E3044',
                fontSize: 10,
                cursor: 'pointer',
                '&:hover': { background: 'rgba(46,117,182,0.3)' },
              }}
            />
          ))}
        </Box>

        <MapContainer
          center={ICN_CENTER}
          zoom={ICN_ZOOM}
          style={{ height: '100%', width: '100%', background: '#0A1118' }}
          zoomControl={false}
        >
          <TileLayer
            url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
            attribution="&copy; CartoDB"
          />

          {/* 위험 구역 오버레이 */}
          <LayerGroup>
            {dangerZones.map((z, i) => (
              <Circle
                key={`danger-${i}`}
                center={[z.lat, z.lng]}
                radius={150}
                pathOptions={{ color: '#E53935', fillColor: '#E53935', fillOpacity: 0.15, weight: 2 }}
              />
            ))}
            {cautionZones.map((z, i) => (
              <Circle
                key={`caution-${i}`}
                center={[z.lat, z.lng]}
                radius={100}
                pathOptions={{ color: '#FF9800', fillColor: '#FF9800', fillOpacity: 0.1, weight: 1 }}
              />
            ))}
          </LayerGroup>

          {/* 작업자 마커 */}
          {workers.map((worker) => (
            <Marker
              key={worker.id}
              position={[worker.lat, worker.lng]}
              icon={workerIcon(worker.status)}
              eventHandlers={{ click: () => selectWorker(worker.id) }}
            >
              <Popup>
                <Box sx={{ minWidth: 200, fontFamily: 'Noto Sans KR' }}>
                  <Typography sx={{ fontWeight: 700, fontSize: 14 }}>
                    {privacyMode ? `작업자 ${worker.id}` : `${worker.name} (${worker.id})`}
                  </Typography>
                  <Typography sx={{ fontSize: 12, color: '#666' }}>{worker.role} | {worker.location}</Typography>
                  {privacyMode ? (
                    <Box sx={{ mt: 1 }}>
                      <span style={{ fontWeight: 600, color: worker.status === 'danger' ? '#E53935' : worker.status === 'caution' ? '#FF9800' : '#43A047' }}>
                        🛡 AI 위험도: {worker.status === 'normal' ? '낮음' : worker.status === 'caution' ? '주의' : '위험'}
                      </span><br/>
                      <span>🔋 피로: {worker.fatigue > 70 ? '높음' : worker.fatigue > 40 ? '보통' : '낮음'}</span>
                    </Box>
                  ) : (
                    <Box sx={{ mt: 1, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4px' }}>
                      <span>💓 {worker.heartRate} bpm</span>
                      <span>🌡 {worker.bodyTemp}°C</span>
                      <span>🫁 {worker.spo2}%</span>
                      <span>😰 스트레스 {worker.stress}</span>
                    </Box>
                  )}
                  {worker.alerts.length > 0 && (
                    <Box sx={{ mt: 1, p: 1, background: '#FFF3E0', borderRadius: 1, fontSize: 11 }}>
                      {worker.alerts[0]}
                    </Box>
                  )}
                </Box>
              </Popup>
            </Marker>
          ))}
        </MapContainer>
      </CardContent>
    </Card>
  );
}
