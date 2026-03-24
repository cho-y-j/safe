import React, { useState } from 'react';
import { Box, Card, CardContent, Typography, Grid, Chip, Button, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
import { useAlertStore } from '../../stores/alertStore';
import { useWorkerStore } from '../../stores/workerStore';
import { BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';

const COLORS = ['#43A047', '#42A5F5', '#FF9800', '#E53935'];

export default function Reports() {
  const events = useAlertStore((s) => s.events);
  const workers = useWorkerStore((s) => s.workers);
  const [period, setPeriod] = useState<'일간' | '주간' | '월간'>('일간');

  const levelCounts = {
    info: events.filter((e) => e.level === 'info').length,
    caution: events.filter((e) => e.level === 'caution').length,
    warning: events.filter((e) => e.level === 'warning').length,
    danger: events.filter((e) => e.level === 'danger').length,
  };

  const pieData = [
    { name: '정보', value: levelCounts.info, color: '#42A5F5' },
    { name: '주의', value: levelCounts.caution, color: '#42A5F5' },
    { name: '경고', value: levelCounts.warning, color: '#FF9800' },
    { name: '위험', value: levelCounts.danger, color: '#E53935' },
  ].filter((d) => d.value > 0);

  const workerStatusData = [
    { name: '정상', count: workers.filter((w) => w.status === 'normal').length },
    { name: '주의', count: workers.filter((w) => w.status === 'caution').length },
    { name: '위험', count: workers.filter((w) => w.status === 'danger').length },
  ];

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {/* 기간 선택 */}
      <Box sx={{ display: 'flex', gap: 1 }}>
        {(['일간', '주간', '월간'] as const).map((p) => (
          <Chip key={p} label={p} onClick={() => setPeriod(p)}
            sx={{
              cursor: 'pointer',
              background: period === p ? 'rgba(46,117,182,0.2)' : 'rgba(255,255,255,0.03)',
              color: period === p ? '#2E75B6' : '#7A8FA3',
              border: period === p ? '1px solid #2E75B6' : '1px solid transparent',
            }}
          />
        ))}
        <Box sx={{ flex: 1 }} />
        <Button variant="outlined" size="small" sx={{ fontSize: 11, borderColor: '#2E75B6', color: '#2E75B6' }}>
          📄 PDF 리포트 생성
        </Button>
        <Button variant="outlined" size="small" sx={{ fontSize: 11, borderColor: '#43A047', color: '#43A047' }}>
          📊 Excel 다운로드
        </Button>
      </Box>

      {/* 요약 카드 */}
      <Grid container spacing={2}>
        <Grid item xs={3}>
          <Card><CardContent sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>총 이벤트</Typography>
            <Typography sx={{ fontSize: 32, fontWeight: 700, color: '#2E75B6' }}>{events.length}</Typography>
          </CardContent></Card>
        </Grid>
        <Grid item xs={3}>
          <Card><CardContent sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>위험 이벤트</Typography>
            <Typography sx={{ fontSize: 32, fontWeight: 700, color: '#E53935' }}>{levelCounts.danger}</Typography>
          </CardContent></Card>
        </Grid>
        <Grid item xs={3}>
          <Card><CardContent sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>경고 이벤트</Typography>
            <Typography sx={{ fontSize: 32, fontWeight: 700, color: '#FF9800' }}>{levelCounts.warning}</Typography>
          </CardContent></Card>
        </Grid>
        <Grid item xs={3}>
          <Card><CardContent sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>현재 작업자</Typography>
            <Typography sx={{ fontSize: 32, fontWeight: 700, color: '#43A047' }}>{workers.length}명</Typography>
          </CardContent></Card>
        </Grid>
      </Grid>

      {/* 차트 */}
      <Grid container spacing={2}>
        <Grid item xs={7}>
          <Card><CardContent>
            <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 2 }}>작업자 상태 현황</Typography>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={workerStatusData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1E3044" />
                <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#7A8FA3' }} />
                <YAxis tick={{ fontSize: 11, fill: '#7A8FA3' }} />
                <Tooltip contentStyle={{ background: '#111D29', border: '1px solid #1E3044', borderRadius: 8 }} />
                <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                  {workerStatusData.map((_, i) => (
                    <Cell key={i} fill={COLORS[i === 0 ? 0 : i === 1 ? 2 : 3]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </CardContent></Card>
        </Grid>
        <Grid item xs={5}>
          <Card><CardContent>
            <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 2 }}>이벤트 레벨 분포</Typography>
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie data={pieData} cx="50%" cy="50%" outerRadius={70} dataKey="value" label={({ name, value }) => `${name} ${value}`}>
                  {pieData.map((entry, i) => (
                    <Cell key={i} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </CardContent></Card>
        </Grid>
      </Grid>

      {/* 이벤트 로그 테이블 */}
      <Card>
        <CardContent>
          <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 2 }}>📋 이벤트 이력</Typography>
          <TableContainer sx={{ maxHeight: 300 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ background: '#111D29', color: '#7A8FA3', fontSize: 11 }}>시각</TableCell>
                  <TableCell sx={{ background: '#111D29', color: '#7A8FA3', fontSize: 11 }}>레벨</TableCell>
                  <TableCell sx={{ background: '#111D29', color: '#7A8FA3', fontSize: 11 }}>내용</TableCell>
                  <TableCell sx={{ background: '#111D29', color: '#7A8FA3', fontSize: 11 }}>구역</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {events.slice(0, 50).map((event) => (
                  <TableRow key={event.id}>
                    <TableCell sx={{ fontSize: 10, color: '#5A7A96' }}>{event.time}</TableCell>
                    <TableCell>
                      <Chip label={event.level} size="small" sx={{
                        height: 18, fontSize: 9,
                        background: event.level === 'danger' ? 'rgba(229,57,53,0.15)' : event.level === 'warning' ? 'rgba(255,152,0,0.15)' : 'rgba(30,136,229,0.15)',
                        color: event.level === 'danger' ? '#EF5350' : event.level === 'warning' ? '#FFB74D' : '#42A5F5',
                      }} />
                    </TableCell>
                    <TableCell sx={{ fontSize: 11, color: '#E0E6ED' }}>{event.message}</TableCell>
                    <TableCell sx={{ fontSize: 10, color: '#5A7A96' }}>{event.zone || '-'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>
    </Box>
  );
}
