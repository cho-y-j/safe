import React from 'react';
import { Box } from '@mui/material';
import AirportMap from './AirportMap';
import RiskGauge from './RiskGauge';
import PublicDataMini from './PublicDataMini';
import AlertFeed from './AlertFeed';
import ScenarioPanel from './ScenarioPanel';
import AIInsightPanel from './AIInsightPanel';

export default function Dashboard() {
  return (
    <Box sx={{ height: 'calc(100vh - 80px)', display: 'flex', gap: 1.5 }}>
      {/* 좌측: 지도 (58%) */}
      <Box sx={{ flex: '0 0 58%', display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        <AirportMap />
      </Box>

      {/* 우측: 패널들 (42%) */}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 1.5, overflow: 'auto',
        '&::-webkit-scrollbar': { width: 4 }, '&::-webkit-scrollbar-thumb': { background: '#1E3044', borderRadius: 2 },
      }}>
        <AIInsightPanel />
        <RiskGauge />
        <PublicDataMini />
        <ScenarioPanel />
        <AlertFeed />
      </Box>
    </Box>
  );
}
