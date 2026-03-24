// ─── 공공데이터 ───
export interface AirQualityData {
  stationName: string;
  dataTime: string;
  so2: number;
  co: number;
  o3: number;
  no2: number;
  pm10: number;
  pm25: number;
  grade: string;
  fetchedAt: string;
}

export interface WeatherData {
  temp: number;
  feelsLike: number;
  humidity: number;
  windSpeed: number;
  windDir: string;
  pressure: number;
  visibility: number;
  description: string;
  riskLevel: string;
  fetchedAt: string;
}

export interface FlightData {
  arriving: number;
  departing: number;
  total: number;
  hourly: number[];
  currentHour: number;
  peakHours: number[];
  workloadLevel: string;
  fetchedAt: string;
}

export interface ForecastData {
  hourlyForecast: { hour: number; passengers: number; congestionLevel: string }[];
  peakHour: number;
  peakPassengers: number;
  totalToday: number;
  fetchedAt: string;
}

// ─── 작업자 ───
export interface WorkerData {
  id: string;
  name: string;
  role: string;
  zone: string;
  location: string;
  floor: string;
  medicalHistory: string | null;
  heartRate: number;
  bodyTemp: number;
  spo2: number;
  stress: number;
  hrv: number;
  lat: number;
  lng: number;
  status: 'normal' | 'caution' | 'danger';
  alerts: string[];
  fatigue: number;
  workMinutes: number;
}

// ─── AI 분석 ───
export interface RiskBreakdown {
  score: number;
  factors: string[];
  category: string;
}

export interface RiskAnalysis {
  totalScore: number;
  level: string;
  color: string;
  action: string;
  breakdown: {
    weather: RiskBreakdown;
    airQuality: RiskBreakdown;
    flight: RiskBreakdown;
    bio: RiskBreakdown & { abnormalCount: number };
  };
  scenarios: { name: string; recommendation: string }[];
  allFactors: string[];
}

// ─── 알림 ───
export interface AlertData {
  id: number;
  type: string;
  level: 'info' | 'caution' | 'warning' | 'danger';
  message: string;
  workerId: string | null;
  zone: string | null;
  scenario: string | null;
  resolved: boolean;
  createdAt: string;
}

// ─── 시나리오 ───
export interface ScenarioState {
  heatwave: boolean;
  airPollution: boolean;
  peakOverwork: boolean;
  collectiveAnomaly: boolean;
  collectiveZone: string;
}
