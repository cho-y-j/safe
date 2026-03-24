import { Box, Typography, Chip } from '@mui/material';
import { useWorkerStore } from '../../stores/workerStore';

// 구역 정의
const ZONES = [
  { id: 'T1', label: '제1터미널', zones: ['T1-B', 'T1-C'], x: 15, y: 45, w: 25, h: 20 },
  { id: 'T2', label: '제2터미널', zones: ['T2-S'], x: 60, y: 45, w: 25, h: 20 },
  { id: 'CB', label: '탑승동', zones: ['CB-3'], x: 37, y: 38, w: 26, h: 12 },
  { id: 'RW', label: '활주로', zones: ['R2-S'], x: 10, y: 78, w: 80, h: 8 },
  { id: 'AP', label: '계류장', zones: ['AP-2'], x: 38, y: 68, w: 24, h: 8 },
  { id: 'CG', label: '화물', zones: ['CG-1'], x: 8, y: 25, w: 15, h: 12 },
  { id: 'CT', label: '기내식', zones: ['CT-1'], x: 77, y: 25, w: 15, h: 12 },
];

function getZoneColor(workers: any[]): string {
  if (workers.some(w => w.status === 'danger')) return '#E53935';
  if (workers.some(w => w.status === 'caution')) return '#FF9800';
  if (workers.length > 0) return '#43A047';
  return '#1E3044';
}

export default function AirportOverview() {
  const workers = useWorkerStore((s) => s.workers);

  const zoneData = ZONES.map((zone) => {
    const zoneWorkers = workers.filter((w) => zone.zones.includes(w.zone));
    return { ...zone, workers: zoneWorkers, color: getZoneColor(zoneWorkers) };
  });

  return (
    <Box sx={{ position: 'relative', width: '100%', height: '100%', minHeight: 180 }}>
      {/* 배경 라벨 */}
      <Typography sx={{ position: 'absolute', top: 4, left: 8, fontSize: 10, color: '#2E75B6', fontWeight: 700 }}>
        인천공항 구역 현황
      </Typography>

      {/* 연결선: T1 - 탑승동 - T2 */}
      <svg style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
        {/* T1 → 탑승동 */}
        <line x1="40%" y1="55%" x2="37%" y2="44%" stroke="#1E3044" strokeWidth="2" strokeDasharray="4,3" />
        {/* 탑승동 → T2 */}
        <line x1="63%" y1="44%" x2="60%" y2="55%" stroke="#1E3044" strokeWidth="2" strokeDasharray="4,3" />
        {/* 활주로 라인 */}
        <line x1="10%" y1="82%" x2="90%" y2="82%" stroke="#2E75B6" strokeWidth="1.5" opacity="0.3" />
        <line x1="10%" y1="86%" x2="90%" y2="86%" stroke="#2E75B6" strokeWidth="1.5" opacity="0.3" />
      </svg>

      {/* 구역 블록들 */}
      {zoneData.map((zone) => (
        <Box key={zone.id} sx={{
          position: 'absolute',
          left: `${zone.x}%`, top: `${zone.y}%`,
          width: `${zone.w}%`, height: `${zone.h}%`,
          background: `${zone.color}15`,
          border: `1.5px solid ${zone.color}66`,
          borderRadius: 1.5,
          display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center',
          cursor: 'pointer',
          transition: 'all 0.2s',
          '&:hover': { background: `${zone.color}25`, transform: 'scale(1.03)' },
        }}>
          <Typography sx={{ fontSize: 9, color: '#7A8FA3', fontWeight: 600 }}>{zone.label}</Typography>
          <Typography sx={{ fontSize: 16, fontWeight: 800, color: zone.color, lineHeight: 1 }}>
            {zone.workers.length}
          </Typography>
          <Typography sx={{ fontSize: 8, color: '#5A7A96' }}>명</Typography>
          {zone.workers.some(w => w.status === 'danger') && (
            <Box sx={{
              position: 'absolute', top: -4, right: -4,
              width: 10, height: 10, borderRadius: '50%', background: '#E53935',
              animation: 'blink 1s infinite',
              '@keyframes blink': { '0%,100%': { opacity: 1 }, '50%': { opacity: 0.3 } },
            }} />
          )}
        </Box>
      ))}
    </Box>
  );
}
