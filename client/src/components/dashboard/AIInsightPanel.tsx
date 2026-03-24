import React from 'react';
import { Card, CardContent, Typography, Box, Chip, Divider } from '@mui/material';
import { useInsightStore } from '../../stores/insightStore';

const levelColors: Record<string, string> = {
  '안전': '#43A047',
  '주의': '#1E88E5',
  '경고': '#FF9800',
  '위험': '#E53935',
};

export default function AIInsightPanel() {
  const insight = useInsightStore((s) => s.insight);

  if (!insight) {
    return (
      <Card sx={{ border: '1px solid #1E3044' }}>
        <CardContent sx={{ py: 2, textAlign: 'center' }}>
          <Typography sx={{ fontSize: 20, mb: 1 }}>🧠</Typography>
          <Typography sx={{ fontSize: 12, color: '#5A7A96' }}>AI 분석관 초기화 중...</Typography>
          <Box sx={{ mt: 1, display: 'flex', justifyContent: 'center', gap: 0.5 }}>
            {[0, 1, 2].map((i) => (
              <Box key={i} sx={{
                width: 6, height: 6, borderRadius: '50%', background: '#2E75B6',
                animation: `blink 1.4s ${i * 0.2}s infinite`,
                '@keyframes blink': { '0%, 80%, 100%': { opacity: 0.3 }, '40%': { opacity: 1 } },
              }} />
            ))}
          </Box>
        </CardContent>
      </Card>
    );
  }

  const color = levelColors[insight.riskLevel] || '#42A5F5';
  const isAI = insight.source === 'ai';

  return (
    <Card sx={{
      border: `1px solid ${color}33`,
      background: `linear-gradient(135deg, ${color}08, transparent)`,
    }}>
      <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
        {/* 헤더 */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography sx={{ fontSize: 18 }}>🧠</Typography>
            <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700 }}>
              AI 분석관
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 0.5 }}>
            <Chip
              size="small"
              label={insight.riskLevel}
              sx={{ height: 20, fontSize: 10, fontWeight: 700, background: `${color}22`, color, border: `1px solid ${color}44` }}
            />
            <Chip
              size="small"
              label={isAI ? `${insight.model}` : 'Rule-based'}
              sx={{
                height: 20, fontSize: 9,
                background: isAI ? 'rgba(46,117,182,0.15)' : 'rgba(255,255,255,0.05)',
                color: isAI ? '#5CA0E0' : '#5A7A96',
              }}
            />
          </Box>
        </Box>

        {/* 종합 판단 */}
        <Box sx={{ p: 1.2, background: `${color}0A`, borderRadius: 2, border: `1px solid ${color}20`, mb: 1 }}>
          <Typography sx={{ fontSize: 14, fontWeight: 700, color, mb: 0.3 }}>
            {insight.summary}
          </Typography>
          <Typography sx={{ fontSize: 11, color: '#B0BEC5', lineHeight: 1.5 }}>
            {insight.analysis}
          </Typography>
        </Box>

        {/* 경고 */}
        {insight.warnings && insight.warnings.length > 0 && (
          <Box sx={{ mb: 1 }}>
            {insight.warnings.map((w, i) => (
              <Box key={i} sx={{
                p: 0.8, mb: 0.4, borderRadius: 1.5,
                background: 'rgba(229,57,53,0.06)', borderLeft: '3px solid #E53935',
              }}>
                <Typography sx={{ fontSize: 11, color: '#EF5350' }}>🚨 {w}</Typography>
              </Box>
            ))}
          </Box>
        )}

        {/* 권고사항 */}
        {insight.recommendations && insight.recommendations.length > 0 && (
          <Box sx={{ mb: 1 }}>
            <Typography sx={{ fontSize: 10, color: '#5A7A96', mb: 0.5, fontWeight: 600 }}>💡 AI 권고사항</Typography>
            {insight.recommendations.map((r, i) => (
              <Box key={i} sx={{ display: 'flex', gap: 0.5, mb: 0.3 }}>
                <Typography sx={{ fontSize: 10, color: '#2E75B6', minWidth: 14 }}>{i + 1}.</Typography>
                <Typography sx={{ fontSize: 11, color: '#B0BEC5' }}>{r}</Typography>
              </Box>
            ))}
          </Box>
        )}

        {/* 예측 */}
        {insight.prediction && (
          <Box sx={{ p: 0.8, background: 'rgba(30,136,229,0.06)', borderRadius: 1.5, borderLeft: '3px solid #1E88E5' }}>
            <Typography sx={{ fontSize: 10, color: '#5A7A96', mb: 0.2 }}>📊 향후 예측</Typography>
            <Typography sx={{ fontSize: 11, color: '#90CAF9' }}>{insight.prediction}</Typography>
          </Box>
        )}

        {/* 타임스탬프 */}
        <Typography sx={{ fontSize: 9, color: '#3A5268', mt: 1, textAlign: 'right' }}>
          {insight.timestamp ? new Date(insight.timestamp).toLocaleTimeString('ko-KR') : ''} 업데이트
        </Typography>
      </CardContent>
    </Card>
  );
}
