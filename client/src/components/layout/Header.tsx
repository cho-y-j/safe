import React, { useEffect, useState } from 'react';
import { Box, Typography, Chip, Tooltip, Switch } from '@mui/material';
import { useDashboardStore } from '../../stores/dashboardStore';
import { usePrivacyStore } from '../../stores/privacyStore';

export default function Header() {
  const [clock, setClock] = useState('');
  const isConnected = useDashboardStore((s) => s.isConnected);
  const riskAnalysis = useDashboardStore((s) => s.riskAnalysis);
  const { privacyMode, togglePrivacy } = usePrivacyStore();

  useEffect(() => {
    const tick = () => {
      const now = new Date();
      setClock(
        now.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }) +
        ' ' +
        now.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      );
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);

  const riskLevel = riskAnalysis?.level || '안전';
  const riskColor = riskAnalysis?.color || '#66BB6A';

  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        px: 3,
        py: 1.5,
        background: 'linear-gradient(135deg, #0D2137, #1B3A5C)',
        borderBottom: '2px solid #2E75B6',
        boxShadow: '0 4px 20px rgba(0,0,0,0.4)',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Typography variant="h6" sx={{ color: '#fff', fontWeight: 700 }}>
          SafePulse
          <Typography component="span" sx={{ ml: 1, color: '#8CB4D8', fontWeight: 400, fontSize: 14 }}>
            | 인천공항 AI 안전관리
          </Typography>
        </Typography>
      </Box>

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        {/* 개인정보 보호 토글 */}
        <Tooltip
          title={
            <Box sx={{ p: 1, maxWidth: 280 }}>
              <Typography sx={{ fontWeight: 700, fontSize: 13, mb: 0.5 }}>
                {privacyMode ? '개인정보 보호 모드 ON' : '관리자 모드 (원시 데이터 표시)'}
              </Typography>
              <Typography sx={{ fontSize: 11, lineHeight: 1.6 }}>
                {privacyMode
                  ? 'AI가 분석한 위험도/피로도 결과만 표시합니다. 심박수, 체온, SpO₂ 등 원시 생체 데이터는 숨겨집니다. (개인정보보호법 제23조 민감정보 보호)'
                  : '권한 인증된 안전관리자 모드입니다. 긴급 대응을 위해 원시 생체 데이터가 표시됩니다. 열람 이력이 기록됩니다.'
                }
              </Typography>
            </Box>
          }
          arrow
          placement="bottom"
        >
          <Box
            onClick={togglePrivacy}
            sx={{
              display: 'flex', alignItems: 'center', gap: 0.8,
              px: 1.5, py: 0.5, borderRadius: 3, cursor: 'pointer',
              background: privacyMode ? 'rgba(67,160,71,0.15)' : 'rgba(255,152,0,0.15)',
              border: `1px solid ${privacyMode ? '#43A04766' : '#FF980066'}`,
              transition: 'all 0.3s',
              '&:hover': {
                background: privacyMode ? 'rgba(67,160,71,0.25)' : 'rgba(255,152,0,0.25)',
              },
            }}
          >
            <Typography sx={{ fontSize: 14 }}>{privacyMode ? '🔒' : '🔓'}</Typography>
            <Typography sx={{
              fontSize: 11, fontWeight: 600,
              color: privacyMode ? '#66BB6A' : '#FFB74D',
            }}>
              {privacyMode ? '개인정보 보호' : '관리자 모드'}
            </Typography>
            <Switch
              size="small"
              checked={privacyMode}
              sx={{
                ml: 0.3,
                '& .MuiSwitch-switchBase.Mui-checked': { color: '#43A047' },
                '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': { backgroundColor: '#43A047' },
              }}
            />
          </Box>
        </Tooltip>

        <Chip
          size="small"
          label={`종합 위험도: ${riskLevel}`}
          sx={{
            background: `${riskColor}22`,
            color: riskColor,
            fontWeight: 700,
            border: `1px solid ${riskColor}44`,
          }}
        />
        <Chip
          size="small"
          label={isConnected ? '● LIVE' : '○ DEMO'}
          sx={{
            background: isConnected ? 'rgba(229,57,53,0.2)' : 'rgba(255,152,0,0.2)',
            color: isConnected ? '#EF5350' : '#FFB74D',
            fontWeight: 600,
            animation: isConnected ? 'pulse 2s infinite' : 'none',
            '@keyframes pulse': {
              '0%, 100%': { opacity: 1 },
              '50%': { opacity: 0.6 },
            },
          }}
        />
        <Typography variant="body2" sx={{ color: '#8CB4D8', fontVariantNumeric: 'tabular-nums' }}>
          {clock}
        </Typography>
      </Box>
    </Box>
  );
}
