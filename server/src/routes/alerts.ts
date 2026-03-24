import { Router } from 'express';
import { Alert } from '../models';
import { Op } from 'sequelize';

const router = Router();

// 최근 알림 목록
router.get('/', async (req, res) => {
  try {
    const limit = parseInt(req.query.limit as string) || 100;
    const alerts = await Alert.findAll({
      order: [['createdAt', 'DESC']],
      limit,
    });
    res.json(alerts);
  } catch (err) {
    res.status(500).json({ error: 'Failed to fetch alerts' });
  }
});

// 알림 해제
router.patch('/:id/resolve', async (req, res) => {
  try {
    const alert = await Alert.findByPk(req.params.id);
    if (!alert) return res.status(404).json({ error: 'Alert not found' });
    await alert.update({ resolved: true });
    res.json(alert);
  } catch (err) {
    res.status(500).json({ error: 'Failed to resolve alert' });
  }
});

// 기간별 알림 통계
router.get('/stats', async (req, res) => {
  try {
    const days = parseInt(req.query.days as string) || 7;
    const since = new Date(Date.now() - days * 24 * 60 * 60 * 1000);

    const alerts = await Alert.findAll({
      where: { createdAt: { [Op.gte]: since } },
      order: [['createdAt', 'ASC']],
    });

    const stats = {
      total: alerts.length,
      byLevel: { info: 0, caution: 0, warning: 0, danger: 0 } as Record<string, number>,
      byType: {} as Record<string, number>,
      resolved: alerts.filter((a) => a.resolved).length,
    };

    alerts.forEach((a) => {
      stats.byLevel[a.level] = (stats.byLevel[a.level] || 0) + 1;
      stats.byType[a.type] = (stats.byType[a.type] || 0) + 1;
    });

    res.json(stats);
  } catch (err) {
    res.status(500).json({ error: 'Failed to fetch stats' });
  }
});

export default router;
