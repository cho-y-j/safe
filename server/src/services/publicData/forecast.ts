import axios from 'axios';
import { cacheGet, cacheSet } from '../redis';

const CACHE_KEY = 'public:forecast';
const CACHE_TTL = 3600; // 1시간

export interface ForecastData {
  hourlyForecast: { hour: number; passengers: number; congestionLevel: string }[];
  peakHour: number;
  peakPassengers: number;
  totalToday: number;
  fetchedAt: string;
}

function congestionLevel(passengers: number): string {
  if (passengers >= 8000) return '매우혼잡';
  if (passengers >= 5000) return '혼잡';
  if (passengers >= 3000) return '보통';
  return '여유';
}

export async function fetchForecast(): Promise<ForecastData> {
  const cached = await cacheGet<ForecastData>(CACHE_KEY);
  if (cached) return cached;

  const apiKey = process.env.DATA_GO_KR_API_KEY;
  if (!apiKey) return generateDemoForecast();

  try {
    const url = 'https://apis.data.go.kr/B551177/passgrAnncmt/getfPassengerNotice';
    const now = new Date();
    const searchDate = now.toISOString().slice(0, 10).replace(/-/g, '');

    const res = await axios.get(url, {
      params: {
        serviceKey: apiKey,
        numOfRows: 24,
        pageNo: 1,
        type: 'json',
        selectdate: searchDate,
      },
      timeout: 15000,
    });

    const items = res.data?.response?.body?.items || [];
    if (items.length > 0) {
      const hourlyForecast = items.map((item: any) => {
        const hour = parseInt(item.atime?.substring(0, 2)) || 0;
        const passengers = parseInt(item.sum_num) || 0;
        return { hour, passengers, congestionLevel: congestionLevel(passengers) };
      });

      const peak = hourlyForecast.reduce((a: any, b: any) => a.passengers > b.passengers ? a : b);
      const data: ForecastData = {
        hourlyForecast,
        peakHour: peak.hour,
        peakPassengers: peak.passengers,
        totalToday: hourlyForecast.reduce((s: number, h: any) => s + h.passengers, 0),
        fetchedAt: new Date().toISOString(),
      };

      await cacheSet(CACHE_KEY, data, CACHE_TTL);
      return data;
    }
  } catch (err) {
    console.error('[Forecast] API error:', err);
  }

  return generateDemoForecast();
}

function generateDemoForecast(): ForecastData {
  const baseCurve = [800, 500, 300, 200, 400, 1200, 3500, 5500, 7000, 6800, 5500, 4800, 5000, 5500, 7200, 8500, 7800, 6500, 5200, 4000, 3000, 2200, 1500, 1000];
  const hourlyForecast = baseCurve.map((base, hour) => {
    const passengers = Math.round(base * (0.85 + Math.random() * 0.3));
    return { hour, passengers, congestionLevel: congestionLevel(passengers) };
  });

  const peak = hourlyForecast.reduce((a, b) => a.passengers > b.passengers ? a : b);
  return {
    hourlyForecast,
    peakHour: peak.hour,
    peakPassengers: peak.passengers,
    totalToday: hourlyForecast.reduce((s, h) => s + h.passengers, 0),
    fetchedAt: new Date().toISOString(),
  };
}
