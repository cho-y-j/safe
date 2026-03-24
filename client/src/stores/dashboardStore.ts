import { create } from 'zustand';
import type { AirQualityData, WeatherData, FlightData, ForecastData, RiskAnalysis } from '../types';

interface DashboardState {
  airQuality: AirQualityData | null;
  weather: WeatherData | null;
  flights: FlightData | null;
  forecast: ForecastData | null;
  riskAnalysis: RiskAnalysis | null;
  isConnected: boolean;
  lastUpdate: string | null;

  setPublicData: (data: {
    airQuality?: AirQualityData;
    weather?: WeatherData;
    flights?: FlightData;
    forecast?: ForecastData;
  }) => void;
  setRiskAnalysis: (data: RiskAnalysis) => void;
  setConnected: (val: boolean) => void;
}

export const useDashboardStore = create<DashboardState>((set) => ({
  airQuality: null,
  weather: null,
  flights: null,
  forecast: null,
  riskAnalysis: null,
  isConnected: false,
  lastUpdate: null,

  setPublicData: (data) =>
    set((state) => ({
      ...state,
      ...data,
      lastUpdate: new Date().toISOString(),
    })),

  setRiskAnalysis: (data) => set({ riskAnalysis: data }),
  setConnected: (val) => set({ isConnected: val }),
}));
