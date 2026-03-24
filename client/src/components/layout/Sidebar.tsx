import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  List, ListItemButton, ListItemIcon, ListItemText,
  Box, Typography, Divider, Chip,
} from '@mui/material';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PeopleIcon from '@mui/icons-material/People';
import CloudIcon from '@mui/icons-material/Cloud';
import MapIcon from '@mui/icons-material/Map';
import AssessmentIcon from '@mui/icons-material/Assessment';
import SettingsIcon from '@mui/icons-material/Settings';
import { useDashboardStore } from '../../stores/dashboardStore';

const DRAWER_WIDTH = 190;

const menuItems = [
  { path: '/', label: '통합 상황판', icon: <DashboardIcon /> },
  { path: '/map', label: '지도 현황', icon: <MapIcon /> },
  { path: '/workers', label: '작업자 모니터링', icon: <PeopleIcon /> },
  { path: '/public-data', label: '공공데이터 분석', icon: <CloudIcon /> },
  { path: '/congestion', label: '혼잡 예측', icon: <MapIcon /> },
  { path: '/reports', label: '이력 / 리포트', icon: <AssessmentIcon /> },
  { path: '/admin', label: '인력/기기 관리', icon: <SettingsIcon /> },
];

export default function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();
  const isConnected = useDashboardStore((s) => s.isConnected);

  return (
    <Box
      sx={{
        width: '100%',
        height: '100vh',
        background: 'linear-gradient(180deg, #0D1B2A 0%, #0A1118 100%)',
        borderRight: '1px solid #1E3044',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {/* Logo */}
      <Box sx={{ p: 1.5, borderBottom: '1px solid #1E3044' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box
            sx={{
              width: 32, height: 32, borderRadius: 1.5,
              background: 'linear-gradient(135deg, #2E75B6, #1E88E5)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontWeight: 800, fontSize: 14, color: '#fff',
            }}
          >
            SP
          </Box>
          <Box>
            <Typography sx={{ fontWeight: 700, color: '#fff', fontSize: 14, lineHeight: 1.2 }}>
              SafePulse
            </Typography>
            <Typography sx={{ color: '#5A7A96', fontSize: 10 }}>
              인천공항 AI 안전관리
            </Typography>
          </Box>
        </Box>
      </Box>

      {/* Connection Status */}
      <Box sx={{ px: 1, py: 1 }}>
        <Chip
          size="small"
          label={isConnected ? '● LIVE 연동' : '○ 연결 대기'}
          sx={{
            width: '100%', height: 24,
            background: isConnected ? 'rgba(67,160,71,0.15)' : 'rgba(255,152,0,0.15)',
            color: isConnected ? '#66BB6A' : '#FFB74D',
            fontWeight: 600, fontSize: 10,
          }}
        />
      </Box>

      <Divider sx={{ borderColor: '#1E3044' }} />

      {/* Menu */}
      <List sx={{ px: 0.5, pt: 0.5 }}>
        {menuItems.map((item) => {
          const isActive = location.pathname === item.path;
          return (
            <ListItemButton
              key={item.path}
              onClick={() => navigate(item.path)}
              sx={{
                borderRadius: 1.5, mb: 0.3, py: 0.8,
                color: isActive ? '#fff' : '#7A8FA3',
                background: isActive ? 'rgba(46,117,182,0.2)' : 'transparent',
                borderLeft: isActive ? '3px solid #2E75B6' : '3px solid transparent',
                '&:hover': { background: 'rgba(46,117,182,0.1)', color: '#fff' },
              }}
            >
              <ListItemIcon sx={{ color: isActive ? '#2E75B6' : '#5A7A96', minWidth: 30 }}>
                {item.icon}
              </ListItemIcon>
              <ListItemText
                primary={item.label}
                primaryTypographyProps={{ fontSize: 12, fontWeight: isActive ? 600 : 400 }}
              />
            </ListItemButton>
          );
        })}
      </List>

      {/* Footer */}
      <Box sx={{ mt: 'auto', p: 1.5, borderTop: '1px solid #1E3044' }}>
        <Typography sx={{ color: '#4A6278', fontSize: 10, textAlign: 'center' }}>
          (주)다인온 &copy; 2026
        </Typography>
        <Typography sx={{ color: '#3A5268', fontSize: 9, textAlign: 'center', mt: 0.3 }}>
          AI-PORT 공모전 세션2
        </Typography>
      </Box>
    </Box>
  );
}

export { DRAWER_WIDTH };
