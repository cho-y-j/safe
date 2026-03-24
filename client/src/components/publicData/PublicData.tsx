import React from 'react';
import { Box, Card, CardContent, Typography, Grid, Chip } from '@mui/material';
import { useDashboardStore } from '../../stores/dashboardStore';
import { BarChart, Bar, Cell, LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, RadarChart, PolarGrid, PolarAngleAxis, Radar } from 'recharts';

const airItems = [
  { key: 'pm10', label: 'PM10 미세먼지', unit: '㎍/㎥', thresholds: [30, 80, 150] },
  { key: 'pm25', label: 'PM2.5 초미세먼지', unit: '㎍/㎥', thresholds: [15, 35, 75] },
  { key: 'o3', label: 'O₃ 오존', unit: 'ppm', thresholds: [0.03, 0.09, 0.15] },
  { key: 'no2', label: 'NO₂ 이산화질소', unit: 'ppm', thresholds: [0.03, 0.06, 0.2] },
  { key: 'co', label: 'CO 일산화탄소', unit: 'ppm', thresholds: [2, 9, 15] },
  { key: 'so2', label: 'SO₂ 아황산가스', unit: 'ppm', thresholds: [0.02, 0.05, 0.15] },
];

function gradeValue(val: number, thresholds: number[]) {
  if (val >= thresholds[2]) return { label: '매우나쁨', color: '#EF5350' };
  if (val >= thresholds[1]) return { label: '나쁨', color: '#FFB74D' };
  if (val >= thresholds[0]) return { label: '보통', color: '#42A5F5' };
  return { label: '좋음', color: '#66BB6A' };
}

export default function PublicData() {
  const { airQuality, weather, flights, forecast } = useDashboardStore();

  const hourlyData = flights?.hourly.map((v, i) => ({
    hour: `${String(i).padStart(2, '0')}시`,
    flights: v,
    isCurrent: i === flights.currentHour,
  })) || [];

  const forecastData = forecast?.hourlyForecast.map((f) => ({
    hour: `${String(f.hour).padStart(2, '0')}시`,
    passengers: f.passengers,
    congestion: f.congestionLevel,
  })) || [];

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pb: 2 }}>
      {/* 대기질 */}
      <Card>
        <CardContent>
          <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 2 }}>
            🌫 인천공항 실외 대기질 (실시간)
          </Typography>
          <Grid container spacing={1.5}>
            {airItems.map((item) => {
              const val = airQuality ? (airQuality as any)[item.key] : 0;
              const grade = gradeValue(val, item.thresholds);
              return (
                <Grid item xs={4} sm={2} key={item.key}>
                  <Box sx={{ p: 1.5, background: 'rgba(255,255,255,0.02)', borderRadius: 2, textAlign: 'center' }}>
                    <Typography sx={{ fontSize: 10, color: '#7A8FA3', mb: 0.5 }}>{item.label}</Typography>
                    <Typography sx={{ fontSize: 24, fontWeight: 700, color: grade.color }}>{val}</Typography>
                    <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>{item.unit}</Typography>
                    <Chip label={grade.label} size="small" sx={{ mt: 0.5, height: 18, fontSize: 9, background: `${grade.color}22`, color: grade.color }} />
                  </Box>
                </Grid>
              );
            })}
          </Grid>
          <Typography sx={{ fontSize: 9, color: '#4A6278', mt: 1, textAlign: 'right', fontStyle: 'italic' }}>
            출처: 인천국제공항공사_실외대기질 (data.go.kr) | 측정소: {airQuality?.stationName || '-'}
          </Typography>
        </CardContent>
      </Card>

      {/* 기상 + 운항 */}
      <Grid container spacing={2}>
        <Grid item xs={12} md={5}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 2 }}>🌡 기상 분석</Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, mb: 2 }}>
                <Typography sx={{ fontSize: 48, fontWeight: 700, color: '#E0E6ED' }}>
                  {weather?.temp ?? '--'}<Typography component="span" sx={{ fontSize: 20, color: '#5A7A96' }}>°C</Typography>
                </Typography>
                <Box>
                  <Typography sx={{ fontSize: 12, color: '#7A8FA3' }}>체감온도 {weather?.feelsLike ?? '--'}°C</Typography>
                  <Typography sx={{ fontSize: 12, color: '#7A8FA3' }}>습도 {weather?.humidity ?? '--'}%</Typography>
                  <Typography sx={{ fontSize: 12, color: '#7A8FA3' }}>풍속 {weather?.windSpeed ?? '--'} m/s ({weather?.windDir ?? '-'})</Typography>
                  <Typography sx={{ fontSize: 12, color: '#7A8FA3' }}>기압 {weather?.pressure ?? '--'} hPa</Typography>
                  <Typography sx={{ fontSize: 12, color: '#7A8FA3' }}>시정 {weather?.visibility ?? '--'} km</Typography>
                </Box>
              </Box>
              <Chip label={`위험 수준: ${weather?.riskLevel || '-'}`} sx={{
                background: weather?.riskLevel === '위험' ? 'rgba(229,57,53,0.15)' : weather?.riskLevel === '경고' ? 'rgba(255,152,0,0.15)' : 'rgba(67,160,71,0.15)',
                color: weather?.riskLevel === '위험' ? '#EF5350' : weather?.riskLevel === '경고' ? '#FFB74D' : '#66BB6A',
              }} />
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={7}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 2 }}>✈ 시간대별 운항 현황</Typography>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={hourlyData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1E3044" />
                  <XAxis dataKey="hour" tick={{ fontSize: 9, fill: '#5A7A96' }} interval={2} />
                  <YAxis tick={{ fontSize: 9, fill: '#5A7A96' }} />
                  <Tooltip contentStyle={{ background: '#111D29', border: '1px solid #1E3044', borderRadius: 8, fontSize: 11 }} />
                  <Bar dataKey="flights" fill="#2E75B6" radius={[3, 3, 0, 0]}>
                    {hourlyData.map((entry, i) => (
                      <Cell key={i} fill={entry.isCurrent ? '#FF9800' : '#2E75B6'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
              <Typography sx={{ fontSize: 9, color: '#4A6278', mt: 1, textAlign: 'right', fontStyle: 'italic' }}>
                출처: 인천국제공항공사_여객편 운항현황 (data.go.kr) | 현재 시간 주황색
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* AI 융합 위험 예측 */}
      <Card>
        <CardContent>
          <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 2 }}>
            🧠 AI 융합 위험 예측 (공공데이터 3종 결합)
          </Typography>
          <Box sx={{ p: 2, background: 'rgba(255,255,255,0.02)', borderRadius: 2 }}>
            <Typography sx={{ fontSize: 13, color: '#E0E6ED', mb: 1 }}>
              현재 상황: 기온 {weather?.temp}°C, PM2.5 {airQuality?.pm25}㎍/㎥, 운항 {flights?.total}편
            </Typography>
            {weather && weather.temp >= 30 && (
              <Box sx={{ p: 1, background: 'rgba(229,57,53,0.08)', borderRadius: 1, mb: 1 }}>
                <Typography sx={{ fontSize: 11, color: '#EF5350' }}>
                  🔥 고온 + 운항 {flights?.total}편 → 야외 작업자 교대주기 단축 권고
                </Typography>
              </Box>
            )}
            {airQuality && airQuality.pm25 >= 35 && (
              <Box sx={{ p: 1, background: 'rgba(255,152,0,0.08)', borderRadius: 1, mb: 1 }}>
                <Typography sx={{ fontSize: 11, color: '#FFB74D' }}>
                  🌫 PM2.5 나쁨 → 전체 야외 작업자 마스크 착용 필요
                </Typography>
              </Box>
            )}
            {flights && flights.total >= 30 && (
              <Box sx={{ p: 1, background: 'rgba(30,136,229,0.08)', borderRadius: 1, mb: 1 }}>
                <Typography sx={{ fontSize: 11, color: '#42A5F5' }}>
                  ✈ 피크 시간대 → 수하물/화물 구역 인력 재배치 권고
                </Typography>
              </Box>
            )}
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
