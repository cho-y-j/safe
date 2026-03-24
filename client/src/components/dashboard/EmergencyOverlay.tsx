import { Box, Typography, Chip, Button, Grid, LinearProgress } from '@mui/material';
import type { WorkerData } from '../../types';

interface Props {
  workers: WorkerData[];      // 위험 상태 작업자들
  allWorkers: WorkerData[];   // 전체 작업자 (주변 동료 찾기용)
  onClose: () => void;
}

export default function EmergencyOverlay({ workers, allWorkers, onClose }: Props) {
  const target = workers[0]; // 첫 번째 위험 작업자
  if (!target) return null;

  // 가장 가까운 동료 (같은 구역 우선, 그 다음 다른 구역)
  const nearbyWorkers = allWorkers
    .filter((w) => w.id !== target.id && w.status !== 'danger')
    .sort((a, b) => {
      const aZone = a.zone === target.zone ? 0 : 1;
      const bZone = b.zone === target.zone ? 0 : 1;
      return aZone - bZone;
    })
    .slice(0, 3);

  return (
    <Box sx={{ p: 3 }}>
      {/* 헤더 */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
        <Box sx={{
          width: 48, height: 48, borderRadius: 2,
          background: '#E53935', display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 24, animation: 'pulse 1s infinite',
          '@keyframes pulse': { '0%,100%': { transform: 'scale(1)' }, '50%': { transform: 'scale(1.1)' } },
        }}>🚨</Box>
        <Box sx={{ flex: 1 }}>
          <Typography sx={{ fontSize: 22, fontWeight: 800, color: '#E53935' }}>
            긴급 상황 발생
          </Typography>
          <Typography sx={{ fontSize: 14, color: '#FFCDD2' }}>
            P2P BLE 경보 발동 | 관제센터 자동 통보 완료
          </Typography>
        </Box>
        <Button variant="outlined" onClick={onClose} sx={{ color: '#7A8FA3', borderColor: '#1E3044' }}>
          경보 해제
        </Button>
      </Box>

      <Grid container spacing={2}>
        {/* 좌측: 위험 작업자 정보 */}
        <Grid item xs={5}>
          <Box sx={{ p: 2, background: 'rgba(229,57,53,0.08)', borderRadius: 2, border: '1px solid rgba(229,57,53,0.3)' }}>
            <Typography sx={{ fontSize: 12, color: '#EF5350', fontWeight: 600, mb: 1 }}>위험 작업자</Typography>

            <Typography sx={{ fontSize: 20, fontWeight: 800, color: '#E0E6ED' }}>
              {target.name} <Typography component="span" sx={{ fontSize: 12, color: '#7A8FA3' }}>{target.id}</Typography>
            </Typography>
            <Typography sx={{ fontSize: 12, color: '#7A8FA3', mb: 2 }}>
              {target.role} · {target.location} · {target.zone}
            </Typography>

            {/* 생체 데이터 (긴급 시 원시 데이터 표시) */}
            <Grid container spacing={1}>
              {[
                { icon: '💓', label: '심박수', value: `${target.heartRate} bpm`, danger: target.heartRate > 110 },
                { icon: '🌡', label: '체온', value: `${target.bodyTemp}°C`, danger: target.bodyTemp > 37.5 },
                { icon: '🫁', label: 'SpO₂', value: `${target.spo2}%`, danger: target.spo2 < 94 },
                { icon: '😰', label: '스트레스', value: `${target.stress}`, danger: target.stress > 70 },
              ].map((v) => (
                <Grid item xs={6} key={v.label}>
                  <Box sx={{ p: 1, background: 'rgba(0,0,0,0.2)', borderRadius: 1.5, textAlign: 'center', border: v.danger ? '1px solid #E53935' : undefined }}>
                    <Typography sx={{ fontSize: 14 }}>{v.icon}</Typography>
                    <Typography sx={{ fontSize: 18, fontWeight: 700, color: v.danger ? '#EF5350' : '#E0E6ED' }}>{v.value}</Typography>
                    <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>{v.label}</Typography>
                  </Box>
                </Grid>
              ))}
            </Grid>

            {target.medicalHistory && (
              <Box sx={{ mt: 1.5, p: 1, background: 'rgba(229,57,53,0.1)', borderRadius: 1.5 }}>
                <Typography sx={{ fontSize: 11, color: '#EF5350' }}>🏥 의료 이력: {target.medicalHistory}</Typography>
              </Box>
            )}
          </Box>
        </Grid>

        {/* 우측: 대응 정보 */}
        <Grid item xs={7}>
          {/* 가장 가까운 동료 */}
          <Box sx={{ p: 2, background: 'rgba(255,255,255,0.02)', borderRadius: 2, border: '1px solid #1E3044', mb: 2 }}>
            <Typography sx={{ fontSize: 12, color: '#2E75B6', fontWeight: 600, mb: 1 }}>
              P2P 경보 수신 동료 (거리순)
            </Typography>
            {nearbyWorkers.map((w, i) => {
              const sameZone = w.zone === target.zone;
              const dist = sameZone ? '~10-30m' : '~50-100m';
              const intensity = sameZone ? 90 : 40;
              return (
                <Box key={w.id} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1, p: 1, background: 'rgba(255,255,255,0.02)', borderRadius: 1.5 }}>
                  <Typography sx={{ fontSize: 14, fontWeight: 700, color: '#2E75B6', minWidth: 20 }}>{i + 1}</Typography>
                  <Box sx={{ flex: 1 }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#E0E6ED' }}>
                      {w.name} <Typography component="span" sx={{ fontSize: 10, color: '#5A7A96' }}>{w.role} · {w.zone}</Typography>
                    </Typography>
                    <Box sx={{ display: 'flex', gap: 1, mt: 0.3 }}>
                      <Chip label={dist} size="small" sx={{ height: 16, fontSize: 9, background: 'rgba(46,117,182,0.12)', color: '#5CA0E0' }} />
                      <Chip label={`진동 ${intensity}%`} size="small" sx={{ height: 16, fontSize: 9, background: sameZone ? 'rgba(229,57,53,0.15)' : 'rgba(255,152,0,0.15)', color: sameZone ? '#EF5350' : '#FFB74D' }} />
                    </Box>
                  </Box>
                  <Chip label={sameZone ? '같은 구역' : '인접 구역'} size="small" sx={{ fontSize: 9, background: sameZone ? 'rgba(67,160,71,0.15)' : 'rgba(255,255,255,0.05)', color: sameZone ? '#66BB6A' : '#5A7A96' }} />
                </Box>
              );
            })}
          </Box>

          {/* AED + 119 */}
          <Box sx={{ display: 'flex', gap: 2 }}>
            <Box sx={{ flex: 1, p: 2, background: 'rgba(229,57,53,0.06)', borderRadius: 2, border: '1px solid rgba(229,57,53,0.2)', textAlign: 'center' }}>
              <Typography sx={{ fontSize: 28 }}>♥</Typography>
              <Typography sx={{ fontSize: 14, fontWeight: 700, color: '#E53935' }}>가장 가까운 AED</Typography>
              <Typography sx={{ fontSize: 12, color: '#E0E6ED', mt: 0.5 }}>
                {target.zone.startsWith('T1') ? 'T1-1F 중앙홀' : target.zone.startsWith('T2') ? 'T2-1F 대합실' : '탑승동 2F'}
              </Typography>
              <Typography sx={{ fontSize: 11, color: '#7A8FA3' }}>약 30~50m</Typography>
            </Box>

            <Box sx={{ flex: 1, p: 2, background: 'rgba(255,255,255,0.02)', borderRadius: 2, border: '1px solid #1E3044', textAlign: 'center' }}>
              <Typography sx={{ fontSize: 28 }}>🚑</Typography>
              <Typography sx={{ fontSize: 14, fontWeight: 700, color: '#FFB74D' }}>에스컬레이션</Typography>
              <Box sx={{ mt: 0.5 }}>
                <Typography sx={{ fontSize: 10, color: '#66BB6A' }}>✅ 1단계: 본인 알림</Typography>
                <Typography sx={{ fontSize: 10, color: '#66BB6A' }}>✅ 2단계: P2P 동료 경보</Typography>
                <Typography sx={{ fontSize: 10, color: '#66BB6A' }}>✅ 3단계: 관제센터 알림</Typography>
                <Typography sx={{ fontSize: 10, color: '#5A7A96' }}>⏳ 4단계: 119 자동 (90초 후)</Typography>
              </Box>
            </Box>
          </Box>
        </Grid>
      </Grid>
    </Box>
  );
}
