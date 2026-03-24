import axios from 'axios';
import { cacheGet, cacheSet } from '../redis';

const CACHE_KEY = 'public:statistics';
const CACHE_TTL = 86400; // 24시간

export interface StatisticsData {
  year: number;
  month: number;
  totalFlights: number;
  totalPassengers: number;
  totalCargo: number;
  avgDailyFlights: number;
  trend: 'increasing' | 'stable' | 'decreasing';
  fetchedAt: string;
}

export async function fetchStatistics(): Promise<StatisticsData> {
  const cached = await cacheGet<StatisticsData>(CACHE_KEY);
  if (cached) return cached;

  const apiKey = process.env.DATA_GO_KR_API_KEY;
  if (!apiKey) return generateDemoStats();

  try {
    const url = 'https://apis.data.go.kr/B551177/AviationStatsByAirport/getAviationStatsByAirport';
    const now = new Date();
    // 전월 데이터 조회
    const targetMonth = now.getMonth() === 0 ? 12 : now.getMonth();
    const targetYear = now.getMonth() === 0 ? now.getFullYear() - 1 : now.getFullYear();

    const res = await axios.get(url, {
      params: {
        serviceKey: apiKey,
        numOfRows: 1,
        pageNo: 1,
        type: 'json',
        from_month: `${targetYear}${String(targetMonth).padStart(2, '0')}`,
        to_month: `${targetYear}${String(targetMonth).padStart(2, '0')}`,
        airport_code: 'ICN',
      },
      timeout: 15000,
    });

    const items = res.data?.response?.body?.items;
    if (items && items.length > 0) {
      const item = items[0];
      const totalFlights = parseInt(item.flight) || 30000;
      const data: StatisticsData = {
        year: targetYear,
        month: targetMonth,
        totalFlights,
        totalPassengers: parseInt(item.passenger) || 5000000,
        totalCargo: parseInt(item.cargo) || 200000,
        avgDailyFlights: Math.round(totalFlights / 30),
        trend: 'stable',
        fetchedAt: new Date().toISOString(),
      };

      await cacheSet(CACHE_KEY, data, CACHE_TTL);
      return data;
    }
  } catch (err) {
    console.error('[Statistics] API error:', err);
  }

  return generateDemoStats();
}

function generateDemoStats(): StatisticsData {
  const now = new Date();
  return {
    year: now.getFullYear(),
    month: now.getMonth() || 12,
    totalFlights: 28000 + Math.round(Math.random() * 4000),
    totalPassengers: 4800000 + Math.round(Math.random() * 600000),
    totalCargo: 180000 + Math.round(Math.random() * 40000),
    avgDailyFlights: 900 + Math.round(Math.random() * 200),
    trend: 'increasing',
    fetchedAt: new Date().toISOString(),
  };
}
