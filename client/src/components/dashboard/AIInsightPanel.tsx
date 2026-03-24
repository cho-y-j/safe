import React, { useState } from 'react';
import { Card, CardContent, Typography, Box, Chip, IconButton, Collapse } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import { useInsightStore } from '../../stores/insightStore';

const levelColors: Record<string, string> = {
  '안전': '#43A047',
  '주의': '#1E88E5',
  '경고': '#FF9800',
  '위험': '#E53935',
};

export default function AIInsightPanel() {
  const insight = useInsightStore((s) => s.insight);
  const [expanded, setExpanded] = useState(false);

  if (!insight) {
    return (
      <Card sx={{ border: '1px solid #1E3044' }}>
        <CardContent sx={{ py: 1.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{
            width: 28, height: 28, borderRadius: 1.5,
            background: 'linear-gradient(135deg, #2E75B6, #1E88E5)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 14, color: '#fff', fontWeight: 700,
          }}>AI</Box>
          <Typography sx={{ fontSize: 12, color: '#5A7A96' }}>AI 분석관 초기화 중...</Typography>
          <Box sx={{ display: 'flex', gap: 0.5, ml: 1 }}>
            {[0, 1, 2].map((i) => (
              <Box key={i} sx={{
                width: 5, height: 5, borderRadius: '50%', background: '#2E75B6',
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
      background: `linear-gradient(135deg, ${color}06, transparent)`,
    }}>
      <CardContent sx={{ py: 1, px: 1.5, '&:last-child': { pb: 1 } }}>
        {/* ── 헤더 (항상 보임) ── */}
        <Box
          onClick={() => setExpanded(!expanded)}
          sx={{ display: 'flex', alignItems: 'center', gap: 1, cursor: 'pointer' }}
        >
          <Box sx={{
            width: 26, height: 26, borderRadius: 1.5, flexShrink: 0,
            background: `linear-gradient(135deg, ${color}, ${color}88)`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 11, color: '#fff', fontWeight: 800,
          }}>AI</Box>

          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography sx={{ fontSize: 13, fontWeight: 700, color, lineHeight: 1.2 }} noWrap>
              {insight.summary}
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexShrink: 0 }}>
            <Chip size="small" label={insight.riskLevel}
              sx={{ height: 18, fontSize: 9, fontWeight: 700, background: `${color}22`, color, border: `1px solid ${color}44` }}
            />
            {isAI && (
              <Chip size="small" label={insight.model}
                sx={{ height: 18, fontSize: 8, background: 'rgba(46,117,182,0.12)', color: '#5CA0E0' }}
              />
            )}
            <IconButton size="small" sx={{ color: '#5A7A96', p: 0.3 }}>
              {expanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
            </IconButton>
          </Box>
        </Box>

        {/* ── 경고 배너 (항상 보임, 있을 때만) ── */}
        {insight.warnings && insight.warnings.length > 0 && (
          <Box sx={{ mt: 0.8, display: 'flex', flexDirection: 'column', gap: 0.3 }}>
            {insight.warnings.slice(0, expanded ? 10 : 1).map((w, i) => (
              <Box key={i} sx={{
                px: 1, py: 0.4, borderRadius: 1,
                background: 'rgba(229,57,53,0.08)', borderLeft: '2px solid #E53935',
              }}>
                <Typography sx={{ fontSize: 10, color: '#EF5350' }} noWrap={!expanded}>
                  {w}
                </Typography>
              </Box>
            ))}
            {!expanded && insight.warnings.length > 1 && (
              <Typography sx={{ fontSize: 9, color: '#5A7A96', pl: 1 }}>
                +{insight.warnings.length - 1}건 더보기
              </Typography>
            )}
          </Box>
        )}

        {/* ── 상세 (접었다 펼치기) ── */}
        <Collapse in={expanded}>
          <Box sx={{ mt: 1 }}>
            {/* 상세 분석 */}
            {insight.analysis && (
              <Box sx={{ p: 1, background: 'rgba(255,255,255,0.02)', borderRadius: 1.5, mb: 1 }}>
                <Typography sx={{ fontSize: 11, color: '#B0BEC5', lineHeight: 1.6 }}>
                  {insight.analysis}
                </Typography>
              </Box>
            )}

            {/* 권고사항 */}
            {insight.recommendations && insight.recommendations.length > 0 && (
              <Box sx={{ mb: 1 }}>
                <Typography sx={{ fontSize: 10, color: '#5A7A96', mb: 0.5, fontWeight: 600 }}>
                  AI 권고사항
                </Typography>
                {insight.recommendations.map((r, i) => (
                  <Box key={i} sx={{ display: 'flex', gap: 0.5, mb: 0.3, pl: 0.5 }}>
                    <Typography sx={{ fontSize: 10, color: '#2E75B6', fontWeight: 700 }}>{i + 1}.</Typography>
                    <Typography sx={{ fontSize: 10, color: '#B0BEC5', lineHeight: 1.5 }}>{r}</Typography>
                  </Box>
                ))}
              </Box>
            )}

            {/* 예측 */}
            {insight.prediction && (
              <Box sx={{ p: 0.8, background: 'rgba(30,136,229,0.05)', borderRadius: 1, borderLeft: '2px solid #1E88E5' }}>
                <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>향후 예측</Typography>
                <Typography sx={{ fontSize: 10, color: '#90CAF9' }}>{insight.prediction}</Typography>
              </Box>
            )}

            {/* 타임스탬프 */}
            <Typography sx={{ fontSize: 8, color: '#3A5268', mt: 0.8, textAlign: 'right' }}>
              {insight.timestamp ? new Date(insight.timestamp).toLocaleTimeString('ko-KR') : ''} 업데이트
            </Typography>
          </Box>
        </Collapse>
      </CardContent>
    </Card>
  );
}
