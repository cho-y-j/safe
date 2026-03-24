import { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Circle, LayerGroup } from 'react-leaflet';
import { Card, CardContent, Typography, Box, Chip, Switch } from '@mui/material';
import L from 'leaflet';
import { useWorkerStore } from '../../stores/workerStore';
import { usePrivacyStore } from '../../stores/privacyStore';

const ICN_CENTER: [number, number] = [37.4563, 126.4407];
const ICN_ZOOM = 14;

// AED 위치 (터미널 구역별 대략 좌표)
const AED_COORDS: Record<string, [number, number]> = {
  'T1': [37.4490, 126.4515],
  'T2': [37.4608, 126.4260],
  '탑승동': [37.4600, 126.4430],
};

function workerIcon(status: string) {
  const color = status === 'danger' ? '#E53935' : status === 'caution' ? '#FF9800' : '#43A047';
  return L.divIcon({
    className: '',
    html: `<div style="
      width:26px;height:26px;border-radius:50%;
      background:${color};border:2px solid #fff;
      box-shadow:0 2px 8px rgba(0,0,0,0.4);
      display:flex;align-items:center;justify-content:center;
      font-size:11px;color:#fff;font-weight:700;
      ${status === 'danger' ? 'animation:pulse 1s infinite;' : ''}
    ">👷</div>
    <style>@keyframes pulse{0%,100%{transform:scale(1)}50%{transform:scale(1.3)}}</style>`,
    iconSize: [26, 26],
    iconAnchor: [13, 13],
  });
}

function aedIcon() {
  return L.divIcon({
    className: '',
    html: `<div style="
      width:22px;height:22px;border-radius:4px;
      background:#E53935;border:2px solid #fff;
      box-shadow:0 2px 6px rgba(0,0,0,0.3);
      display:flex;align-items:center;justify-content:center;
      font-size:10px;color:#fff;font-weight:900;
    ">♥</div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });
}

interface AEDData {
  id: string;
  location: string;
  terminal: string;
}

export default function AirportMap() {
  const workers = useWorkerStore((s) => s.workers);
  const selectWorker = useWorkerStore((s) => s.selectWorker);
  const privacyMode = usePrivacyStore((s) => s.privacyMode);
  const [showAED, setShowAED] = useState(true);
  const [aedList, setAedList] = useState<AEDData[]>([]);

  useEffect(() => {
    fetch('/assets/aed-locations.json')
      .then((r) => r.json())
      .then((data) => setAedList(data))
      .catch(() => {});
  }, []);

  const dangerZones = workers.filter((w) => w.status === 'danger');
  const cautionZones = workers.filter((w) => w.status === 'caution');
  const hasEmergency = dangerZones.length > 0;

  // AED를 터미널별로 그룹화 → 대표 좌표에 표시
  const aedByTerminal = ['T1', 'T2', '탑승동'].map((t) => ({
    terminal: t,
    count: aedList.filter((a) => a.terminal === t).length,
    coord: AED_COORDS[t] || ICN_CENTER,
  }));

  return (
    <Card sx={{
      flex: 1, overflow: 'hidden',
      border: hasEmergency ? '2px solid #E53935' : undefined,
      animation: hasEmergency ? 'emergencyBorder 2s infinite' : undefined,
      '@keyframes emergencyBorder': { '0%,100%': { borderColor: '#E53935' }, '50%': { borderColor: '#E5393533' } },
    }}>
      <CardContent sx={{ p: '0 !important', height: '100%', position: 'relative' }}>
        {/* 지도 위 제목 */}
        <Box sx={{
          position: 'absolute', top: 8, left: 8, zIndex: 1000,
          background: 'rgba(10,17,24,0.9)', borderRadius: 2, px: 1.5, py: 0.8,
          border: '1px solid #1E3044',
        }}>
          <Typography sx={{ color: '#2E75B6', fontWeight: 700, fontSize: 12 }}>
            인천공항 실시간 관제
          </Typography>
          <Typography sx={{ color: '#7A8FA3', fontSize: 10 }}>
            작업자 {workers.length}명
            {dangerZones.length > 0 && <span style={{ color: '#EF5350' }}> | 위험 {dangerZones.length}</span>}
            {cautionZones.length > 0 && <span style={{ color: '#FFB74D' }}> | 주의 {cautionZones.length}</span>}
          </Typography>
        </Box>

        {/* AED 토글 + 층 선택 */}
        <Box sx={{
          position: 'absolute', top: 8, right: 8, zIndex: 1000,
          display: 'flex', flexDirection: 'column', gap: 0.5,
        }}>
          <Box onClick={() => setShowAED(!showAED)} sx={{
            display: 'flex', alignItems: 'center', gap: 0.5,
            background: 'rgba(10,17,24,0.9)', borderRadius: 1.5, px: 1, py: 0.3,
            border: `1px solid ${showAED ? '#E53935' : '#1E3044'}`, cursor: 'pointer',
          }}>
            <Typography sx={{ fontSize: 10, color: showAED ? '#EF5350' : '#5A7A96' }}>♥ AED {aedList.length}개</Typography>
            <Switch size="small" checked={showAED} sx={{
              transform: 'scale(0.6)', ml: -0.5,
              '& .MuiSwitch-switchBase.Mui-checked': { color: '#E53935' },
              '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': { backgroundColor: '#E53935' },
            }} />
          </Box>
          {['1F', '2F', '3F', 'B1'].map((floor) => (
            <Chip key={floor} label={floor} size="small" sx={{
              background: 'rgba(10,17,24,0.85)', color: '#8CB4D8', border: '1px solid #1E3044',
              fontSize: 9, height: 22, cursor: 'pointer', '&:hover': { background: 'rgba(46,117,182,0.3)' },
            }} />
          ))}
        </Box>

        {/* 긴급 모드 배너 */}
        {hasEmergency && (
          <Box sx={{
            position: 'absolute', bottom: 8, left: 8, right: 8, zIndex: 1000,
            background: 'rgba(229,57,53,0.95)', borderRadius: 2, px: 2, py: 1,
            display: 'flex', alignItems: 'center', gap: 1.5,
            animation: 'slideUp 0.3s ease',
            '@keyframes slideUp': { from: { transform: 'translateY(20px)', opacity: 0 }, to: { transform: 'translateY(0)', opacity: 1 } },
          }}>
            <Typography sx={{ fontSize: 18 }}>🚨</Typography>
            <Box sx={{ flex: 1 }}>
              <Typography sx={{ color: '#fff', fontWeight: 700, fontSize: 13 }}>
                긴급 상황 — {dangerZones.map((w) => privacyMode ? w.id : w.name).join(', ')}
              </Typography>
              <Typography sx={{ color: '#FFCDD2', fontSize: 11 }}>
                P2P 경보 발동 | 가장 가까운 AED: {dangerZones[0]?.zone || 'T1'} 구역
              </Typography>
            </Box>
            <Typography sx={{ color: '#fff', fontSize: 11, fontWeight: 600 }}>
              ♥ AED 안내 중
            </Typography>
          </Box>
        )}

        <MapContainer center={ICN_CENTER} zoom={ICN_ZOOM}
          style={{ height: '100%', width: '100%', background: '#1a2634' }} zoomControl={false}>
          <TileLayer url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}" attribution="&copy; Esri" />
          <TileLayer url="https://{s}.basemaps.cartocdn.com/light_only_labels/{z}/{x}/{y}{r}.png" attribution="&copy; CartoDB" />

          {/* 위험 구역 오버레이 */}
          <LayerGroup>
            {dangerZones.map((w, i) => (
              <Circle key={`d-${i}`} center={[w.lat, w.lng]} radius={150}
                pathOptions={{ color: '#E53935', fillColor: '#E53935', fillOpacity: 0.2, weight: 2, dashArray: '8,4' }} />
            ))}
            {cautionZones.map((w, i) => (
              <Circle key={`c-${i}`} center={[w.lat, w.lng]} radius={100}
                pathOptions={{ color: '#FF9800', fillColor: '#FF9800', fillOpacity: 0.1, weight: 1 }} />
            ))}
          </LayerGroup>

          {/* AED 마커 */}
          {showAED && aedByTerminal.map((t) => (
            <Marker key={`aed-${t.terminal}`} position={t.coord} icon={aedIcon()}>
              <Popup>
                <Box sx={{ fontFamily: 'Noto Sans KR', minWidth: 150 }}>
                  <Typography sx={{ fontWeight: 700, fontSize: 13, color: '#E53935' }}>♥ AED ({t.terminal})</Typography>
                  <Typography sx={{ fontSize: 11 }}>{t.count}대 설치</Typography>
                  <Typography sx={{ fontSize: 10, color: '#666', mt: 0.5 }}>
                    {aedList.filter((a) => a.terminal === t.terminal).slice(0, 3).map((a) => a.location).join('\n')}
                  </Typography>
                </Box>
              </Popup>
            </Marker>
          ))}

          {/* 작업자 마커 */}
          {workers.map((worker) => (
            <Marker key={worker.id} position={[worker.lat, worker.lng]}
              icon={workerIcon(worker.status)} eventHandlers={{ click: () => selectWorker(worker.id) }}>
              <Popup>
                <Box sx={{ minWidth: 180, fontFamily: 'Noto Sans KR' }}>
                  <Typography sx={{ fontWeight: 700, fontSize: 13 }}>
                    {privacyMode ? `작업자 ${worker.id}` : `${worker.name} (${worker.id})`}
                  </Typography>
                  <Typography sx={{ fontSize: 11, color: '#666' }}>{worker.role} | {worker.location}</Typography>
                  {privacyMode ? (
                    <Box sx={{ mt: 0.5, fontSize: 11 }}>
                      <span style={{ fontWeight: 600, color: worker.status === 'danger' ? '#E53935' : worker.status === 'caution' ? '#FF9800' : '#43A047' }}>
                        AI 위험도: {worker.status === 'normal' ? '낮음' : worker.status === 'caution' ? '주의' : '위험'}
                      </span>
                    </Box>
                  ) : (
                    <Box sx={{ mt: 0.5, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2px', fontSize: 11 }}>
                      <span>💓 {worker.heartRate}bpm</span>
                      <span>🌡 {worker.bodyTemp}°C</span>
                      <span>🫁 {worker.spo2}%</span>
                      <span>😰 {worker.stress}</span>
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
