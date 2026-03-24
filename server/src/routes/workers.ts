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

    const { heartRate, spo2, bodyTemp, stress, latitude, longitude } = req.body;

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

    res.json({ success: true, workerId: req.params.id });
  } catch (err) {
    res.status(500).json({ error: 'Failed to save sensor data' });
  }
});

export default router;
