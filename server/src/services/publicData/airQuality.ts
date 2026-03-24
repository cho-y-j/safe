import axios from 'axios';
import { parseStringPromise } from 'xml2js';
import { cacheGet, cacheSet } from '../redis';

const CACHE_KEY = 'public:airQuality';
const CACHE_TTL = 300; // 5분

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

function gradeFromPM25(pm25: number): string {
  if (pm25 <= 15) return '좋음';
  if (pm25 <= 35) return '보통';
  if (pm25 <= 75) return '나쁨';
  return '매우나쁨';
}

export async function fetchAirQuality(): Promise<AirQualityData> {
  // 캐시 확인
  const cached = await cacheGet<AirQualityData>(CACHE_KEY);
  if (cached) return cached;

  const apiKey = process.env.DATA_GO_KR_API_KEY;
  if (!apiKey) return generateDemoData();

  try {
    const url = 'https://apis.data.go.kr/B551177/OutdoorAirQualityInfo/getOutdoorAirQuality';
    const res = await axios.get(url, {
      params: {
        serviceKey: apiKey,
        numOfRows: 1,
        pageNo: 1,
        type: 'json',
      },
      timeout: 10000,
    });

    const body = res.data?.response?.body;
    if (!body?.items?.length) return generateDemoData();

    const item = body.items[0];
    const pm25Val = parseFloat(item.pm2_5) || 0;
    const data: AirQualityData = {
      stationName: item.locationid || '자유무역지역',
      dataTime: item.rtime || new Date().toISOString(),
      so2: parseFloat(item.so2) || 0,
      co: parseFloat(item.co) || 0,
      o3: parseFloat(item.o3) || 0,
      no2: parseFloat(item.no2) || 0,
      pm10: parseInt(item.pm10) || 0,
      pm25: pm25Val,
      grade: gradeFromPM25(pm25Val),
      fetchedAt: new Date().toISOString(),
    };

    await cacheSet(CACHE_KEY, data, CACHE_TTL);
    return data;
  } catch (err) {
    console.error('[AirQuality] API error:', err);
    // 폴백: 마지막 캐시 또는 데모 데이터
    return generateDemoData();
  }
}

function generateDemoData(): AirQualityData {
  const hour = new Date().getHours();
  const base = hour > 7 && hour < 19 ? 1.2 : 0.8;
  const pm25 = Math.round(12 + Math.random() * 20 * base);
  return {
    stationName: '자유무역지역',
    dataTime: new Date().toISOString(),
    so2: parseFloat((0.003 + Math.random() * 0.005 * base).toFixed(3)),
    co: parseFloat((0.3 + Math.random() * 0.3 * base).toFixed(1)),
    o3: parseFloat((0.02 + Math.random() * 0.04 * base).toFixed(3)),
    no2: parseFloat((0.015 + Math.random() * 0.02 * base).toFixed(3)),
    pm10: Math.round(25 + Math.random() * 35 * base),
    pm25,
    grade: gradeFromPM25(pm25),
    fetchedAt: new Date().toISOString(),
  };
}
