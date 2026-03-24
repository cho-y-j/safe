import { create } from 'zustand';

export interface AIInsight {
  summary: string;
  riskLevel: string;
  analysis: string;
  recommendations: string[];
  warnings: string[];
  prediction: string;
  source: string;
  model: string;
  timestamp?: string;
}

interface InsightState {
  insight: AIInsight | null;
  setInsight: (data: AIInsight) => void;
}

export const useInsightStore = create<InsightState>((set) => ({
  insight: null,
  setInsight: (data) => set({ insight: data }),
}));
