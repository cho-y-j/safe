import { Router } from 'express';
import { Alert, Worker } from '../models';
import { Op } from 'sequelize';
import { findNearestAED } from '../services/aed';

const router = Router();

// 긴급 알림 수신 (Galaxy Watch → 서버)
router.post('/emergency', async (req, res) => {
  try {
    const { workerId, message, heartRate, spo2, bodyTemp } = req.body;

    const worker = await Worker.findByPk(workerId);
    const zone = worker?.zone || 'T1-B';
    const nearestAed = findNearestAED(zone);

    const alertMsg = message || `🚨 작업자 ${workerId} 응급 — 심박 ${heartRate}bpm, SpO₂ ${spo2}%`;

    const alert = await Alert.create({
      type: 'emergency',
      level: 'danger',
      message: alertMsg,
      workerId,
      zone,
      scenario: 'p2p_emergency',
    });

    // WebSocket으로 모든 클라이언트에 긴급 알림 + AED 정보
    const { io } = require('../app');
    io.emit('emergency', {
      alert,
      worker: worker ? { id: worker.id, name: worker.name, role: worker.role, zone: worker.zone, location: worker.location } : null,
      nearestAed: nearestAed ? { id: nearestAed.aed.id, location: nearestAed.aed.location, distance: nearestAed.distance } : null,
      timestamp: new Date().toISOString(),
    });

    res.json({ success: true, alert, nearestAed });
  } catch (err) {
    res.status(500).json({ error: 'Failed to create emergency alert' });
  }
});

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

// 경보 확인 (워치/폰/대시보드에서)
router.post('/acknowledge', async (req, res) => {
  try {
    const { workerId, source } = req.body;
    console.log(`[ACK] ${workerId || 'unknown'} acknowledged from ${source || 'unknown'}`);

    // 최근 미해결 긴급 알림 해제
    const recentAlerts = await Alert.findAll({
      where: {
        level: 'danger',
        resolved: false,
        ...(workerId ? { workerId } : {}),
      },
      order: [['createdAt', 'DESC']],
      limit: 5,
    });

    for (const alert of recentAlerts) {
      await alert.update({ resolved: true });
    }

    // WebSocket으로 ACK 브로드캐스트
    const { io } = require('../app');
    io.emit('emergency:ack', {
      workerId,
      source,
      resolvedCount: recentAlerts.length,
      timestamp: new Date().toISOString(),
    });

    res.json({ success: true, resolvedCount: recentAlerts.length });
  } catch (err) {
    res.status(500).json({ error: 'Failed to acknowledge' });
  }
});

// 수신자 응답 (P2P "응답함" / "도움불가")
router.post('/respond', async (req, res) => {
  try {
    const { workerId, responderId, action } = req.body;
    console.log(`[P2P Response] ${responderId} → ${action} for ${workerId}`);

    // WebSocket으로 응답 브로드캐스트 (대시보드에 표시)
    const { io } = require('../app');
    io.emit('emergency:respond', {
      workerId,
      responderId,
      action, // "responding" or "cant_help"
      timestamp: new Date().toISOString(),
    });

    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Failed to record response' });
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
