import React, { useState, useEffect } from 'react';
import { Box, Card, CardContent, Typography, Grid, TextField, Chip, LinearProgress } from '@mui/material';
import { useWorkerStore } from '../../stores/workerStore';
import { usePrivacyStore } from '../../stores/privacyStore';

const statusConfig: Record<string, { color: string; label: string }> = {
  normal: { color: '#43A047', label: '정상' },
  caution: { color: '#FF9800', label: '주의' },
  danger: { color: '#E53935', label: '위험' },
};

export default function Workers() {
  const workers = useWorkerStore((s) => s.workers);
  const privacyMode = usePrivacyStore((s) => s.privacyMode);
  const [filter, setFilter] = useState('전체');
  const [search, setSearch] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  // 첫 번째 작업자 자동 선택
  useEffect(() => {
    if (!selectedId && workers.length > 0) setSelectedId(workers[0].id);
  }, [workers]);

  const filtered = workers
    .filter((w) => filter === '전체' || statusConfig[w.status]?.label === filter)
    .filter((w) => w.name.includes(search) || w.id.includes(search) || w.zone.includes(search));

  const selected = selectedId ? workers.find((w) => w.id === selectedId) : null;

  // 상태 요약
  const normalCount = workers.filter((w) => w.status === 'normal').length;
  const cautionCount = workers.filter((w) => w.status === 'caution').length;
  const dangerCount = workers.filter((w) => w.status === 'danger').length;

  return (
    <Box sx={{ display: 'flex', gap: 1, height: 'calc(100vh - 76px)', width: '100%' }}>

      {/* ──── 좌측: 작업자 목록 ──── */}
      <Box sx={{ flex: '0 0 22%', minWidth: 220, maxWidth: 280, display: 'flex', flexDirection: 'column', gap: 0.8 }}>
        {/* 상태 요약 + 검색 */}
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          {[
            { label: '전체', count: workers.length, color: '#2E75B6' },
            { label: '정상', count: normalCount, color: '#43A047' },
            { label: '주의', count: cautionCount, color: '#FF9800' },
            { label: '위험', count: dangerCount, color: '#E53935' },
          ].map((s) => (
            <Box key={s.label} onClick={() => setFilter(s.label === filter ? '전체' : s.label)}
              sx={{
                flex: 1, textAlign: 'center', py: 0.5, borderRadius: 1, cursor: 'pointer',
                background: filter === s.label ? `${s.color}20` : 'rgba(255,255,255,0.02)',
                border: `1px solid ${filter === s.label ? s.color : '#1E3044'}`,
                transition: 'all 0.2s',
              }}
            >
              <Typography sx={{ fontSize: 15, fontWeight: 800, color: s.color, lineHeight: 1 }}>{s.count}</Typography>
              <Typography sx={{ fontSize: 8, color: '#7A8FA3' }}>{s.label}</Typography>
            </Box>
          ))}
        </Box>
        <TextField size="small" fullWidth placeholder="🔍 검색" value={search} onChange={(e) => setSearch(e.target.value)}
          sx={{ '& .MuiInputBase-root': { fontSize: 11, height: 32, background: 'rgba(255,255,255,0.03)', borderRadius: 1.5 } }}
        />

        {/* 목록 */}
        <Box sx={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 0.3,
          '&::-webkit-scrollbar': { width: 3 }, '&::-webkit-scrollbar-thumb': { background: '#1E3044', borderRadius: 2 }
        }}>
          {filtered.map((w) => {
            const cfg = statusConfig[w.status];
            const isSelected = selectedId === w.id;
            return (
              <Box key={w.id} onClick={() => setSelectedId(w.id)}
                sx={{
                  p: 0.8, pl: 1.2, borderRadius: 1.5, cursor: 'pointer',
                  borderLeft: `3px solid ${cfg.color}`,
                  background: isSelected ? 'rgba(46,117,182,0.12)' : 'rgba(255,255,255,0.015)',
                  transition: 'all 0.15s',
                  '&:hover': { background: 'rgba(46,117,182,0.06)' },
                }}
              >
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography sx={{ fontSize: 12, fontWeight: 600, color: '#E0E6ED' }}>
                    {privacyMode ? `작업자 ${w.id}` : w.name}
                    {!privacyMode && <Typography component="span" sx={{ fontSize: 9, color: '#5A7A96', ml: 0.5 }}>{w.id}</Typography>}
                  </Typography>
                  <Chip label={cfg.label} size="small" sx={{ height: 14, fontSize: 8, background: `${cfg.color}22`, color: cfg.color, '& .MuiChip-label': { px: 0.5 } }} />
                </Box>
                <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>{w.role} · {w.location}</Typography>
                <Box sx={{ display: 'flex', gap: 1.5, fontSize: 9, color: '#7A8FA3' }}>
                  {privacyMode ? (
                    <>
                      <span style={{ color: cfg.color }}>AI 위험도: {w.status === 'normal' ? '낮음' : w.status === 'caution' ? '주의' : '높음'}</span>
                      <span>피로: {w.fatigue > 70 ? '높음' : w.fatigue > 40 ? '보통' : '낮음'}</span>
                    </>
                  ) : (
                    <>
                      <span style={{ color: w.heartRate > 100 ? '#FF9800' : '#7A8FA3' }}>💓{w.heartRate}</span>
                      <span>🌡{w.bodyTemp}°</span>
                      <span style={{ color: w.spo2 < 95 ? '#FF9800' : '#7A8FA3' }}>🫁{w.spo2}%</span>
                    </>
                  )}
                </Box>
              </Box>
            );
          })}
        </Box>
      </Box>

      {/* ──── 중앙: 바이탈 상세 ──── */}
      {selected ? (
        <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 1.5, overflow: 'auto' }}>
          {/* 헤더 */}
          <Card sx={{ background: `linear-gradient(135deg, ${statusConfig[selected.status].color}10, transparent)` }}>
            <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 }, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Box>
                <Typography sx={{ fontSize: 16, fontWeight: 700, color: '#E0E6ED' }}>
                  {privacyMode ? `작업자 ${selected.id}` : selected.name}
                  <Typography component="span" sx={{ fontSize: 12, color: '#7A8FA3', ml: 1 }}>
                    {privacyMode ? '' : `${selected.id} · `}{selected.role}
                  </Typography>
                </Typography>
                <Typography sx={{ fontSize: 11, color: '#5A7A96' }}>
                  📍 {selected.location} · {selected.floor} · 구역 {selected.zone}
                </Typography>
              </Box>
              <Chip
                label={statusConfig[selected.status].label}
                sx={{
                  fontWeight: 700, fontSize: 12,
                  background: `${statusConfig[selected.status].color}22`,
                  color: statusConfig[selected.status].color,
                  border: `1px solid ${statusConfig[selected.status].color}44`,
                }}
              />
            </CardContent>
          </Card>

          {/* 바이탈 / AI 분석 결과 */}
          {privacyMode ? (
            /* ── 개인정보 보호 모드: AI 분석 결과만 ── */
            <Card sx={{ border: '1px solid rgba(67,160,71,0.2)' }}>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                  <Typography sx={{ fontSize: 16 }}>🔒</Typography>
                  <Typography variant="subtitle2" sx={{ color: '#43A047', fontWeight: 700 }}>개인정보 보호 모드 — AI 분석 결과만 표시</Typography>
                </Box>
                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1.5 }}>
                  {[
                    {
                      label: 'AI 종합 위험도',
                      value: selected.status === 'normal' ? '낮음' : selected.status === 'caution' ? '주의' : '위험',
                      icon: '🛡',
                      color: statusConfig[selected.status].color,
                    },
                    {
                      label: 'AI 피로 예측',
                      value: selected.fatigue > 70 ? '높음' : selected.fatigue > 40 ? '보통' : '낮음',
                      icon: '🔋',
                      color: selected.fatigue > 70 ? '#EF5350' : selected.fatigue > 40 ? '#FFB74D' : '#43A047',
                    },
                    {
                      label: 'AI 권고',
                      value: selected.status === 'danger' ? '즉시 조치' : selected.fatigue > 60 ? '휴식 권고' : '정상 근무',
                      icon: '💡',
                      color: selected.status === 'danger' ? '#EF5350' : selected.fatigue > 60 ? '#FFB74D' : '#43A047',
                    },
                  ].map((item) => (
                    <Card key={item.label} sx={{ background: `${item.color}08`, border: `1px solid ${item.color}22` }}>
                      <CardContent sx={{ py: 2, '&:last-child': { pb: 2 }, textAlign: 'center' }}>
                        <Typography sx={{ fontSize: 28, mb: 0.5 }}>{item.icon}</Typography>
                        <Typography sx={{ fontSize: 20, fontWeight: 800, color: item.color }}>{item.value}</Typography>
                        <Typography sx={{ fontSize: 10, color: '#7A8FA3', mt: 0.5 }}>{item.label}</Typography>
                      </CardContent>
                    </Card>
                  ))}
                </Box>
                <Box sx={{ mt: 2, p: 1.5, background: 'rgba(67,160,71,0.06)', borderRadius: 2, border: '1px solid rgba(67,160,71,0.15)' }}>
                  <Typography sx={{ fontSize: 10, color: '#66BB6A', mb: 0.5, fontWeight: 600 }}>개인정보보호법 제23조 준수</Typography>
                  <Typography sx={{ fontSize: 10, color: '#7A8FA3', lineHeight: 1.6 }}>
                    심박수, 체온, SpO₂ 등 원시 생체 데이터는 웨어러블 기기 내부에서만 처리됩니다.
                    관제센터에는 AI가 분석한 위험도, 피로도, 권고사항만 전달됩니다.
                    긴급 상황 시 권한 인증된 안전관리자만 상세 데이터에 접근할 수 있으며, 접근 이력이 기록됩니다.
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          ) : (
            /* ── 관리자 모드: 원시 데이터 표시 ── */
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1.5 }}>
              {[
                { label: '심박수', value: selected.heartRate, unit: 'bpm', icon: '💓', max: 150, danger: 110, inverse: false },
                { label: '체온', value: selected.bodyTemp, unit: '°C', icon: '🌡', max: 40, danger: 37.5, inverse: false },
                { label: 'SpO₂', value: selected.spo2, unit: '%', icon: '🫁', max: 100, danger: 94, inverse: true },
                { label: '스트레스', value: selected.stress, unit: '', icon: '😰', max: 100, danger: 70, inverse: false },
                { label: 'HRV', value: selected.hrv, unit: 'ms', icon: '📈', max: 100, danger: 30, inverse: true },
                { label: '피로도', value: Math.round(selected.fatigue), unit: '%', icon: '🔋', max: 100, danger: 70, inverse: false },
              ].map((v) => {
                const isAbnormal = v.inverse ? v.value < v.danger : v.value > v.danger;
                const pct = Math.min(100, (v.value / v.max) * 100);
                return (
                  <Card key={v.label} sx={{ border: isAbnormal ? '1px solid rgba(229,57,53,0.3)' : undefined }}>
                    <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 }, textAlign: 'center' }}>
                      <Typography sx={{ fontSize: 22, mb: 0.3 }}>{v.icon}</Typography>
                      <Typography sx={{ fontSize: 26, fontWeight: 800, color: isAbnormal ? '#EF5350' : '#E0E6ED', lineHeight: 1.1 }}>
                        {v.value}
                        <Typography component="span" sx={{ fontSize: 12, color: '#5A7A96', ml: 0.3 }}>{v.unit}</Typography>
                      </Typography>
                      <Typography sx={{ fontSize: 10, color: '#7A8FA3', mb: 0.8 }}>{v.label}</Typography>
                      <LinearProgress
                        variant="determinate" value={pct}
                        sx={{
                          height: 4, borderRadius: 2, background: 'rgba(255,255,255,0.05)',
                          '& .MuiLinearProgress-bar': { borderRadius: 2, background: isAbnormal ? '#EF5350' : pct > 60 ? '#FFB74D' : '#43A047', transition: 'transform 0.8s ease' },
                        }}
                      />
                    </CardContent>
                  </Card>
                );
              })}
            </Box>
          )}

          {/* 하단: AI 분석 + 위치/의료 */}
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5, flex: 1, minHeight: 0 }}>
            {/* AI 분석 */}
            <Card>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 1.5 }}>🧠 AI 분석</Typography>

                {/* 피로도 바 */}
                <Box sx={{ mb: 1.5 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                    <Typography sx={{ fontSize: 11, color: '#7A8FA3' }}>피로도 예측</Typography>
                    <Typography sx={{ fontSize: 11, fontWeight: 700, color: selected.fatigue > 70 ? '#EF5350' : selected.fatigue > 40 ? '#FFB74D' : '#43A047' }}>
                      {selected.fatigue.toFixed(0)}%
                    </Typography>
                  </Box>
                  <LinearProgress
                    variant="determinate" value={selected.fatigue}
                    sx={{ height: 8, borderRadius: 4, background: 'rgba(255,255,255,0.05)', '& .MuiLinearProgress-bar': { borderRadius: 4, background: selected.fatigue > 70 ? '#EF5350' : selected.fatigue > 40 ? '#FFB74D' : '#43A047' } }}
                  />
                  <Typography sx={{ fontSize: 10, color: '#5A7A96', mt: 0.3 }}>
                    {selected.fatigue > 70 ? '⚠ 즉시 휴식 필요' : selected.fatigue > 40 ? '💡 30분 내 휴식 권고' : '✅ 정상 범위'}
                  </Typography>
                </Box>

                {/* 연속 작업 */}
                <Box sx={{ p: 1.2, background: 'rgba(255,255,255,0.02)', borderRadius: 2, mb: 1 }}>
                  <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>연속 작업 시간</Typography>
                  <Typography sx={{ fontSize: 22, fontWeight: 700, color: selected.workMinutes > 240 ? '#EF5350' : '#E0E6ED' }}>
                    {(selected.workMinutes / 60).toFixed(1)}<Typography component="span" sx={{ fontSize: 12, color: '#5A7A96' }}>시간</Typography>
                  </Typography>
                </Box>

                {/* 알림 */}
                {selected.alerts.map((a, i) => (
                  <Box key={i} sx={{ p: 1, background: 'rgba(255,152,0,0.06)', borderRadius: 1.5, mb: 0.5, borderLeft: '3px solid #FF9800' }}>
                    <Typography sx={{ fontSize: 11, color: '#FFB74D' }}>⚠ {a}</Typography>
                  </Box>
                ))}
                {selected.alerts.length === 0 && (
                  <Typography sx={{ fontSize: 11, color: '#43A047', textAlign: 'center', py: 1 }}>✅ 이상 없음</Typography>
                )}
              </CardContent>
            </Card>

            {/* 위치 + 의료이력 */}
            <Card>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 1.5 }}>📍 위치 및 정보</Typography>

                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                  {[
                    { label: '구역', value: selected.zone, icon: '🏢' },
                    { label: '위치', value: selected.location, icon: '📍' },
                    { label: '층', value: selected.floor, icon: '🏗' },
                    { label: '좌표', value: `${selected.lat.toFixed(4)}, ${selected.lng.toFixed(4)}`, icon: '🌐' },
                  ].map((item) => (
                    <Box key={item.label} sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 0.8, background: 'rgba(255,255,255,0.02)', borderRadius: 1.5 }}>
                      <Typography sx={{ fontSize: 16 }}>{item.icon}</Typography>
                      <Box>
                        <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>{item.label}</Typography>
                        <Typography sx={{ fontSize: 12, color: '#E0E6ED', fontWeight: 500 }}>{item.value}</Typography>
                      </Box>
                    </Box>
                  ))}
                </Box>

                {selected.medicalHistory && (
                  <Box sx={{ mt: 1.5, p: 1.2, background: 'rgba(229,57,53,0.06)', borderRadius: 2, border: '1px solid rgba(229,57,53,0.15)' }}>
                    <Typography sx={{ fontSize: 10, color: '#EF5350', fontWeight: 600, mb: 0.3 }}>🏥 의료 이력</Typography>
                    <Typography sx={{ fontSize: 12, color: '#EF9090' }}>{selected.medicalHistory}</Typography>
                  </Box>
                )}
              </CardContent>
            </Card>
          </Box>
        </Box>
      ) : (
        <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Box sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: 48, mb: 1 }}>👷</Typography>
            <Typography sx={{ color: '#5A7A96' }}>좌측에서 작업자를 선택하세요</Typography>
          </Box>
        </Box>
      )}
    </Box>
  );
}
