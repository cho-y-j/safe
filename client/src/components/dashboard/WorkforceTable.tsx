import { Box, Typography } from '@mui/material';
import { useWorkerStore } from '../../stores/workerStore';

const ZONE_LABELS: Record<string, string> = {
  'R2-S': '활주로',
  'T1-B': 'T1 B구역',
  'T1-C': 'T1 C구역',
  'CB-3': '탑승동',
  'CG-1': '화물터미널',
  'T2-S': 'T2 보안',
  'AP-2': '계류장',
  'CT-1': '기내식',
};

export default function WorkforceTable() {
  const workers = useWorkerStore((s) => s.workers);

  // 구역별 집계
  const byZone = Object.entries(ZONE_LABELS).map(([zone, label]) => {
    const zw = workers.filter((w) => w.zone === zone);
    return {
      zone, label,
      total: zw.length,
      normal: zw.filter((w) => w.status === 'normal').length,
      caution: zw.filter((w) => w.status === 'caution').length,
      danger: zw.filter((w) => w.status === 'danger').length,
    };
  }).filter((z) => z.total > 0);

  const totals = {
    total: workers.length,
    normal: workers.filter((w) => w.status === 'normal').length,
    caution: workers.filter((w) => w.status === 'caution').length,
    danger: workers.filter((w) => w.status === 'danger').length,
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 10, color: '#2E75B6', fontWeight: 700, mb: 0.5 }}>구역별 근무 현황</Typography>

      {/* 헤더 */}
      <Box sx={{ display: 'flex', gap: 0.5, mb: 0.3, px: 0.5 }}>
        <Typography sx={{ flex: 1, fontSize: 8, color: '#5A7A96', fontWeight: 600 }}>구역</Typography>
        <Typography sx={{ width: 28, fontSize: 8, color: '#5A7A96', fontWeight: 600, textAlign: 'center' }}>인원</Typography>
        <Typography sx={{ width: 28, fontSize: 8, color: '#43A047', fontWeight: 600, textAlign: 'center' }}>정상</Typography>
        <Typography sx={{ width: 28, fontSize: 8, color: '#FF9800', fontWeight: 600, textAlign: 'center' }}>주의</Typography>
        <Typography sx={{ width: 28, fontSize: 8, color: '#E53935', fontWeight: 600, textAlign: 'center' }}>위험</Typography>
      </Box>

      {/* 행 */}
      {byZone.map((z) => (
        <Box key={z.zone} sx={{
          display: 'flex', gap: 0.5, py: 0.3, px: 0.5, borderRadius: 0.5,
          borderLeft: `2px solid ${z.danger > 0 ? '#E53935' : z.caution > 0 ? '#FF9800' : '#43A047'}`,
          background: z.danger > 0 ? 'rgba(229,57,53,0.05)' : 'rgba(255,255,255,0.01)',
          mb: 0.2,
        }}>
          <Typography sx={{ flex: 1, fontSize: 10, color: '#E0E6ED' }}>{z.label}</Typography>
          <Typography sx={{ width: 28, fontSize: 10, fontWeight: 700, color: '#E0E6ED', textAlign: 'center' }}>{z.total}</Typography>
          <Typography sx={{ width: 28, fontSize: 10, color: '#43A047', textAlign: 'center' }}>{z.normal || '-'}</Typography>
          <Typography sx={{ width: 28, fontSize: 10, color: '#FF9800', textAlign: 'center' }}>{z.caution || '-'}</Typography>
          <Typography sx={{ width: 28, fontSize: 10, color: '#E53935', textAlign: 'center' }}>{z.danger || '-'}</Typography>
        </Box>
      ))}

      {/* 합계 */}
      <Box sx={{ display: 'flex', gap: 0.5, py: 0.5, px: 0.5, mt: 0.3, borderTop: '1px solid #1E3044' }}>
        <Typography sx={{ flex: 1, fontSize: 10, color: '#2E75B6', fontWeight: 700 }}>합계</Typography>
        <Typography sx={{ width: 28, fontSize: 10, fontWeight: 800, color: '#E0E6ED', textAlign: 'center' }}>{totals.total}</Typography>
        <Typography sx={{ width: 28, fontSize: 10, fontWeight: 700, color: '#43A047', textAlign: 'center' }}>{totals.normal}</Typography>
        <Typography sx={{ width: 28, fontSize: 10, fontWeight: 700, color: '#FF9800', textAlign: 'center' }}>{totals.caution}</Typography>
        <Typography sx={{ width: 28, fontSize: 10, fontWeight: 700, color: '#E53935', textAlign: 'center' }}>{totals.danger}</Typography>
      </Box>
    </Box>
  );
}
