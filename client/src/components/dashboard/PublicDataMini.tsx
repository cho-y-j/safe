import React from 'react';
import { Card, CardContent, Typography, Box, Chip, Grid } from '@mui/material';
import { useDashboardStore } from '../../stores/dashboardStore';

function gradeColor(grade: string): string {
  switch (grade) {
    case '좋음': return '#66BB6A';
    case '보통': return '#42A5F5';
    case '나쁨': return '#FFB74D';
    case '매우나쁨': return '#EF5350';
    default: return '#7A8FA3';
  }
}

function workloadColor(level: string): string {
  switch (level) {
    case '매우높음': return '#EF5350';
    case '높음': return '#FFB74D';
    case '보통': return '#42A5F5';
    default: return '#66BB6A';
  }
}

export default function PublicDataMini() {
  const { airQuality, weather, flights } = useDashboardStore();

  return (
    <Card>
      <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 1 }}>
          📡 공공데이터 현황 (실시간)
        </Typography>

        <Grid container spacing={1}>
          {/* 대기질 */}
          <Grid item xs={4}>
            <Box sx={{ p: 1, background: 'rgba(255,255,255,0.02)', borderRadius: 2, textAlign: 'center' }}>
              <Typography sx={{ fontSize: 10, color: '#7A8FA3', mb: 0.5 }}>🌫 대기질</Typography>
              <Typography sx={{ fontSize: 18, fontWeight: 700, color: gradeColor(airQuality?.grade || '') }}>
                {airQuality?.pm25 ?? '--'}
              </Typography>
              <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>PM2.5 ㎍/㎥</Typography>
              <Chip
                size="small"
                label={airQuality?.grade || '대기중'}
                sx={{
                  mt: 0.5, height: 18, fontSize: 9,
                  background: `${gradeColor(airQuality?.grade || '')}22`,
                  color: gradeColor(airQuality?.grade || ''),
                }}
              />
            </Box>
          </Grid>

          {/* 기상 */}
          <Grid item xs={4}>
            <Box sx={{ p: 1, background: 'rgba(255,255,255,0.02)', borderRadius: 2, textAlign: 'center' }}>
              <Typography sx={{ fontSize: 10, color: '#7A8FA3', mb: 0.5 }}>🌡 기상</Typography>
              <Typography sx={{ fontSize: 18, fontWeight: 700, color: '#E0E6ED' }}>
                {weather?.temp ?? '--'}°
              </Typography>
              <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>
                체감 {weather?.feelsLike ?? '--'}° | 풍속 {weather?.windSpeed ?? '--'}m/s
              </Typography>
              <Chip
                size="small"
                label={weather?.riskLevel || '대기중'}
                sx={{
                  mt: 0.5, height: 18, fontSize: 9,
                  background: weather?.riskLevel === '위험' ? 'rgba(229,57,53,0.2)' :
                    weather?.riskLevel === '경고' ? 'rgba(255,152,0,0.2)' :
                    weather?.riskLevel === '주의' ? 'rgba(30,136,229,0.2)' : 'rgba(67,160,71,0.2)',
                  color: weather?.riskLevel === '위험' ? '#EF5350' :
                    weather?.riskLevel === '경고' ? '#FFB74D' :
                    weather?.riskLevel === '주의' ? '#42A5F5' : '#66BB6A',
                }}
              />
            </Box>
          </Grid>

          {/* 운항 */}
          <Grid item xs={4}>
            <Box sx={{ p: 1, background: 'rgba(255,255,255,0.02)', borderRadius: 2, textAlign: 'center' }}>
              <Typography sx={{ fontSize: 10, color: '#7A8FA3', mb: 0.5 }}>✈ 운항</Typography>
              <Typography sx={{ fontSize: 18, fontWeight: 700, color: workloadColor(flights?.workloadLevel || '') }}>
                {flights?.total ?? '--'}
              </Typography>
              <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>
                도착 {flights?.arriving ?? '-'} / 출발 {flights?.departing ?? '-'}
              </Typography>
              <Chip
                size="small"
                label={flights?.workloadLevel || '대기중'}
                sx={{
                  mt: 0.5, height: 18, fontSize: 9,
                  background: `${workloadColor(flights?.workloadLevel || '')}22`,
                  color: workloadColor(flights?.workloadLevel || ''),
                }}
              />
            </Box>
          </Grid>
        </Grid>

        <Typography sx={{ fontSize: 9, color: '#4A6278', mt: 1, textAlign: 'right', fontStyle: 'italic' }}>
          출처: data.go.kr 실시간 연동 | {airQuality?.stationName || '인천공항'}
        </Typography>
      </CardContent>
    </Card>
  );
}
