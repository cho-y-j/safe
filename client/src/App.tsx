import React from 'react';
import { Routes, Route } from 'react-router-dom';
import { Box } from '@mui/material';
import Sidebar, { DRAWER_WIDTH } from './components/layout/Sidebar';
import Header from './components/layout/Header';
import { useWebSocket } from './hooks/useWebSocket';
import Dashboard from './components/dashboard/Dashboard';
import Workers from './components/workers/Workers';
import PublicData from './components/publicData/PublicData';
import Congestion from './components/congestion/Congestion';
import Reports from './components/reports/Reports';

export default function App() {
  useWebSocket();

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', width: '100vw', overflow: 'hidden' }}>
      {/* 사이드바 — 고정 폭 */}
      <Box sx={{ width: DRAWER_WIDTH, minWidth: DRAWER_WIDTH, flexShrink: 0 }}>
        <Sidebar />
      </Box>

      {/* 메인 콘텐츠 — 나머지 전체 */}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <Header />
        <Box sx={{ flex: 1, p: 1, overflow: 'auto' }}>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/workers" element={<Workers />} />
            <Route path="/public-data" element={<PublicData />} />
            <Route path="/congestion" element={<Congestion />} />
            <Route path="/reports" element={<Reports />} />
          </Routes>
        </Box>
      </Box>
    </Box>
  );
}
