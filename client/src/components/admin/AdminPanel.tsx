import { useState, useEffect } from 'react';
import {
  Box, Card, CardContent, Typography, Grid, TextField, Chip, Button,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Dialog, DialogTitle, DialogContent, DialogActions, MenuItem, Select, InputLabel, FormControl,
  IconButton, LinearProgress,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import LinkIcon from '@mui/icons-material/Link';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import EditIcon from '@mui/icons-material/Edit';
import axios from 'axios';

const API = import.meta.env.VITE_API_URL || 'http://localhost:4000';

const COMPANIES = ['전체', '인천국제공항공사', '한국공항', '아시아나에어포트', '대한항공 지상조업', '인천공항보안', 'LSG스카이셰프', '클린에어'];
const SHIFTS = ['전체', 'A조', 'B조', 'C조'];
const ZONES = ['전체', 'R2-S', 'T1-B', 'T1-C', 'CB-3', 'CG-1', 'T2-S', 'AP-2', 'CT-1'];
const ROLES = ['활주로 정비', '수하물 처리', '시설 관리', '화물 처리', '보안 검색', '계류장 유도', '기내식 운반', '청소 관리'];

interface WorkerData {
  id: string; name: string; role: string; zone: string; location: string;
  company: string; department: string; shiftGroup: string; employeeType: string;
  wearableId: string | null; wearableStatus: string; medicalHistory: string | null;
  isActive: boolean; emergencyContact: string | null;
}

interface Stats {
  total: number; active: number; paired: number; wearableRate: number;
  byCompany: { company: string; count: number }[];
}

export default function AdminPanel() {
  const [workers, setWorkers] = useState<WorkerData[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState('');
  const [filterCompany, setFilterCompany] = useState('전체');
  const [filterShift, setFilterShift] = useState('전체');
  const [filterZone, setFilterZone] = useState('전체');
  const [showDialog, setShowDialog] = useState(false);
  const [editWorker, setEditWorker] = useState<Partial<WorkerData> | null>(null);
  const [pairDialog, setPairDialog] = useState<string | null>(null);
  const [pairDeviceId, setPairDeviceId] = useState('');

  const fetchWorkers = async () => {
    const params: any = { limit: 100 };
    if (filterCompany !== '전체') params.company = filterCompany;
    if (filterShift !== '전체') params.shiftGroup = filterShift;
    if (filterZone !== '전체') params.zone = filterZone;
    if (search) params.search = search;
    const res = await axios.get(`${API}/api/admin/workers`, { params });
    setWorkers(res.data.workers);
    setTotal(res.data.total);
  };

  const fetchStats = async () => {
    const res = await axios.get(`${API}/api/admin/stats`);
    setStats(res.data);
  };

  useEffect(() => { fetchWorkers(); fetchStats(); }, [filterCompany, filterShift, filterZone, search]);

  const handleSave = async () => {
    if (!editWorker) return;
    if (editWorker.id && workers.find(w => w.id === editWorker.id)) {
      await axios.put(`${API}/api/admin/workers/${editWorker.id}`, editWorker);
    } else {
      await axios.post(`${API}/api/admin/workers`, editWorker);
    }
    setShowDialog(false);
    setEditWorker(null);
    fetchWorkers();
    fetchStats();
  };

  const handlePair = async () => {
    if (!pairDialog || !pairDeviceId) return;
    await axios.post(`${API}/api/admin/workers/${pairDialog}/pair`, { wearableId: pairDeviceId });
    setPairDialog(null);
    setPairDeviceId('');
    fetchWorkers();
    fetchStats();
  };

  const handleUnpair = async (id: string) => {
    await axios.post(`${API}/api/admin/workers/${id}/unpair`);
    fetchWorkers();
    fetchStats();
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      {/* 통계 요약 */}
      <Grid container spacing={1.5}>
        {[
          { label: '전체 등록', value: stats?.total || 0, color: '#2E75B6' },
          { label: '금일 근무', value: stats?.active || 0, color: '#43A047' },
          { label: '웨어러블 착용', value: stats?.paired || 0, color: '#FF9800' },
          { label: '착용률', value: `${stats?.wearableRate || 0}%`, color: stats?.wearableRate && stats.wearableRate >= 80 ? '#43A047' : '#FF9800' },
        ].map((s) => (
          <Grid item xs={3} key={s.label}>
            <Card>
              <CardContent sx={{ py: 1.5, textAlign: 'center', '&:last-child': { pb: 1.5 } }}>
                <Typography sx={{ fontSize: 10, color: '#7A8FA3' }}>{s.label}</Typography>
                <Typography sx={{ fontSize: 28, fontWeight: 800, color: s.color }}>{s.value}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* 소속사별 분포 */}
      {stats?.byCompany && (
        <Card>
          <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
            <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700, mb: 1 }}>소속사별 인력 현황</Typography>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {stats.byCompany.map((c) => (
                <Chip key={c.company} label={`${c.company} (${c.count})`} size="small"
                  onClick={() => setFilterCompany(c.company)}
                  sx={{ fontSize: 11, background: filterCompany === c.company ? 'rgba(46,117,182,0.2)' : 'rgba(255,255,255,0.03)', color: filterCompany === c.company ? '#2E75B6' : '#7A8FA3' }}
                />
              ))}
            </Box>
          </CardContent>
        </Card>
      )}

      {/* 필터 + 검색 + 등록 버튼 */}
      <Card>
        <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <TextField size="small" placeholder="이름/ID/구역 검색" value={search}
              onChange={(e) => setSearch(e.target.value)}
              sx={{ width: 200, '& .MuiInputBase-root': { fontSize: 12, height: 34 } }}
            />
            <FormControl size="small" sx={{ minWidth: 120 }}>
              <Select value={filterCompany} onChange={(e) => setFilterCompany(e.target.value)} sx={{ fontSize: 12, height: 34 }}>
                {COMPANIES.map((c) => <MenuItem key={c} value={c} sx={{ fontSize: 12 }}>{c}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 80 }}>
              <Select value={filterShift} onChange={(e) => setFilterShift(e.target.value)} sx={{ fontSize: 12, height: 34 }}>
                {SHIFTS.map((s) => <MenuItem key={s} value={s} sx={{ fontSize: 12 }}>{s}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 100 }}>
              <Select value={filterZone} onChange={(e) => setFilterZone(e.target.value)} sx={{ fontSize: 12, height: 34 }}>
                {ZONES.map((z) => <MenuItem key={z} value={z} sx={{ fontSize: 12 }}>{z}</MenuItem>)}
              </Select>
            </FormControl>
            <Box sx={{ flex: 1 }} />
            <Typography sx={{ fontSize: 11, color: '#5A7A96' }}>{total}명</Typography>
            <Button size="small" variant="contained" startIcon={<AddIcon />}
              onClick={() => { setEditWorker({ id: `W-${String(total + 1).padStart(3, '0')}`, company: '인천국제공항공사', shiftGroup: 'A조', employeeType: '정규직', wearableStatus: 'unpaired' }); setShowDialog(true); }}
              sx={{ fontSize: 11, textTransform: 'none' }}>
              작업자 등록
            </Button>
          </Box>
        </CardContent>
      </Card>

      {/* 작업자 테이블 */}
      <Card>
        <TableContainer sx={{ maxHeight: 'calc(100vh - 380px)' }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                {['ID', '이름', '소속사', '직무', '구역', '근무조', '웨어러블', '상태', '관리'].map((h) => (
                  <TableCell key={h} sx={{ background: '#111D29', color: '#7A8FA3', fontSize: 11, fontWeight: 600, py: 1 }}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {workers.map((w) => (
                <TableRow key={w.id} hover sx={{ '&:hover': { background: 'rgba(46,117,182,0.05)' } }}>
                  <TableCell sx={{ fontSize: 11, color: '#5A7A96' }}>{w.id}</TableCell>
                  <TableCell sx={{ fontSize: 12, fontWeight: 600, color: '#E0E6ED' }}>{w.name}</TableCell>
                  <TableCell sx={{ fontSize: 11, color: '#7A8FA3' }}>{w.company}</TableCell>
                  <TableCell sx={{ fontSize: 11, color: '#7A8FA3' }}>{w.role}</TableCell>
                  <TableCell><Chip label={w.zone} size="small" sx={{ height: 18, fontSize: 9, background: 'rgba(46,117,182,0.12)', color: '#5CA0E0' }} /></TableCell>
                  <TableCell><Chip label={w.shiftGroup} size="small" sx={{ height: 18, fontSize: 9 }} /></TableCell>
                  <TableCell>
                    {w.wearableId ? (
                      <Chip label={`${w.wearableId}`} size="small" icon={<LinkIcon sx={{ fontSize: 12 }} />}
                        sx={{ height: 20, fontSize: 9, background: 'rgba(67,160,71,0.12)', color: '#66BB6A' }} />
                    ) : (
                      <Chip label="미연결" size="small" sx={{ height: 18, fontSize: 9, background: 'rgba(255,152,0,0.12)', color: '#FFB74D' }} />
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip label={w.isActive ? '근무중' : '미근무'} size="small"
                      sx={{ height: 18, fontSize: 9, background: w.isActive ? 'rgba(67,160,71,0.12)' : 'rgba(255,255,255,0.05)', color: w.isActive ? '#66BB6A' : '#5A7A96' }} />
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.3 }}>
                      <IconButton size="small" onClick={() => { setEditWorker(w); setShowDialog(true); }} sx={{ color: '#5A7A96', p: 0.3 }}><EditIcon fontSize="small" /></IconButton>
                      {w.wearableId ? (
                        <IconButton size="small" onClick={() => handleUnpair(w.id)} sx={{ color: '#EF5350', p: 0.3 }}><LinkOffIcon fontSize="small" /></IconButton>
                      ) : (
                        <IconButton size="small" onClick={() => { setPairDialog(w.id); setPairDeviceId(''); }} sx={{ color: '#43A047', p: 0.3 }}><LinkIcon fontSize="small" /></IconButton>
                      )}
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* 등록/수정 다이얼로그 */}
      <Dialog open={showDialog} onClose={() => setShowDialog(false)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { background: '#111D29', border: '1px solid #1E3044' } }}>
        <DialogTitle sx={{ fontSize: 16, color: '#2E75B6' }}>
          {editWorker?.id && workers.find(w => w.id === editWorker.id) ? '작업자 수정' : '작업자 등록'}
        </DialogTitle>
        <DialogContent>
          <Grid container spacing={1.5} sx={{ mt: 0.5 }}>
            {[
              { key: 'id', label: 'ID', placeholder: 'W-009' },
              { key: 'name', label: '이름', placeholder: '홍길동' },
              { key: 'emergencyContact', label: '비상 연락처', placeholder: '010-0000-0000' },
            ].map((f) => (
              <Grid item xs={4} key={f.key}>
                <TextField size="small" fullWidth label={f.label} placeholder={f.placeholder}
                  value={(editWorker as any)?.[f.key] || ''}
                  onChange={(e) => setEditWorker({ ...editWorker, [f.key]: e.target.value })}
                  sx={{ '& .MuiInputBase-root': { fontSize: 12 }, '& .MuiInputLabel-root': { fontSize: 12 } }}
                />
              </Grid>
            ))}
            <Grid item xs={4}>
              <FormControl size="small" fullWidth>
                <InputLabel sx={{ fontSize: 12 }}>소속사</InputLabel>
                <Select value={editWorker?.company || ''} label="소속사"
                  onChange={(e) => setEditWorker({ ...editWorker, company: e.target.value })} sx={{ fontSize: 12 }}>
                  {COMPANIES.filter(c => c !== '전체').map((c) => <MenuItem key={c} value={c} sx={{ fontSize: 12 }}>{c}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={4}>
              <FormControl size="small" fullWidth>
                <InputLabel sx={{ fontSize: 12 }}>직무</InputLabel>
                <Select value={editWorker?.role || ''} label="직무"
                  onChange={(e) => setEditWorker({ ...editWorker, role: e.target.value })} sx={{ fontSize: 12 }}>
                  {ROLES.map((r) => <MenuItem key={r} value={r} sx={{ fontSize: 12 }}>{r}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={4}>
              <FormControl size="small" fullWidth>
                <InputLabel sx={{ fontSize: 12 }}>근무조</InputLabel>
                <Select value={editWorker?.shiftGroup || ''} label="근무조"
                  onChange={(e) => setEditWorker({ ...editWorker, shiftGroup: e.target.value })} sx={{ fontSize: 12 }}>
                  {['A조', 'B조', 'C조'].map((s) => <MenuItem key={s} value={s} sx={{ fontSize: 12 }}>{s}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={4}>
              <FormControl size="small" fullWidth>
                <InputLabel sx={{ fontSize: 12 }}>구역</InputLabel>
                <Select value={editWorker?.zone || ''} label="구역"
                  onChange={(e) => setEditWorker({ ...editWorker, zone: e.target.value })} sx={{ fontSize: 12 }}>
                  {ZONES.filter(z => z !== '전체').map((z) => <MenuItem key={z} value={z} sx={{ fontSize: 12 }}>{z}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={8}>
              <TextField size="small" fullWidth label="의료 이력" placeholder="(선택) 호흡기 질환, 고혈압 등"
                value={editWorker?.medicalHistory || ''}
                onChange={(e) => setEditWorker({ ...editWorker, medicalHistory: e.target.value })}
                sx={{ '& .MuiInputBase-root': { fontSize: 12 }, '& .MuiInputLabel-root': { fontSize: 12 } }}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowDialog(false)} sx={{ fontSize: 12 }}>취소</Button>
          <Button onClick={handleSave} variant="contained" sx={{ fontSize: 12 }}>저장</Button>
        </DialogActions>
      </Dialog>

      {/* 웨어러블 페어링 다이얼로그 */}
      <Dialog open={!!pairDialog} onClose={() => setPairDialog(null)}
        PaperProps={{ sx: { background: '#111D29', border: '1px solid #1E3044' } }}>
        <DialogTitle sx={{ fontSize: 14, color: '#43A047' }}>웨어러블 페어링</DialogTitle>
        <DialogContent>
          <Typography sx={{ fontSize: 12, color: '#7A8FA3', mb: 2 }}>
            Galaxy Watch 기기 ID를 입력하세요. 페어링 후 자동으로 베이스라인 학습이 시작됩니다.
          </Typography>
          <TextField size="small" fullWidth label="기기 ID" placeholder="GW4-009"
            value={pairDeviceId} onChange={(e) => setPairDeviceId(e.target.value)}
            sx={{ '& .MuiInputBase-root': { fontSize: 12 } }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPairDialog(null)} sx={{ fontSize: 12 }}>취소</Button>
          <Button onClick={handlePair} variant="contained" color="success" sx={{ fontSize: 12 }}>페어링</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
