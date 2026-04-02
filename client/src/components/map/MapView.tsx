import { useState, useEffect } from 'react';
import { MapContainer, ImageOverlay, Marker, Popup, Circle, LayerGroup, useMap } from 'react-leaflet';
import { Box, Card, CardContent, Typography, Chip, Switch } from '@mui/material';
import L from 'leaflet';
import { useWorkerStore } from '../../stores/workerStore';
import { usePrivacyStore } from '../../stores/privacyStore';

type Bounds = [[number, number], [number, number]];

interface TerminalConfig {
  id: string;
  label: string;
  floors: string[];
  bounds: Bounds;
  center: [number, number];
  zoom: number;
}

const TERMINALS: TerminalConfig[] = [
  { id: 'T1', label: '제1터미널', floors: ['1F', '2F', '3F', '4F'], bounds: [[37.4468, 126.4465], [37.4518, 126.4575]], center: [37.4493, 126.4520], zoom: 17 },
  { id: 'T2', label: '제2터미널', floors: ['1F', '2F', '3F', '4F', '5F'], bounds: [[37.4585, 126.4205], [37.4630, 126.4320]], center: [37.4608, 126.4262], zoom: 17 },
  { id: 'CB', label: '탑승동', floors: ['1F', '2F', '3F', '4F'], bounds: [[37.4570, 126.4375], [37.4625, 126.4480]], center: [37.4598, 126.4428], zoom: 17 },
];

const AED_POSITIONS: Record<string, { lat: number; lng: number; floor: string }[]> = {
  'T1': [{ lat: 37.4490, lng: 126.4510, floor: '1F' }, { lat: 37.4493, lng: 126.4525, floor: '1F' }, { lat: 37.4488, lng: 126.4540, floor: '3F' }, { lat: 37.4496, lng: 126.4515, floor: '3F' }],
  'T2': [{ lat: 37.4605, lng: 126.4255, floor: '1F' }, { lat: 37.4610, lng: 126.4270, floor: '3F' }],
  'CB': [{ lat: 37.4598, lng: 126.4420, floor: '2F' }, { lat: 37.4600, lng: 126.4440, floor: '3F' }],
};

function workerIcon(status: string) {
  const color = status === 'danger' ? '#E53935' : status === 'caution' ? '#FF9800' : '#43A047';
  return L.divIcon({
    className: '',
    html: `<div style="width:24px;height:24px;border-radius:50%;background:${color};border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;font-size:11px;color:#fff;font-weight:700;${status === 'danger' ? 'animation:pulse 1s infinite;' : ''}">👷</div><style>@keyframes pulse{0%,100%{transform:scale(1)}50%{transform:scale(1.3)}}</style>`,
    iconSize: [24, 24], iconAnchor: [12, 12],
  });
}

function aedIcon() {
  return L.divIcon({
    className: '',
    html: `<div style="width:20px;height:20px;border-radius:3px;background:#E53935;border:2px solid #fff;box-shadow:0 2px 4px rgba(0,0,0,0.4);display:flex;align-items:center;justify-content:center;font-size:10px;color:#fff;font-weight:900;">♥</div>`,
    iconSize: [20, 20], iconAnchor: [10, 10],
  });
}

function MapViewController({ center, zoom }: { center: [number, number]; zoom: number }) {
  const map = useMap();
  useEffect(() => { map.flyTo(center, zoom, { duration: 0.8 }); }, [center, zoom]);
  return null;
}

export default function MapView() {
  const workers = useWorkerStore((s) => s.workers);
  const selectedWorkerId = useWorkerStore((s) => s.selectedWorkerId);
  const selectWorker = useWorkerStore((s) => s.selectWorker);
  const privacyMode = usePrivacyStore((s) => s.privacyMode);
  const [selectedTerminal, setSelectedTerminal] = useState('T1');
  const [selectedFloor, setSelectedFloor] = useState('3F');
  const [showAED, setShowAED] = useState(true);
  const [mapCenter, setMapCenter] = useState<[number, number] | null>(null);

  const terminal = TERMINALS.find((t) => t.id === selectedTerminal)!;

  // ★ 선택된 작업자가 있으면 자동 줌
  useEffect(() => {
    if (selectedWorkerId) {
      const worker = workers.find((w) => w.id === selectedWorkerId);
      if (worker && worker.lat && worker.lng) {
        setMapCenter([worker.lat, worker.lng]);
      }
    }
  }, [selectedWorkerId, workers]);

  useEffect(() => {
    if (!terminal.floors.includes(selectedFloor)) {
      setSelectedFloor(terminal.floors[Math.floor(terminal.floors.length / 2)]);
    }
  }, [selectedTerminal]);

  const floorPlanUrl = `/assets/floors/${selectedTerminal}-${selectedFloor}.png`;
  const dangerWorkers = workers.filter((w) => w.status === 'danger');
  const cautionWorkers = workers.filter((w) => w.status === 'caution');
  const currentAEDs = AED_POSITIONS[selectedTerminal]?.filter((a) => a.floor === selectedFloor) || [];

  return (
    <Box sx={{ height: 'calc(100vh - 72px)', position: 'relative' }}>
      {/* 좌상단: 터미널 선택 */}
      <Box sx={{ position: 'absolute', top: 8, left: 8, zIndex: 1000, display: 'flex', flexDirection: 'column', gap: 0.8 }}>
        <Box sx={{ background: 'rgba(10,17,24,0.92)', borderRadius: 2, px: 1.5, py: 0.8, border: '1px solid #1E3044', backdropFilter: 'blur(8px)' }}>
          <Typography sx={{ color: '#2E75B6', fontWeight: 700, fontSize: 13 }}>지도 현황</Typography>
          <Typography sx={{ color: '#7A8FA3', fontSize: 10 }}>
            {terminal.label} {selectedFloor} | 작업자 {workers.length}명
            {dangerWorkers.length > 0 && <span style={{ color: '#EF5350' }}> | 위험 {dangerWorkers.length}</span>}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 0.3 }}>
          {TERMINALS.map((t) => (
            <Chip key={t.id} label={t.label} size="small" onClick={() => setSelectedTerminal(t.id)}
              sx={{ fontSize: 10, height: 26, cursor: 'pointer',
                background: selectedTerminal === t.id ? 'rgba(46,117,182,0.3)' : 'rgba(10,17,24,0.85)',
                color: selectedTerminal === t.id ? '#fff' : '#7A8FA3',
                border: selectedTerminal === t.id ? '1px solid #2E75B6' : '1px solid #1E3044',
              }}
            />
          ))}
        </Box>
      </Box>

      {/* 우상단: 층 + AED */}
      <Box sx={{ position: 'absolute', top: 8, right: 8, zIndex: 1000, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
        <Box onClick={() => setShowAED(!showAED)} sx={{
          display: 'flex', alignItems: 'center', gap: 0.5, background: 'rgba(10,17,24,0.92)',
          borderRadius: 1.5, px: 1, py: 0.4, border: `1px solid ${showAED ? '#E53935' : '#1E3044'}`, cursor: 'pointer',
        }}>
          <Typography sx={{ fontSize: 10, color: showAED ? '#EF5350' : '#5A7A96' }}>♥ AED {currentAEDs.length}개</Typography>
        </Box>
        {terminal.floors.map((floor) => (
          <Chip key={floor} label={floor} size="small" onClick={() => setSelectedFloor(floor)}
            sx={{ fontSize: 11, height: 28, fontWeight: 600, cursor: 'pointer',
              background: selectedFloor === floor ? 'rgba(46,117,182,0.4)' : 'rgba(10,17,24,0.85)',
              color: selectedFloor === floor ? '#fff' : '#8CB4D8',
              border: selectedFloor === floor ? '2px solid #2E75B6' : '1px solid #1E3044',
            }}
          />
        ))}
      </Box>

      {/* 긴급 배너 */}
      {dangerWorkers.length > 0 && (
        <Box sx={{
          position: 'absolute', bottom: 8, left: 8, right: 8, zIndex: 1000,
          background: 'rgba(229,57,53,0.95)', borderRadius: 2, px: 2, py: 1,
          display: 'flex', alignItems: 'center', gap: 1.5,
        }}>
          <Typography sx={{ fontSize: 16 }}>🚨</Typography>
          <Box sx={{ flex: 1 }}>
            <Typography sx={{ color: '#fff', fontWeight: 700, fontSize: 13 }}>
              긴급 — {dangerWorkers.map((w) => privacyMode ? w.id : w.name).join(', ')}
            </Typography>
            <Typography sx={{ color: '#FFCDD2', fontSize: 11 }}>P2P 경보 발동 | AED 안내 중</Typography>
          </Box>
        </Box>
      )}

      {/* Leaflet 지도 */}
      <MapContainer center={mapCenter || terminal.center} zoom={mapCenter ? 18 : terminal.zoom}
        style={{ height: '100%', width: '100%', background: '#f0f0f0' }}
        zoomControl={false} minZoom={15} maxZoom={20}>
        <MapViewController center={mapCenter || terminal.center} zoom={mapCenter ? 18 : terminal.zoom} />
        <ImageOverlay url={floorPlanUrl} bounds={terminal.bounds} opacity={1} />

        <LayerGroup>
          {dangerWorkers.map((w, i) => (
            <Circle key={`d-${i}`} center={[w.lat, w.lng]} radius={30}
              pathOptions={{ color: '#E53935', fillColor: '#E53935', fillOpacity: 0.25, weight: 2, dashArray: '6,3' }} />
          ))}
          {cautionWorkers.map((w, i) => (
            <Circle key={`c-${i}`} center={[w.lat, w.lng]} radius={20}
              pathOptions={{ color: '#FF9800', fillColor: '#FF9800', fillOpacity: 0.15, weight: 1 }} />
          ))}
        </LayerGroup>

        {showAED && currentAEDs.map((aed, i) => (
          <Marker key={`aed-${i}`} position={[aed.lat, aed.lng]} icon={aedIcon()}>
            <Popup><Typography sx={{ fontWeight: 700, color: '#E53935' }}>♥ AED — {selectedTerminal} {aed.floor}</Typography></Popup>
          </Marker>
        ))}

        {workers.map((worker) => (
          <Marker key={worker.id} position={[worker.lat, worker.lng]}
            icon={workerIcon(worker.status)} eventHandlers={{ click: () => selectWorker(worker.id) }}>
            <Popup>
              <Box sx={{ minWidth: 160, fontFamily: 'Noto Sans KR' }}>
                <Typography sx={{ fontWeight: 700, fontSize: 13 }}>
                  {privacyMode ? `작업자 ${worker.id}` : `${worker.name} (${worker.id})`}
                </Typography>
                <Typography sx={{ fontSize: 11, color: '#666' }}>{worker.role} | {worker.location}</Typography>
              </Box>
            </Popup>
          </Marker>
        ))}

        {/* ★ 선택된 작업자 강조 표시 (빨간 펄스 원) */}
        {selectedWorkerId && (() => {
          const sw = workers.find((w) => w.id === selectedWorkerId);
          return sw ? (
            <Circle center={[sw.lat, sw.lng]} radius={20}
              pathOptions={{ color: '#FF1744', fillColor: '#FF1744', fillOpacity: 0.4, weight: 3 }} />
          ) : null;
        })()}
      </MapContainer>
    </Box>
  );
}
