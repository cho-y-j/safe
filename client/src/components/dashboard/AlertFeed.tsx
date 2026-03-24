import React from 'react';
import { Card, CardContent, Typography, Box, Chip } from '@mui/material';
import { useAlertStore } from '../../stores/alertStore';

const levelConfig = {
  info: { color: '#42A5F5', icon: 'ℹ️', bg: 'rgba(30,136,229,0.08)' },
  caution: { color: '#42A5F5', icon: '🔵', bg: 'rgba(30,136,229,0.08)' },
  warning: { color: '#FFB74D', icon: '⚠️', bg: 'rgba(255,152,0,0.08)' },
  danger: { color: '#EF5350', icon: '🚨', bg: 'rgba(229,57,53,0.08)' },
};

export default function AlertFeed() {
  const events = useAlertStore((s) => s.events);

  return (
    <Card sx={{ flex: 1, overflow: 'hidden' }}>
      <CardContent sx={{ py: 1.5, display: 'flex', flexDirection: 'column', height: '100%' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
          <Typography variant="subtitle2" sx={{ color: '#2E75B6', fontWeight: 700 }}>
            📋 AI 판단 이벤트 로그
          </Typography>
          <Chip size="small" label={`${events.length}건`} sx={{ height: 20, fontSize: 10, background: 'rgba(30,136,229,0.15)', color: '#42A5F5' }} />
        </Box>

        <Box sx={{ flex: 1, overflow: 'auto', '&::-webkit-scrollbar': { width: 4 }, '&::-webkit-scrollbar-thumb': { background: '#1E3044', borderRadius: 2 } }}>
          {events.length === 0 ? (
            <Typography sx={{ fontSize: 12, color: '#5A7A96', textAlign: 'center', mt: 3 }}>
              이벤트 대기 중...
            </Typography>
          ) : (
            events.map((event) => {
              const cfg = levelConfig[event.level] || levelConfig.info;
              return (
                <Box
                  key={event.id}
                  sx={{
                    display: 'flex', gap: 1, py: 0.8, px: 1,
                    borderBottom: '1px solid rgba(255,255,255,0.03)',
                    background: cfg.bg, borderRadius: 1, mb: 0.5,
                    animation: 'slideIn 0.3s ease',
                    '@keyframes slideIn': { from: { opacity: 0, transform: 'translateX(-10px)' }, to: { opacity: 1, transform: 'translateX(0)' } },
                  }}
                >
                  <Typography sx={{ fontSize: 10, color: '#5A7A96', minWidth: 55, fontVariantNumeric: 'tabular-nums' }}>
                    {event.time}
                  </Typography>
                  <Typography sx={{ fontSize: 12 }}>{cfg.icon}</Typography>
                  <Typography sx={{ fontSize: 11, color: cfg.color, flex: 1 }}>
                    {event.message}
                  </Typography>
                </Box>
              );
            })
          )}
        </Box>
      </CardContent>
    </Card>
  );
}
