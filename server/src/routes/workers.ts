import { Router } from 'express';
import { Worker, SensorData } from '../models';
import { Op } from 'sequelize';

const router = Router();

// 전체 작업자 목록
router.get('/', async (_req, res) => {
  try {
    const workers = await Worker.findAll({ where: { isActive: true } });
    res.json(workers);
  } catch (err) {
    res.status(500).json({ error: 'Failed to fetch workers' });
  }
});

// 작업자 등록 (워치에서 자동 발급)
router.post('/register', async (req, res) => {
  try {
    const { name, company, role, zone } = req.body;

    // 다음 순번 ID 생성
    const workers = await Worker.findAll({ order: [['id', 'DESC']], limit: 1 });
    let nextNum = 1;
    if (workers.length > 0) {
      const lastId = workers[0].id; // "W-003"
      const num = parseInt(lastId.replace('W-', ''));
      if (!isNaN(num)) nextNum = num + 1;
    }
    const workerId = `W-${String(nextNum).padStart(3, '0')}`;

    const worker = await Worker.create({
      id: workerId,
      name: name || `작업자${nextNum}`,
      role: role || '경량작업',
      zone: zone || 'T1-B',
      location: zone || '미지정',
      floor: '1F',
      company: company || '',
      isActive: true,
    });

    console.log(`[Register] New worker: ${workerId} (${name})`);
    res.json({ success: true, workerId, worker });
  } catch (err) {
    console.error('[Register] Error:', err);
    res.status(500).json({ error: 'Failed to register worker' });
  }
});

// 특정 작업자 상세 + 최근 센서 데이터
router.get('/:id', async (req, res) => {
  try {
    const worker = await Worker.findByPk(req.params.id);
    if (!worker) return res.status(404).json({ error: 'Worker not found' });

    const recentData = await SensorData.findAll({
      where: { workerId: req.params.id },
      order: [['createdAt', 'DESC']],
      limit: 100,
    });

    res.json({ worker, sensorHistory: recentData });
  } catch (err) {
    res.status(500).json({ error: 'Failed to fetch worker' });
  }
});

// 작업자 센서 이력 (시간 범위)
router.get('/:id/history', async (req, res) => {
  try {
    const hours = parseInt(req.query.hours as string) || 8;
    const since = new Date(Date.now() - hours * 60 * 60 * 1000);

    const data = await SensorData.findAll({
      where: {
        workerId: req.params.id,
        createdAt: { [Op.gte]: since },
      },
      order: [['createdAt', 'ASC']],
    });

    res.json(data);
  } catch (err) {
    res.status(500).json({ error: 'Failed to fetch history' });
  }
});

// Galaxy Watch에서 실시간 센서 데이터 수신
router.post('/:id/sensor', async (req, res) => {
  try {
    const worker = await Worker.findByPk(req.params.id);
    if (!worker) return res.status(404).json({ error: 'Worker not found' });

    const { heartRate, spo2, bodyTemp, stress, latitude, longitude, status } = req.body;

    // 실제 워치 데이터 → 시뮬레이터에 반영 (대시보드에 표시)
    const { realWatchData } = require('../services/simulator/wearable');
    realWatchData[req.params.id] = { heartRate, spo2, bodyTemp, stress, latitude, longitude, status: status || 'normal', timestamp: Date.now() };

    await SensorData.create({
      workerId: req.params.id,
      heartRate: heartRate || 0,
      spo2: spo2 || 98,
      bodyTemp: bodyTemp || 36.5,
      stress: stress || 0,
      hrv: 50,
      latitude: latitude || 37.4602,
      longitude: longitude || 126.4407,
    });

    console.log(`[Watch] ${req.params.id}: HR=${heartRate}, SpO₂=${spo2}, temp=${bodyTemp}`);
    res.json({ success: true, workerId: req.params.id });
  } catch (err) {
    res.status(500).json({ error: 'Failed to save sensor data' });
  }
});

export default router;
