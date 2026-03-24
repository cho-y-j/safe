import React, { useState } from 'react';
import { Card, CardContent, Typography, Box, Button, Chip } from '@mui/material';
import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:4000';

const scenarios = [
  { key: 'heatwave', label: '폭염 대응', icon: '🔥', color: '#E53935', desc: '기온35°C → 야외구역 위험 → 교대단축' },
  { key: 'airPollution', label: '대기질 악화', icon: '🌫', color: '#FF9800', desc: 'PM2.5 나쁨 → 마스크 → SpO₂ 강화' },
  { key: 'peakOverwork', label: '피크 과로', icon: '✈', color: '#2196F3', desc: '운항42편 → 혼잡 → 인력 재배치' },
  { key: 'collectiveAnomaly', label: '집단 이상', icon: '🚨', color: '#D32F2F', desc: '동일구역 2/3명 이상 → 대피 경보' },
];

export default function ScenarioPanel() {
  const [activeScenarios, setActive] = useState<Record<string, boolean>>({});

  const toggle = async (key: string) => {
    const newState = !activeScenarios[key];
    try {
      await axios.post(`${API_URL}/api/scenarios/trigger`, { scenario: key, active: newState });
      setActive((prev) => ({ ...prev, [key]: newState }));
    } catch (err) {
      console.error('Scenario trigger failed:', err);
    }
  };

  const resetAll = async () => {
    try {
      await axios.post(`${API_URL}/api/scenarios/reset`);
      setActive({});
    } catch (err) {
      console.error('Reset failed:', err);
    }
  };

  return (
    <Card>
      <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700 }}>
            🎬 시나리오 시연
          </Typography>
          <Button size="small" onClick={resetAll} sx={{ fontSize: 10, color: '#7A8FA3', minWidth: 0 }}>
            초기화
          </Button>
        </Box>

        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 0.8 }}>
          {scenarios.map((s) => {
            const isActive = activeScenarios[s.key];
            return (
              <Box
                key={s.key}
                onClick={() => toggle(s.key)}
                sx={{
                  p: 1, borderRadius: 1.5, cursor: 'pointer',
                  background: isActive ? `${s.color}15` : 'rgba(255,255,255,0.02)',
                  border: `1px solid ${isActive ? s.color : '#1E3044'}`,
                  transition: 'all 0.2s',
                  '&:hover': { background: `${s.color}10`, borderColor: `${s.color}66` },
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.3 }}>
                  <Typography sx={{ fontSize: 14 }}>{s.icon}</Typography>
                  <Typography sx={{ fontSize: 11, fontWeight: 600, color: isActive ? s.color : '#E0E6ED' }}>
                    {s.label}
                  </Typography>
                  {isActive && (
                    <Chip size="small" label="ON" sx={{ height: 14, fontSize: 8, ml: 'auto', background: `${s.color}33`, color: s.color }} />
                  )}
                </Box>
                <Typography sx={{ fontSize: 9, color: '#5A7A96' }}>{s.desc}</Typography>
              </Box>
            );
          })}
        </Box>
      </CardContent>
    </Card>
  );
}
