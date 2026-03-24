import axios from 'axios';
import { cacheGet, cacheSet } from '../redis';

const CACHE_KEY = 'public:weather';
const CACHE_TTL = 600; // 10분

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

function calcFeelsLike(temp: number, windSpeed: number, humidity: number): number {
  if (temp >= 27) {
    // 체감온도 (열지수)
    return parseFloat((temp + 0.33 * (humidity / 100 * 6.105 * Math.exp(17.27 * temp / (237.7 + temp))) - 0.7 * windSpeed - 4).toFixed(1));
  }
  if (temp <= 10 && windSpeed > 1.3) {
    // 풍속냉각
    return parseFloat((13.12 + 0.6215 * temp - 11.37 * Math.pow(windSpeed * 3.6, 0.16) + 0.3965 * temp * Math.pow(windSpeed * 3.6, 0.16)).toFixed(1));
  }
  return temp;
}

function weatherRiskLevel(temp: number, windSpeed: number): string {
  if (temp >= 35 || temp <= -10) return '위험';
  if (temp >= 33 || temp <= -5 || windSpeed > 15) return '경고';
  if (temp >= 30 || temp <= 0 || windSpeed > 10) return '주의';
  return '안전';
}

export async function fetchWeather(): Promise<WeatherData> {
  const cached = await cacheGet<WeatherData>(CACHE_KEY);
  if (cached) return cached;

  // 기상청 초단기실황 API (인천공항 격자: nx=55, ny=124)
  try {
    const apiKey = process.env.DATA_GO_KR_API_KEY;
    if (apiKey) {
      const url = 'https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst';
      const now = new Date();
      // API는 정시 기준 → 현재 시각의 정시
      const baseDate = now.toISOString().slice(0, 10).replace(/-/g, '');
      let baseHour = now.getHours();
      if (now.getMinutes() < 40) baseHour = baseHour - 1; // 40분 전이면 이전 시간
      if (baseHour < 0) baseHour = 23;
      const baseTime = String(baseHour).padStart(2, '0') + '00';

      const res = await axios.get(url, {
        params: {
          serviceKey: apiKey,
          numOfRows: 10,
          pageNo: 1,
          dataType: 'JSON',
          base_date: baseDate,
          base_time: baseTime,
          nx: 55,
          ny: 124,
        },
        timeout: 10000,
      });

      const items = res.data?.response?.body?.items?.item;
      if (items && items.length > 0) {
        let temp = 20, humidity = 60, windSpeed = 3, windDir = 'W';
        for (const item of items) {
          if (item.category === 'T1H') temp = parseFloat(item.obsrValue);
          if (item.category === 'REH') humidity = parseInt(item.obsrValue);
          if (item.category === 'WSD') windSpeed = parseFloat(item.obsrValue);
          if (item.category === 'VEC') {
            const deg = parseInt(item.obsrValue);
            const dirs = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
            windDir = dirs[Math.round(deg / 45) % 8];
          }
        }

        const feelsLike = calcFeelsLike(temp, windSpeed, humidity);
        const data: WeatherData = {
          temp, feelsLike, humidity, windSpeed, windDir,
          pressure: 1013,
          visibility: 10,
          description: temp >= 30 ? '맑고 더움' : temp <= 5 ? '맑고 추움' : '맑음',
          riskLevel: weatherRiskLevel(temp, windSpeed),
          fetchedAt: new Date().toISOString(),
        };

        await cacheSet(CACHE_KEY, data, CACHE_TTL);
        return data;
      }
    }
  } catch (err) {
    console.error('[Weather] API error:', err);
  }

  return generateDemoWeather();
}

function generateDemoWeather(): WeatherData {
  const month = new Date().getMonth();
  const baseTemp = [0, 2, 8, 14, 19, 24, 27, 28, 23, 16, 9, 2][month];
  const hourMod = Math.sin((new Date().getHours() - 6) * Math.PI / 12) * 4;
  const temp = parseFloat((baseTemp + hourMod + (Math.random() - 0.5) * 2).toFixed(1));
  const humidity = Math.round(55 + Math.random() * 25);
  const windSpeed = parseFloat((2 + Math.random() * 8).toFixed(1));
  const windDir = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'][Math.floor(Math.random() * 8)];
  const feelsLike = calcFeelsLike(temp, windSpeed, humidity);

  return {
    temp, feelsLike, humidity, windSpeed, windDir,
    pressure: parseFloat((1010 + Math.random() * 15).toFixed(1)),
    visibility: parseFloat((5 + Math.random() * 15).toFixed(1)),
    description: temp >= 30 ? '맑고 더움' : temp <= 5 ? '맑고 추움' : '맑음',
    riskLevel: weatherRiskLevel(temp, windSpeed),
    fetchedAt: new Date().toISOString(),
  };
}
