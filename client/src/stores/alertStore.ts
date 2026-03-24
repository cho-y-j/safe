import { create } from 'zustand';

interface AlertEvent {
  id: string;
  time: string;
  level: 'info' | 'caution' | 'warning' | 'danger';
  message: string;
  workerId?: string;
  zone?: string;
}

interface AlertState {
  events: AlertEvent[];
  addEvent: (event: Omit<AlertEvent, 'id' | 'time'>) => void;
  clearEvents: () => void;
}

export const useAlertStore = create<AlertState>((set) => ({
  events: [],

  addEvent: (event) =>
    set((state) => ({
      events: [
        {
          ...event,
          id: `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          time: new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        },
        ...state.events,
      ].slice(0, 100), // 최근 100건
    })),

  clearEvents: () => set({ events: [] }),
}));
