import { useEffect, useRef } from 'react';
import { io, Socket } from 'socket.io-client';
import { useDashboardStore } from '../stores/dashboardStore';
import { useWorkerStore } from '../stores/workerStore';
import { useAlertStore } from '../stores/alertStore';
import { useInsightStore } from '../stores/insightStore';

const WS_URL = import.meta.env.VITE_API_URL || 'http://localhost:4000';

export function useWebSocket() {
  const socketRef = useRef<Socket | null>(null);
  const setPublicData = useDashboardStore((s) => s.setPublicData);
  const setConnected = useDashboardStore((s) => s.setConnected);
  const setWorkers = useWorkerStore((s) => s.setWorkers);
  const addEvent = useAlertStore((s) => s.addEvent);

  useEffect(() => {
    const socket = io(WS_URL, { transports: ['websocket', 'polling'] });
    socketRef.current = socket;

    socket.on('connect', () => {
      setConnected(true);
      addEvent({ level: 'info', message: '관제 시스템 연결 완료' });
    });

    socket.on('disconnect', () => {
      setConnected(false);
      addEvent({ level: 'warning', message: '관제 시스템 연결 끊김' });
    });

    socket.on('publicData:update', (data) => {
      setPublicData(data);
    });

    socket.on('ai:insight', (data) => {
      useInsightStore.getState().setInsight(data);
      if (data.warnings?.length > 0) {
        data.warnings.forEach((w: string) => {
          addEvent({ level: 'warning', message: `⚡ AI: ${w}` });
        });
      }
    });

    socket.on('workers:update', (data) => {
      const prevWorkers = useWorkerStore.getState().workers;
      setWorkers(data.workers);

      // 상태 변화 감지 → 알림 생성
      for (const worker of data.workers) {
        const prev = prevWorkers.find((w) => w.id === worker.id);
        if (prev && prev.status !== worker.status) {
          if (worker.status === 'danger') {
            addEvent({
              level: 'danger',
              message: `[위험] ${worker.name} (${worker.role}) — 즉시 조치 필요`,
              workerId: worker.id,
              zone: worker.zone,
            });
          } else if (worker.status === 'caution' && prev.status === 'normal') {
            addEvent({
              level: 'warning',
              message: `[주의] ${worker.name} — ${worker.alerts[0] || '이상 징후 감지'}`,
              workerId: worker.id,
              zone: worker.zone,
            });
          }
        }

        // 새로운 알림 전파
        for (const alert of worker.alerts) {
          if (!prev?.alerts.includes(alert)) {
            addEvent({
              level: worker.status === 'danger' ? 'danger' : 'warning',
              message: `${worker.name}: ${alert}`,
              workerId: worker.id,
              zone: worker.zone,
            });
          }
        }
      }
    });

    return () => {
      socket.disconnect();
    };
  }, []);

  return socketRef;
}
