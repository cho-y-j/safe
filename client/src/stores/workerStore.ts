import { create } from 'zustand';
import type { WorkerData } from '../types';

interface WorkerState {
  workers: WorkerData[];
  selectedWorkerId: string | null;
  lastUpdate: string | null;

  setWorkers: (workers: WorkerData[]) => void;
  selectWorker: (id: string | null) => void;
  getWorkerById: (id: string) => WorkerData | undefined;
}

export const useWorkerStore = create<WorkerState>((set, get) => ({
  workers: [],
  selectedWorkerId: null,
  lastUpdate: null,

  setWorkers: (workers) => set({ workers, lastUpdate: new Date().toISOString() }),
  selectWorker: (id) => set({ selectedWorkerId: id }),
  getWorkerById: (id) => get().workers.find((w) => w.id === id),
}));
