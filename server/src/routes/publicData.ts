import { Router } from 'express';
import { fetchAirQuality } from '../services/publicData/airQuality';
import { fetchWeather } from '../services/publicData/weather';
import { fetchFlights } from '../services/publicData/flights';
import { fetchStatistics } from '../services/publicData/statistics';
import { fetchForecast } from '../services/publicData/forecast';
import { latestPublicData, latestInsight } from '../services/scheduler';

const router = Router();

// 전체 공공데이터 한번에
router.get('/all', async (_req, res) => {
  try {
    if (latestPublicData.airQuality) {
      res.json(latestPublicData);
    } else {
      const [airQuality, weather, flights, statistics, forecast] = await Promise.all([
        fetchAirQuality(), fetchWeather(), fetchFlights(), fetchStatistics(), fetchForecast(),
      ]);
      res.json({ airQuality, weather, flights, statistics, forecast });
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to fetch public data' });
  }
});

router.get('/air-quality', async (_req, res) => {
  try { res.json(await fetchAirQuality()); } catch { res.status(500).json({ error: 'Failed' }); }
});

router.get('/weather', async (_req, res) => {
  try { res.json(await fetchWeather()); } catch { res.status(500).json({ error: 'Failed' }); }
});

router.get('/flights', async (_req, res) => {
  try { res.json(await fetchFlights()); } catch { res.status(500).json({ error: 'Failed' }); }
});

router.get('/statistics', async (_req, res) => {
  try { res.json(await fetchStatistics()); } catch { res.status(500).json({ error: 'Failed' }); }
});

router.get('/forecast', async (_req, res) => {
  try { res.json(await fetchForecast()); } catch { res.status(500).json({ error: 'Failed' }); }
});

// AI 인사이트
router.get('/ai-insight', (_req, res) => {
  if (latestInsight) {
    res.json(latestInsight);
  } else {
    res.json({ summary: 'AI 분석 초기화 중...', riskLevel: '안전', analysis: '', recommendations: [], warnings: [], prediction: '', source: 'loading' });
  }
});

export default router;
