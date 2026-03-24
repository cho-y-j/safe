import React, { useEffect } from 'react';
import { Card, CardContent, Typography, Box, LinearProgress } from '@mui/material';
import { useDashboardStore } from '../../stores/dashboardStore';
import { useWorkerStore } from '../../stores/workerStore';
import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:4000';

export default function RiskGauge() {
  const { airQuality, weather, flights, forecast, riskAnalysis, setRiskAnalysis } = useDashboardStore();
  const workers = useWorkerStore((s) => s.workers);

  // AI 엔진에 위험도 분석 요청
  useEffect(() => {
    if (!airQuality || !weather || !flights || workers.length === 0) return;

    const analyze = async () => {
      try {
        const res = await axios.post(`${API_URL}/api/analyze/risk`, {
          publicData: {
            temp: weather.temp,
            feelsLike: weather.feelsLike,
            humidity: weather.humidity,
            windSpeed: weather.windSpeed,
            pm25: airQuality.pm25,
            pm10: airQuality.pm10,
            o3: airQuality.o3,
            co: airQuality.co,
            no2: airQuality.no2,
            so2: airQuality.so2,
            flightTotal: flights.total,
            flightArriving: flights.arriving,
            forecastPassengers: forecast?.hourlyForecast?.[flights.currentHour]?.passengers || 3000,
          },
          workers: workers.map((w) => ({
            id: w.id,
            heartRate: w.heartRate,
            bodyTemp: w.bodyTemp,
            spo2: w.spo2,
            stress: w.stress,
            hrv: w.hrv,
            zone: w.zone,
            workMinutes: w.workMinutes,
            fatigue: w.fatigue,
            medicalHistory: w.medicalHistory,
          })),
        });
        setRiskAnalysis(res.data);
      } catch {
        // 서버 미연결 시 로컬 계산 fallback
        const score = calcLocalRisk();
        setRiskAnalysis(score);
      }
    };

    analyze();
  }, [airQuality?.pm25, weather?.temp, flights?.total, workers.length]);

  function calcLocalRisk() {
    const tempScore = !weather ? 5 : weather.temp >= 35 ? 35 : weather.temp >= 33 ? 28 : weather.temp >= 30 ? 18 : 5;
    const airScore = !airQuality ? 5 : airQuality.pm25 >= 75 ? 30 : airQuality.pm25 >= 35 ? 20 : airQuality.pm25 >= 15 ? 8 : 3;
    const flightScore = !flights ? 5 : flights.total >= 35 ? 20 : flights.total >= 20 ? 12 : 5;
    const maxHR = workers.length > 0 ? Math.max(...workers.map((w) => w.heartRate)) : 70;
    const bioScore = maxHR >= 130 ? 25 : maxHR >= 110 ? 15 : maxHR >= 95 ? 8 : 3;
    const totalScore = Math.min(100, tempScore + airScore + flightScore + bioScore);
    const level = totalScore >= 70 ? '위험' : totalScore >= 50 ? '경고' : totalScore >= 30 ? '주의' : '안전';
    const color = totalScore >= 70 ? '#E53935' : totalScore >= 50 ? '#FF9800' : totalScore >= 30 ? '#42A5F5' : '#66BB6A';

    return {
      totalScore, level, color, action: '',
      breakdown: {
        weather: { score: tempScore, factors: [], category: '기상' },
        airQuality: { score: airScore, factors: [], category: '대기질' },
        flight: { score: flightScore, factors: [], category: '작업강도' },
        bio: { score: bioScore, factors: [], category: '작업자 건강', abnormalCount: 0 },
      },
      scenarios: [],
      allFactors: [],
    };
  }

  const score = riskAnalysis?.totalScore ?? 0;
  const level = riskAnalysis?.level ?? '안전';
  const color = riskAnalysis?.color ?? '#66BB6A';
  const bd = riskAnalysis?.breakdown;

  const factors = [
    { label: '기상', score: bd?.weather.score ?? 0, max: 40, icon: '🌡' },
    { label: '대기질', score: bd?.airQuality.score ?? 0, max: 35, icon: '🌫' },
    { label: '작업강도', score: bd?.flight.score ?? 0, max: 25, icon: '✈' },
    { label: '작업자', score: bd?.bio.score ?? 0, max: 30, icon: '💓' },
  ];

  return (
    <Card>
      <CardContent sx={{ py: 2 }}>
        <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 1.5 }}>
          ⚡ AI 복합 위험도 분석
        </Typography>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, mb: 2 }}>
          {/* 원형 게이지 */}
          <Box sx={{ position: 'relative', width: 100, height: 100 }}>
            <svg width="100" height="100" style={{ transform: 'rotate(-90deg)' }}>
              <circle cx="50" cy="50" r="42" fill="none" stroke="rgba(255,255,255,0.05)" strokeWidth="8" />
              <circle
                cx="50" cy="50" r="42" fill="none"
                stroke={color} strokeWidth="8" strokeLinecap="round"
                strokeDasharray={`${2 * Math.PI * 42}`}
                strokeDashoffset={`${2 * Math.PI * 42 * (1 - score / 100)}`}
                style={{ transition: 'stroke-dashoffset 1s ease' }}
              />
            </svg>
            <Box sx={{
              position: 'absolute', top: '50%', left: '50%',
              transform: 'translate(-50%, -50%)', textAlign: 'center',
            }}>
              <Typography sx={{ fontSize: 22, fontWeight: 800, color, lineHeight: 1 }}>{score}</Typography>
              <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>{level}</Typography>
            </Box>
          </Box>

          {/* 요소별 기여도 */}
          <Box sx={{ flex: 1 }}>
            {factors.map((f) => (
              <Box key={f.label} sx={{ mb: 0.8 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.3 }}>
                  <Typography sx={{ fontSize: 11, color: '#7A8FA3' }}>{f.icon} {f.label}</Typography>
                  <Typography sx={{ fontSize: 11, fontWeight: 700, color: f.score >= f.max * 0.7 ? '#EF5350' : f.score >= f.max * 0.4 ? '#FFB74D' : '#66BB6A' }}>
                    {f.score}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={(f.score / f.max) * 100}
                  sx={{
                    height: 4, borderRadius: 2,
                    background: 'rgba(255,255,255,0.05)',
                    '& .MuiLinearProgress-bar': {
                      borderRadius: 2,
                      background: f.score >= f.max * 0.7 ? '#EF5350' : f.score >= f.max * 0.4 ? '#FFB74D' : '#66BB6A',
                    },
                  }}
                />
              </Box>
            ))}
          </Box>
        </Box>

        {/* 시나리오 경고 */}
        {riskAnalysis?.scenarios && riskAnalysis.scenarios.length > 0 && (
          <Box sx={{ p: 1.5, background: 'rgba(255,152,0,0.08)', borderRadius: 2, border: '1px solid rgba(255,152,0,0.2)' }}>
            {riskAnalysis.scenarios.map((s, i) => (
              <Typography key={i} sx={{ fontSize: 11, color: '#FFB74D', mb: 0.5 }}>
                ⚠ {s.name}: {s.recommendation}
              </Typography>
            ))}
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
