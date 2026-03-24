import cron from 'node-cron';
import axios from 'axios';
import { Server as SocketServer } from 'socket.io';
import { fetchAirQuality } from './publicData/airQuality';
import { fetchWeather } from './publicData/weather';
import { fetchFlights } from './publicData/flights';
import { fetchStatistics } from './publicData/statistics';
import { fetchForecast } from './publicData/forecast';

const AI_ENGINE_URL = process.env.AI_ENGINE_URL || 'http://localhost:8000';

// 최신 공공데이터를 메모리에도 보관 (AI 엔진 연동용)
export let latestPublicData = {
  airQuality: null as any,
  weather: null as any,
  flights: null as any,
  statistics: null as any,
  forecast: null as any,
};

// 최신 AI 인사이트
export let latestInsight: any = null;

async function fetchAndBroadcast(io: SocketServer) {
  try {
    const [airQuality, weather, flights, forecast] = await Promise.all([
      fetchAirQuality(),
      fetchWeather(),
      fetchFlights(),
      fetchForecast(),
    ]);

    latestPublicData = { ...latestPublicData, airQuality, weather, flights, forecast };

    io.emit('publicData:update', {
      airQuality,
      weather,
      flights,
      forecast,
      timestamp: new Date().toISOString(),
    });
  } catch (err) {
    console.error('[Scheduler] Fetch error:', err);
  }
}

async function fetchAIInsight(io: SocketServer) {
  try {
    const { airQuality, weather, flights, forecast } = latestPublicData;
    if (!airQuality || !weather) return;

    // 현재 작업자 데이터는 시뮬레이터에서 가져옴
    // (간단히 마지막 WebSocket 데이터 활용)
    const workersRes = await axios.get(`http://localhost:${process.env.PORT || 4000}/api/workers`);
    const workers = workersRes.data;

    // 1) 위험도 분석
    const riskRes = await axios.post(`${AI_ENGINE_URL}/analyze/risk`, {
      publicData: {
        temp: weather.temp, feelsLike: weather.feelsLike,
        humidity: weather.humidity, windSpeed: weather.windSpeed,
        pm25: airQuality.pm25, pm10: airQuality.pm10,
        o3: airQuality.o3, co: airQuality.co,
        no2: airQuality.no2, so2: airQuality.so2,
        flightTotal: flights?.total || 15,
        flightArriving: flights?.arriving || 8,
        forecastPassengers: forecast?.hourlyForecast?.[flights?.currentHour || 12]?.passengers || 3000,
      },
      workers: workers.map((w: any) => ({
        id: w.id, heartRate: 80, bodyTemp: 36.5, spo2: 98,
        stress: 30, hrv: 55, zone: w.zone,
        workMinutes: 60, fatigue: 10, medicalHistory: w.medicalHistory,
      })),
    });

    // 2) LLM 인사이트 요청
    const insightRes = await axios.post(`${AI_ENGINE_URL}/analyze/insight`, {
      publicData: {
        temp: weather.temp, feelsLike: weather.feelsLike,
        humidity: weather.humidity, windSpeed: weather.windSpeed,
        pm25: airQuality.pm25, pm10: airQuality.pm10,
        o3: airQuality.o3, co: airQuality.co,
        no2: airQuality.no2, so2: airQuality.so2,
        flightTotal: flights?.total || 15,
        flightArriving: flights?.arriving || 8,
        flightDeparting: flights?.departing || 7,
        forecastPassengers: forecast?.hourlyForecast?.[flights?.currentHour || 12]?.passengers || 3000,
      },
      workers: workers.map((w: any) => ({
        name: w.name, role: w.role, zone: w.zone,
        heartRate: 80, bodyTemp: 36.5, spo2: 98,
        stress: 30, fatigue: 10, status: 'normal',
        medicalHistory: w.medicalHistory,
      })),
      riskScore: riskRes.data,
    });

    latestInsight = {
      ...insightRes.data,
      riskAnalysis: riskRes.data,
      timestamp: new Date().toISOString(),
    };

    io.emit('ai:insight', latestInsight);
    console.log(`[AI] Insight generated: ${latestInsight.summary} (${latestInsight.source})`);
  } catch (err: any) {
    console.error('[AI Insight] Error:', err.message);
  }
}

export function startSchedulers(io: SocketServer) {
  // 즉시 1회 실행
  fetchAndBroadcast(io);

  // 대기질 + 기상 + 운항: 5분마다
  cron.schedule('*/5 * * * *', () => fetchAndBroadcast(io));

  // AI 인사이트: 30초 후 첫 실행, 이후 2분마다
  setTimeout(() => fetchAIInsight(io), 30000);
  cron.schedule('*/2 * * * *', () => fetchAIInsight(io));

  // 항공통계: 매일 06시
  cron.schedule('0 6 * * *', async () => {
    try {
      latestPublicData.statistics = await fetchStatistics();
    } catch (err) {
      console.error('[Scheduler] Statistics error:', err);
    }
  });

  // 초기 통계 로드
  fetchStatistics().then((s) => { latestPublicData.statistics = s; });
}
