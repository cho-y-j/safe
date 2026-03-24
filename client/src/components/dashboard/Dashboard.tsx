import { useState, useEffect } from 'react';
import { Box, Card, CardContent, Typography, Grid, Chip, LinearProgress, Dialog } from '@mui/material';
import { useDashboardStore } from '../../stores/dashboardStore';
import { useWorkerStore } from '../../stores/workerStore';
import { useAlertStore } from '../../stores/alertStore';
import { useInsightStore } from '../../stores/insightStore';
import { usePrivacyStore } from '../../stores/privacyStore';
import AIInsightPanel from './AIInsightPanel';
import EmergencyOverlay from './EmergencyOverlay';
import AirportOverview from './AirportOverview';
import WorkforceTable from './WorkforceTable';

const riskColorMap: Record<string, string> = { '안전': '#43A047', '주의': '#1E88E5', '경고': '#FF9800', '위험': '#E53935' };

export default function Dashboard() {
  const { airQuality, weather, flights, riskAnalysis } = useDashboardStore();
  const workers = useWorkerStore((s) => s.workers);
  const events = useAlertStore((s) => s.events);
  const privacyMode = usePrivacyStore((s) => s.privacyMode);

  const dangerWorkers = workers.filter((w) => w.status === 'danger');
  const cautionWorkers = workers.filter((w) => w.status === 'caution');
  const normalWorkers = workers.filter((w) => w.status === 'normal');
  const [showEmergency, setShowEmergency] = useState(false);

  // 위험 작업자 발생 시 자동 긴급 팝업
  useEffect(() => {
    if (dangerWorkers.length > 0) setShowEmergency(true);
  }, [dangerWorkers.length]);

  const score = riskAnalysis?.totalScore ?? 0;
  const level = riskAnalysis?.level ?? '안전';
  const color = riskColorMap[level] || '#43A047';

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, height: 'calc(100vh - 72px)', overflow: 'auto',
      '&::-webkit-scrollbar': { width: 4 }, '&::-webkit-scrollbar-thumb': { background: '#1E3044', borderRadius: 2 },
    }}>

      {/* ═══ 1행: 핵심 지표 4개 ═══ */}
      <Grid container spacing={1}>
        {/* 종합 위험도 */}
        <Grid item xs={3}>
          <Card sx={{ background: `linear-gradient(135deg, ${color}15, transparent)`, border: `1px solid ${color}33` }}>
            <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 }, textAlign: 'center' }}>
              <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>종합 위험도</Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1, mt: 0.5 }}>
                <Box sx={{ position: 'relative', width: 52, height: 52 }}>
                  <svg width="52" height="52" style={{ transform: 'rotate(-90deg)' }}>
                    <circle cx="26" cy="26" r="22" fill="none" stroke="rgba(255,255,255,0.05)" strokeWidth="5" />
                    <circle cx="26" cy="26" r="22" fill="none" stroke={color} strokeWidth="5" strokeLinecap="round"
                      strokeDasharray={`${2 * Math.PI * 22}`} strokeDashoffset={`${2 * Math.PI * 22 * (1 - score / 100)}`}
                      style={{ transition: 'stroke-dashoffset 1s ease' }} />
                  </svg>
                  <Box sx={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', textAlign: 'center' }}>
                    <Typography sx={{ fontSize: 16, fontWeight: 800, color, lineHeight: 1 }}>{score}</Typography>
                  </Box>
                </Box>
                <Box>
                  <Chip label={level} size="small" sx={{ fontWeight: 700, fontSize: 11, background: `${color}22`, color, border: `1px solid ${color}44` }} />
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* 작업자 현황 */}
        <Grid item xs={3}>
          <Card>
            <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 }, textAlign: 'center' }}>
              <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>작업자 현황</Typography>
              <Typography sx={{ fontSize: 28, fontWeight: 800, color: '#E0E6ED', mt: 0.3 }}>{workers.length}<Typography component="span" sx={{ fontSize: 12, color: '#5A7A96' }}>명</Typography></Typography>
              <Box sx={{ display: 'flex', justifyContent: 'center', gap: 1, mt: 0.3 }}>
                <Typography sx={{ fontSize: 10, color: '#43A047' }}>정상 {normalWorkers.length}</Typography>
                <Typography sx={{ fontSize: 10, color: '#FF9800' }}>주의 {cautionWorkers.length}</Typography>
                <Typography sx={{ fontSize: 10, color: '#E53935' }}>위험 {dangerWorkers.length}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* 대기질 */}
        <Grid item xs={3}>
          <Card>
            <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 }, textAlign: 'center' }}>
              <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>대기질 PM2.5</Typography>
              <Typography sx={{ fontSize: 28, fontWeight: 800, color: airQuality && airQuality.pm25 >= 35 ? '#FFB74D' : '#42A5F5', mt: 0.3 }}>
                {airQuality?.pm25 ?? '--'}<Typography component="span" sx={{ fontSize: 10, color: '#5A7A96' }}>㎍/㎥</Typography>
              </Typography>
              <Chip label={airQuality?.grade || '-'} size="small" sx={{ height: 18, fontSize: 9,
                background: airQuality?.grade === '나쁨' ? 'rgba(255,152,0,0.15)' : 'rgba(67,160,71,0.15)',
                color: airQuality?.grade === '나쁨' ? '#FFB74D' : airQuality?.grade === '보통' ? '#42A5F5' : '#66BB6A',
              }} />
            </CardContent>
          </Card>
        </Grid>

        {/* 운항 */}
        <Grid item xs={3}>
          <Card>
            <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 }, textAlign: 'center' }}>
              <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>현재 운항</Typography>
              <Typography sx={{ fontSize: 28, fontWeight: 800, color: flights && flights.total >= 30 ? '#FFB74D' : '#2E75B6', mt: 0.3 }}>
                {flights?.total ?? '--'}<Typography component="span" sx={{ fontSize: 10, color: '#5A7A96' }}>편</Typography>
              </Typography>
              <Typography sx={{ fontSize: 10, color: '#5A7A96' }}>
                도착 {flights?.arriving ?? '-'} / 출발 {flights?.departing ?? '-'}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* ═══ 2행: AI분석 + 공항 구역도 + 근무현황 ═══ */}
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Box sx={{ flex: '0 0 30%' }}>
          <AIInsightPanel />
        </Box>
        <Card sx={{ flex: '0 0 35%' }}>
          <CardContent sx={{ py: 1, px: 1.5, '&:last-child': { pb: 1 }, height: '100%' }}>
            <AirportOverview />
          </CardContent>
        </Card>
        <Card sx={{ flex: 1 }}>
          <CardContent sx={{ py: 1, px: 1.5, '&:last-child': { pb: 1 } }}>
            <WorkforceTable />
          </CardContent>
        </Card>
      </Box>

      {/* ═══ 3행: 작업자 카드 ═══ */}
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Card sx={{ flex: 1 }}>
          <CardContent sx={{ py: 1, px: 1.5, '&:last-child': { pb: 1 } }}>
            <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 0.8, fontSize: 12 }}>
              작업자 실시간 현황
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 0.8 }}>
              {[...dangerWorkers, ...cautionWorkers, ...normalWorkers].map((w) => {
                const statusColor = w.status === 'danger' ? '#E53935' : w.status === 'caution' ? '#FF9800' : '#43A047';
                const statusLabel = w.status === 'danger' ? '위험' : w.status === 'caution' ? '주의' : '정상';
                return (
                  <Box key={w.id} sx={{
                    p: 1, borderRadius: 1.5, borderLeft: `3px solid ${statusColor}`,
                    background: w.status === 'danger' ? 'rgba(229,57,53,0.08)' : w.status === 'caution' ? 'rgba(255,152,0,0.05)' : 'rgba(255,255,255,0.015)',
                    cursor: 'pointer', '&:hover': { background: 'rgba(46,117,182,0.06)' },
                  }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.3 }}>
                      <Typography sx={{ fontSize: 12, fontWeight: 700, color: '#E0E6ED' }}>
                        {privacyMode ? w.id : w.name}
                      </Typography>
                      <Chip label={statusLabel} size="small" sx={{ height: 14, fontSize: 8, background: `${statusColor}22`, color: statusColor, '& .MuiChip-label': { px: 0.5 } }} />
                    </Box>
                    <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>{w.role} · {w.zone}</Typography>
                    {privacyMode ? (
                      <Typography sx={{ fontSize: 9, color: statusColor, mt: 0.3 }}>
                        AI: {statusLabel} | 피로: {w.fatigue > 70 ? '높음' : w.fatigue > 40 ? '보통' : '낮음'}
                      </Typography>
                    ) : (
                      <Box sx={{ display: 'flex', gap: 1, mt: 0.3, fontSize: 9, color: '#7A8FA3' }}>
                        <span style={{ color: w.heartRate > 100 ? '#FF9800' : undefined }}>💓{w.heartRate}</span>
                        <span>🌡{w.bodyTemp}°</span>
                        <span style={{ color: w.spo2 < 95 ? '#FF9800' : undefined }}>🫁{w.spo2}%</span>
                      </Box>
                    )}
                    {w.alerts.length > 0 && (
                      <Typography sx={{ fontSize: 8, color: statusColor, mt: 0.3 }} noWrap>{w.alerts[0]}</Typography>
                    )}
                  </Box>
                );
              })}
            </Box>
          </CardContent>
        </Card>
      </Box>

      {/* ═══ 4행: 공공데이터 + 시나리오 + 알림 ═══ */}
      <Box sx={{ display: 'flex', gap: 1 }}>
        {/* 공공데이터 상세 */}
        <Card sx={{ flex: 1 }}>
          <CardContent sx={{ py: 1, '&:last-child': { pb: 1 } }}>
            <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 0.8, fontSize: 12 }}>
              공공데이터 (실시간)
            </Typography>
            <Grid container spacing={1}>
              {/* 대기질 */}
              <Grid item xs={4}>
                <Box sx={{ p: 1, background: 'rgba(255,255,255,0.02)', borderRadius: 1.5 }}>
                  <Typography sx={{ fontSize: 9, color: '#5A7A96', mb: 0.3 }}>🌫 대기질</Typography>
                  {[
                    { label: 'PM10', value: airQuality?.pm10, unit: '㎍/㎥' },
                    { label: 'PM2.5', value: airQuality?.pm25, unit: '㎍/㎥' },
                    { label: 'O₃', value: airQuality?.o3, unit: 'ppm' },
                  ].map((d) => (
                    <Box key={d.label} sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.2 }}>
                      <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>{d.label}</Typography>
                      <Typography sx={{ fontSize: 10, fontWeight: 600, color: '#E0E6ED' }}>{d.value ?? '-'} <span style={{ fontSize: 8, color: '#5A7A96' }}>{d.unit}</span></Typography>
                    </Box>
                  ))}
                  <Typography sx={{ fontSize: 8, color: '#4A6278', mt: 0.3, fontStyle: 'italic' }}>출처: data.go.kr</Typography>
                </Box>
              </Grid>
              {/* 기상 */}
              <Grid item xs={4}>
                <Box sx={{ p: 1, background: 'rgba(255,255,255,0.02)', borderRadius: 1.5 }}>
                  <Typography sx={{ fontSize: 9, color: '#5A7A96', mb: 0.3 }}>🌡 기상</Typography>
                  {[
                    { label: '기온', value: weather?.temp ? `${weather.temp}°C` : '-' },
                    { label: '체감', value: weather?.feelsLike ? `${weather.feelsLike}°C` : '-' },
                    { label: '풍속', value: weather?.windSpeed ? `${weather.windSpeed}m/s` : '-' },
                    { label: '습도', value: weather?.humidity ? `${weather.humidity}%` : '-' },
                  ].map((d) => (
                    <Box key={d.label} sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.2 }}>
                      <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>{d.label}</Typography>
                      <Typography sx={{ fontSize: 10, fontWeight: 600, color: '#E0E6ED' }}>{d.value}</Typography>
                    </Box>
                  ))}
                </Box>
              </Grid>
              {/* 운항 시간대 */}
              <Grid item xs={4}>
                <Box sx={{ p: 1, background: 'rgba(255,255,255,0.02)', borderRadius: 1.5 }}>
                  <Typography sx={{ fontSize: 9, color: '#5A7A96', mb: 0.3 }}>✈ 운항</Typography>
                  <Box sx={{ display: 'flex', gap: 0.5, height: 40, alignItems: 'flex-end' }}>
                    {(flights?.hourly || []).map((v, i) => {
                      const max = Math.max(...(flights?.hourly || [1]), 1);
                      const h = Math.max(2, (v / max) * 36);
                      const isCurrent = i === flights?.currentHour;
                      return (
                        <Box key={i} sx={{
                          flex: 1, height: h, borderRadius: '2px 2px 0 0', minHeight: 2,
                          background: isCurrent ? '#FF9800' : '#2E75B6',
                          opacity: isCurrent ? 1 : 0.5,
                        }} />
                      );
                    })}
                  </Box>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.3 }}>
                    <Typography sx={{ fontSize: 8, color: '#5A7A96' }}>00</Typography>
                    <Typography sx={{ fontSize: 8, color: '#5A7A96' }}>12</Typography>
                    <Typography sx={{ fontSize: 8, color: '#5A7A96' }}>24</Typography>
                  </Box>
                </Box>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        {/* 시나리오 시연 */}
        <Box sx={{ flex: '0 0 25%' }}>
          <ScenarioMini />
        </Box>

        {/* 알림 피드 */}
        <Card sx={{ flex: '0 0 30%' }}>
          <CardContent sx={{ py: 1, px: 1.5, '&:last-child': { pb: 1 }, display: 'flex', flexDirection: 'column', height: '100%' }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
              <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, fontSize: 12 }}>알림</Typography>
              <Chip size="small" label={`${events.length}`} sx={{ height: 16, fontSize: 9, background: 'rgba(30,136,229,0.12)', color: '#42A5F5' }} />
            </Box>
            <Box sx={{ flex: 1, overflow: 'auto', '&::-webkit-scrollbar': { width: 2 } }}>
              {events.slice(0, 8).map((e) => (
                <Box key={e.id} sx={{ display: 'flex', gap: 0.5, py: 0.4, borderBottom: '1px solid rgba(255,255,255,0.03)', fontSize: 10 }}>
                  <Typography sx={{ fontSize: 9, color: '#5A7A96', minWidth: 45 }}>{e.time}</Typography>
                  <Typography sx={{ fontSize: 10, color: e.level === 'danger' ? '#EF5350' : e.level === 'warning' ? '#FFB74D' : '#42A5F5', flex: 1 }} noWrap>
                    {e.message}
                  </Typography>
                </Box>
              ))}
            </Box>
          </CardContent>
        </Card>
      </Box>

      {/* ═══ 긴급 팝업 (위험 작업자 발생 시 자동) ═══ */}
      <Dialog open={showEmergency && dangerWorkers.length > 0} onClose={() => setShowEmergency(false)}
        maxWidth="lg" fullWidth
        PaperProps={{ sx: { background: '#0A1118', border: '2px solid #E53935', borderRadius: 3, maxHeight: '85vh' } }}>
        <EmergencyOverlay workers={dangerWorkers} allWorkers={workers} onClose={() => setShowEmergency(false)} />
      </Dialog>
    </Box>
  );
}

// 시나리오 미니 패널
import axios from 'axios';
function ScenarioMini() {
  const [active, setActive] = useState<Record<string, boolean>>({});
  const API = import.meta.env.VITE_API_URL || 'http://localhost:4000';

  const scenarios = [
    { key: 'heatwave', label: '폭염', icon: '🔥', color: '#E53935' },
    { key: 'airPollution', label: '대기질', icon: '🌫', color: '#FF9800' },
    { key: 'peakOverwork', label: '피크', icon: '✈', color: '#2196F3' },
    { key: 'collectiveAnomaly', label: '집단이상', icon: '🚨', color: '#D32F2F' },
  ];

  const toggle = async (key: string) => {
    const newState = !active[key];
    try {
      await axios.post(`${API}/api/scenarios/trigger`, { scenario: key, active: newState });
      setActive((p) => ({ ...p, [key]: newState }));
    } catch {}
  };

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ py: 1, '&:last-child': { pb: 1 } }}>
        <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 0.8, fontSize: 12 }}>시나리오 시연</Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 0.5 }}>
          {scenarios.map((s) => (
            <Box key={s.key} onClick={() => toggle(s.key)} sx={{
              p: 0.8, borderRadius: 1, cursor: 'pointer', textAlign: 'center',
              background: active[s.key] ? `${s.color}15` : 'rgba(255,255,255,0.02)',
              border: `1px solid ${active[s.key] ? s.color : '#1E3044'}`,
              '&:hover': { borderColor: s.color },
            }}>
              <Typography sx={{ fontSize: 16 }}>{s.icon}</Typography>
              <Typography sx={{ fontSize: 9, color: active[s.key] ? s.color : '#7A8FA3' }}>{s.label}</Typography>
            </Box>
          ))}
        </Box>
      </CardContent>
    </Card>
  );
}
