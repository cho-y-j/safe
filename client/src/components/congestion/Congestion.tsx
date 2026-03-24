import React from 'react';
import { Box, Card, CardContent, Typography, Grid, Chip, LinearProgress } from '@mui/material';
import { useDashboardStore } from '../../stores/dashboardStore';
import { useWorkerStore } from '../../stores/workerStore';

const zones = [
  { id: 'T1-A', name: '제1터미널 A구역', type: '터미널' },
  { id: 'T1-B', name: '제1터미널 B구역', type: '터미널' },
  { id: 'T1-C', name: '제1터미널 C구역', type: '터미널' },
  { id: 'T2-S', name: '제2터미널 보안', type: '터미널' },
  { id: 'CB-3', name: '탑승동 연결통로', type: '통로' },
  { id: 'CG-1', name: '화물터미널 1구역', type: '화물' },
  { id: 'CT-1', name: '기내식 센터', type: '서비스' },
  { id: 'AP-2', name: '계류장 2구역', type: '계류장' },
  { id: 'R2-S', name: '제2활주로 남단', type: '활주로' },
];

function congestionColor(level: number) {
  if (level >= 80) return '#E53935';
  if (level >= 60) return '#FF9800';
  if (level >= 40) return '#FFC107';
  return '#43A047';
}

export default function Congestion() {
  const { flights, forecast } = useDashboardStore();
  const workers = useWorkerStore((s) => s.workers);

  // 구역별 혼잡도 시뮬레이션
  const zoneData = zones.map((zone) => {
    const zoneWorkers = workers.filter((w) => w.zone === zone.id);
    const flightFactor = flights ? Math.min(100, (flights.total / 40) * 60) : 30;
    const workerFactor = zoneWorkers.length * 15;
    const baseCongestion = Math.min(100, flightFactor * 0.6 + workerFactor + Math.random() * 15);

    return {
      ...zone,
      congestion: Math.round(baseCongestion),
      workerCount: zoneWorkers.length,
      maxCapacity: 10 + Math.floor(Math.random() * 20),
      dangerWorkers: zoneWorkers.filter((w) => w.status === 'danger').length,
      cautionWorkers: zoneWorkers.filter((w) => w.status === 'caution').length,
    };
  });

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {/* 요약 */}
      <Grid container spacing={2}>
        <Grid item xs={3}>
          <Card><CardContent sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>전체 평균 혼잡도</Typography>
            <Typography sx={{ fontSize: 32, fontWeight: 700, color: congestionColor(Math.round(zoneData.reduce((s, z) => s + z.congestion, 0) / zoneData.length)) }}>
              {Math.round(zoneData.reduce((s, z) => s + z.congestion, 0) / zoneData.length)}%
            </Typography>
          </CardContent></Card>
        </Grid>
        <Grid item xs={3}>
          <Card><CardContent sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>혼잡 구역</Typography>
            <Typography sx={{ fontSize: 32, fontWeight: 700, color: '#FF9800' }}>
              {zoneData.filter((z) => z.congestion >= 60).length}
            </Typography>
          </CardContent></Card>
        </Grid>
        <Grid item xs={3}>
          <Card><CardContent sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>현재 운항</Typography>
            <Typography sx={{ fontSize: 32, fontWeight: 700, color: '#2E75B6' }}>{flights?.total || 0}편</Typography>
          </CardContent></Card>
        </Grid>
        <Grid item xs={3}>
          <Card><CardContent sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>피크 예측</Typography>
            <Typography sx={{ fontSize: 32, fontWeight: 700, color: '#E53935' }}>
              {forecast?.peakHour != null ? `${forecast.peakHour}시` : '--'}
            </Typography>
          </CardContent></Card>
        </Grid>
      </Grid>

      {/* 구역별 상세 */}
      <Card>
        <CardContent>
          <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 2 }}>🗺 구역별 혼잡도 현황</Typography>
          <Grid container spacing={1.5}>
            {zoneData.map((zone) => (
              <Grid item xs={12} sm={6} md={4} key={zone.id}>
                <Box sx={{
                  p: 2, borderRadius: 2,
                  background: 'rgba(255,255,255,0.02)',
                  border: `1px solid ${zone.congestion >= 80 ? '#E53935' : zone.congestion >= 60 ? '#FF9800' : '#1E3044'}`,
                }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#E0E6ED' }}>{zone.name}</Typography>
                    <Chip label={zone.type} size="small" sx={{ height: 18, fontSize: 9, background: 'rgba(46,117,182,0.15)', color: '#5CA0E0' }} />
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                    <Typography sx={{ fontSize: 24, fontWeight: 700, color: congestionColor(zone.congestion) }}>
                      {zone.congestion}%
                    </Typography>
                    <Box sx={{ flex: 1 }}>
                      <LinearProgress variant="determinate" value={zone.congestion}
                        sx={{ height: 6, borderRadius: 3, background: 'rgba(255,255,255,0.05)', '& .MuiLinearProgress-bar': { background: congestionColor(zone.congestion), borderRadius: 3 } }}
                      />
                    </Box>
                  </Box>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, color: '#7A8FA3' }}>
                    <span>작업자 {zone.workerCount}명</span>
                    {zone.dangerWorkers > 0 && <Chip label={`위험 ${zone.dangerWorkers}`} size="small" sx={{ height: 14, fontSize: 8, background: 'rgba(229,57,53,0.15)', color: '#EF5350' }} />}
                  </Box>
                </Box>
              </Grid>
            ))}
          </Grid>
        </CardContent>
      </Card>

      {/* 인력 배치 최적화 */}
      <Card>
        <CardContent>
          <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 2 }}>👥 AI 인력 배치 최적화 권고</Typography>
          {zoneData.filter((z) => z.congestion >= 70).map((zone) => (
            <Box key={zone.id} sx={{ p: 1.5, background: 'rgba(255,152,0,0.08)', borderRadius: 2, mb: 1, display: 'flex', alignItems: 'center', gap: 2 }}>
              <Typography sx={{ fontSize: 20 }}>⚠️</Typography>
              <Box>
                <Typography sx={{ fontSize: 12, color: '#FFB74D', fontWeight: 600 }}>{zone.name} — 혼잡도 {zone.congestion}%</Typography>
                <Typography sx={{ fontSize: 11, color: '#7A8FA3' }}>
                  여유 구역에서 인력 {Math.ceil((zone.congestion - 60) / 10)}명 재배치 권고
                </Typography>
              </Box>
            </Box>
          ))}
          {zoneData.filter((z) => z.congestion >= 70).length === 0 && (
            <Typography sx={{ fontSize: 12, color: '#5A7A96', textAlign: 'center', py: 2 }}>현재 인력 배치 적정</Typography>
          )}
        </CardContent>
      </Card>
    </Box>
  );
}
