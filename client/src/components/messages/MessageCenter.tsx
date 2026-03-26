import { useState, useEffect } from 'react';
import {
  Box, Card, CardContent, Typography, Grid, TextField, Button, Chip,
  Select, MenuItem, FormControl, InputLabel, Switch, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Dialog, DialogTitle,
  DialogContent, DialogActions,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import axios from 'axios';
import { useWorkerStore } from '../../stores/workerStore';

const API = import.meta.env.VITE_API_URL || 'http://localhost:4000';

const MSG_TYPES = [
  { value: 'rest', label: '🔋 휴식 권고', color: '#43A047' },
  { value: 'shift', label: '🔄 교대 지시', color: '#2196F3' },
  { value: 'safety', label: '⚠ 안전 경고', color: '#FF9800' },
  { value: 'weather', label: '🌡 날씨 알림', color: '#FF9800' },
  { value: 'zone', label: '📍 구역 이동', color: '#2196F3' },
  { value: 'emergency', label: '🚨 긴급', color: '#E53935' },
  { value: 'notice', label: '📢 일반 공지', color: '#7A8FA3' },
];

const AI_AUTO_ALERTS = [
  { key: 'legalRest', label: '법정 휴식 알림 (4시간 근무 → 30분 휴식)', law: '근로기준법 제54조' },
  { key: 'fatigue', label: '피로도 70% 초과 시 자동 휴식 권고' },
  { key: 'overwork', label: '연속 작업 시간 초과 경고' },
  { key: 'heatwave', label: '폭염 33°C↑ 야외 작업자 교대 권고' },
  { key: 'airQuality', label: 'PM2.5 나쁨 → 야외 작업자 마스크 알림' },
  { key: 'congestion', label: '혼잡 예측 시 해당 구역 사전 알림' },
  { key: 'respiratory', label: '호흡기 이력자 + 대기질 악화 → 실내 배치 권고' },
  { key: 'hourlySummary', label: '매시 정각 현황 요약 (근무/이상/피로도)' },
  { key: 'shiftReminder', label: '교대 30분 전 알림' },
  { key: 'nightSleep', label: '야간(22~06) 수면 감지 시 알림 제외', inverted: true },
];

export default function MessageCenter() {
  const workers = useWorkerStore((s) => s.workers);
  const [targetType, setTargetType] = useState<'individual' | 'group' | 'all'>('all');
  const [targetWorker, setTargetWorker] = useState('');
  const [targetGroup, setTargetGroup] = useState<any>({});
  const [msgType, setMsgType] = useState('notice');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [history, setHistory] = useState<any[]>([]);
  const [sending, setSending] = useState(false);
  const [autoAlerts, setAutoAlerts] = useState<Record<string, boolean>>(() => {
    const saved: Record<string, boolean> = {};
    AI_AUTO_ALERTS.forEach((a) => { saved[a.key] = !a.inverted; });
    return saved;
  });

  useEffect(() => {
    loadHistory();
  }, []);

  const loadHistory = async () => {
    try {
      const res = await axios.get(`${API}/api/messages/history`);
      setHistory(res.data);
    } catch {}
  };

  const sendMessage = async () => {
    if (!body.trim()) return;
    setSending(true);
    try {
      const target = targetType === 'individual' ? targetWorker
        : targetType === 'group' ? targetGroup
        : null;

      await axios.post(`${API}/api/messages/send`, {
        target, targetType, messageType: msgType,
        title: title || MSG_TYPES.find((t) => t.value === msgType)?.label || '알림',
        body,
      });

      setTitle('');
      setBody('');
      loadHistory();
    } catch (err) {
      console.error('Send failed:', err);
    }
    setSending(false);
  };

  // 이상 작업자
  const abnormalWorkers = workers.filter((w) => w.status !== 'normal');

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>

      {/* ═══ 메시지 보내기 ═══ */}
      <Card>
        <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
          <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 1.5 }}>
            메시지 보내기
          </Typography>

          <Grid container spacing={1.5}>
            {/* 대상 선택 */}
            <Grid item xs={3}>
              <FormControl size="small" fullWidth>
                <InputLabel sx={{ fontSize: 12 }}>대상</InputLabel>
                <Select value={targetType} label="대상" onChange={(e) => setTargetType(e.target.value as any)} sx={{ fontSize: 12 }}>
                  <MenuItem value="all" sx={{ fontSize: 12 }}>전체 작업자</MenuItem>
                  <MenuItem value="group" sx={{ fontSize: 12 }}>그룹 (필터)</MenuItem>
                  <MenuItem value="individual" sx={{ fontSize: 12 }}>개인</MenuItem>
                </Select>
              </FormControl>
            </Grid>

            {targetType === 'individual' && (
              <Grid item xs={3}>
                <FormControl size="small" fullWidth>
                  <InputLabel sx={{ fontSize: 12 }}>작업자</InputLabel>
                  <Select value={targetWorker} label="작업자" onChange={(e) => setTargetWorker(e.target.value)} sx={{ fontSize: 12 }}>
                    {abnormalWorkers.length > 0 && <MenuItem disabled sx={{ fontSize: 10, color: '#FF9800' }}>⚠ 이상자</MenuItem>}
                    {abnormalWorkers.map((w) => (
                      <MenuItem key={w.id} value={w.id} sx={{ fontSize: 12 }}>
                        {w.name} ({w.id}) — {w.status === 'danger' ? '🚨위험' : '⚠주의'}
                      </MenuItem>
                    ))}
                    {abnormalWorkers.length > 0 && <MenuItem disabled sx={{ fontSize: 10 }}>── 전체 ──</MenuItem>}
                    {workers.filter((w) => w.status === 'normal').map((w) => (
                      <MenuItem key={w.id} value={w.id} sx={{ fontSize: 12 }}>{w.name} ({w.id})</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            )}

            {targetType === 'group' && (
              <>
                <Grid item xs={2}>
                  <FormControl size="small" fullWidth>
                    <InputLabel sx={{ fontSize: 11 }}>구역</InputLabel>
                    <Select value={targetGroup.zone || ''} label="구역" onChange={(e) => setTargetGroup({ ...targetGroup, zone: e.target.value || undefined })} sx={{ fontSize: 11 }}>
                      <MenuItem value="" sx={{ fontSize: 11 }}>전체</MenuItem>
                      {['R2-S', 'T1-B', 'T1-C', 'CB-3', 'CG-1', 'T2-S', 'AP-2', 'CT-1'].map((z) => <MenuItem key={z} value={z} sx={{ fontSize: 11 }}>{z}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={2}>
                  <FormControl size="small" fullWidth>
                    <InputLabel sx={{ fontSize: 11 }}>근무조</InputLabel>
                    <Select value={targetGroup.shiftGroup || ''} label="근무조" onChange={(e) => setTargetGroup({ ...targetGroup, shiftGroup: e.target.value || undefined })} sx={{ fontSize: 11 }}>
                      <MenuItem value="" sx={{ fontSize: 11 }}>전체</MenuItem>
                      {['A조', 'B조', 'C조'].map((s) => <MenuItem key={s} value={s} sx={{ fontSize: 11 }}>{s}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
              </>
            )}

            {/* 유형 */}
            <Grid item xs={targetType === 'group' ? 2 : 3}>
              <FormControl size="small" fullWidth>
                <InputLabel sx={{ fontSize: 12 }}>유형</InputLabel>
                <Select value={msgType} label="유형" onChange={(e) => setMsgType(e.target.value)} sx={{ fontSize: 12 }}>
                  {MSG_TYPES.map((t) => (
                    <MenuItem key={t.value} value={t.value} sx={{ fontSize: 12 }}>{t.label}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>

            <Grid item xs={3}>
              <TextField size="small" fullWidth label="제목 (선택)" value={title} onChange={(e) => setTitle(e.target.value)}
                sx={{ '& .MuiInputBase-root': { fontSize: 12 }, '& .MuiInputLabel-root': { fontSize: 12 } }} />
            </Grid>

            {/* 메시지 입력 */}
            <Grid item xs={9}>
              <TextField size="small" fullWidth placeholder="메시지 내용을 입력하세요" value={body} onChange={(e) => setBody(e.target.value)}
                sx={{ '& .MuiInputBase-root': { fontSize: 12 } }} />
            </Grid>
            <Grid item xs={3}>
              <Button fullWidth variant="contained" startIcon={<SendIcon />} onClick={sendMessage} disabled={sending || !body.trim()}
                sx={{ height: 40, fontSize: 12, textTransform: 'none',
                  background: msgType === 'emergency' ? '#E53935' : '#2E75B6',
                }}>
                {sending ? '전송 중...' : '보내기'}
              </Button>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* ═══ AI 자동 알림 + 발송 이력 ═══ */}
      <Box sx={{ display: 'flex', gap: 1.5 }}>

        {/* AI 자동 알림 설정 */}
        <Card sx={{ flex: '0 0 45%' }}>
          <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
            <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 1 }}>
              AI 자동 알림 설정
            </Typography>
            {AI_AUTO_ALERTS.map((alert) => (
              <Box key={alert.key} sx={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                py: 0.5, borderBottom: '1px solid #1E3044',
              }}>
                <Box>
                  <Typography sx={{ fontSize: 11, color: '#E0E6ED' }}>{alert.label}</Typography>
                  {alert.law && <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>{alert.law}</Typography>}
                </Box>
                <Switch size="small" checked={autoAlerts[alert.key] ?? false}
                  onChange={(e) => setAutoAlerts({ ...autoAlerts, [alert.key]: e.target.checked })}
                  sx={{ '& .MuiSwitch-switchBase.Mui-checked': { color: '#43A047' }, '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': { backgroundColor: '#43A047' } }}
                />
              </Box>
            ))}
          </CardContent>
        </Card>

        {/* 발송 이력 */}
        <Card sx={{ flex: 1 }}>
          <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
              <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700 }}>발송 이력</Typography>
              <Button size="small" onClick={loadHistory} sx={{ fontSize: 10, minWidth: 0 }}>새로고침</Button>
            </Box>
            <TableContainer sx={{ maxHeight: 350 }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    {['시각', '유형', '대상', '내용'].map((h) => (
                      <TableCell key={h} sx={{ background: '#111D29', color: '#7A8FA3', fontSize: 10, py: 0.5 }}>{h}</TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {history.map((msg: any) => {
                    const typeStr = (msg.type || '').replace('msg:', '');
                    const typeInfo = MSG_TYPES.find((t) => t.value === typeStr);
                    return (
                      <TableRow key={msg.id}>
                        <TableCell sx={{ fontSize: 10, color: '#5A7A96', py: 0.5, whiteSpace: 'nowrap' }}>
                          {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : ''}
                        </TableCell>
                        <TableCell sx={{ py: 0.5 }}>
                          <Chip label={typeInfo?.label || typeStr} size="small"
                            sx={{ height: 18, fontSize: 8, background: `${typeInfo?.color || '#7A8FA3'}22`, color: typeInfo?.color || '#7A8FA3' }} />
                        </TableCell>
                        <TableCell sx={{ fontSize: 10, color: '#7A8FA3', py: 0.5 }}>{msg.scenario || '-'}</TableCell>
                        <TableCell sx={{ fontSize: 10, color: '#E0E6ED', py: 0.5, maxWidth: 200 }}>
                          <Typography sx={{ fontSize: 10 }} noWrap>{msg.message}</Typography>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                  {history.length === 0 && (
                    <TableRow><TableCell colSpan={4} sx={{ textAlign: 'center', color: '#5A7A96', fontSize: 11 }}>발송 이력 없음</TableCell></TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>
      </Box>
    </Box>
  );
}
