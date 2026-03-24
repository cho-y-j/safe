import { Router } from 'express';
import { triggerScenario, scenarioState } from '../services/simulator/wearable';

const router = Router();

// 현재 시나리오 상태 조회
router.get('/status', (_req, res) => {
  res.json(scenarioState);
});

// 시나리오 트리거 (시연용)
router.post('/trigger', (req, res) => {
  const { scenario, active, zone } = req.body;
  const validScenarios = ['heatwave', 'airPollution', 'peakOverwork', 'collectiveAnomaly'];

  if (!validScenarios.includes(scenario)) {
    return res.status(400).json({ error: `Invalid scenario. Valid: ${validScenarios.join(', ')}` });
  }

  triggerScenario(scenario, active !== false, { zone });
  res.json({ success: true, scenario, active: active !== false, state: scenarioState });
});

// 전체 시나리오 리셋
router.post('/reset', (_req, res) => {
  triggerScenario('heatwave', false);
  triggerScenario('airPollution', false);
  triggerScenario('peakOverwork', false);
  triggerScenario('collectiveAnomaly', false);
  res.json({ success: true, state: scenarioState });
});

export default router;
