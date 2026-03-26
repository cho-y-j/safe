import { Router } from 'express';
import { Worker, Alert } from '../models';
import { Op } from 'sequelize';
import { sendToDevice, sendToMultiple, sendToTopic, type FCMMessage } from '../services/fcm';

const router = Router();

// FCM 토큰 등록 (워치/폰에서 호출)
router.post('/register-token', async (req, res) => {
  try {
    const { workerId, token, deviceType } = req.body; // deviceType: watch | phone
    const worker = await Worker.findByPk(workerId);
    if (!worker) return res.status(404).json({ error: 'Worker not found' });

    // 토큰을 워커 모델에 저장 (wearableId 필드 활용 또는 별도 필드)
    // 간단히 wearableStatus에 토큰 저장 (임시)
    await worker.update({
      wearableStatus: `${deviceType}:${token}`,
    });

    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Failed to register token' });
  }
});

// 메시지 보내기 (관제센터 → 작업자)
router.post('/send', async (req, res) => {
  try {
    const { target, targetType, messageType, title, body } = req.body;
    // targetType: individual | group | all
    // target: workerId (individual) | { company?, zone?, shiftGroup? } (group) | null (all)

    const message: FCMMessage = {
      title: title || 'SafePulse 알림',
      body: body || '',
      type: messageType || 'notice',
    };

    let tokens: string[] = [];
    let targetDesc = '';
    let workers: any[] = [];

    if (targetType === 'individual') {
      const worker = await Worker.findByPk(target);
      if (worker?.wearableStatus?.includes(':')) {
        tokens.push(worker.wearableStatus.split(':')[1]);
      }
      targetDesc = `${worker?.name || target} (${target})`;
      workers = worker ? [worker] : [];
    } else if (targetType === 'group') {
      const where: any = {};
      if (target?.company) where.company = target.company;
      if (target?.zone) where.zone = target.zone;
      if (target?.shiftGroup) where.shiftGroup = target.shiftGroup;
      if (target?.status) {
        // 이상자만
        // (센서 상태는 실시간이라 DB에 없음 — WebSocket으로 처리)
      }

      workers = await Worker.findAll({ where, attributes: ['id', 'name', 'wearableStatus'] });
      tokens = workers
        .filter((w: any) => w.wearableStatus?.includes(':'))
        .map((w: any) => w.wearableStatus.split(':')[1]);
      targetDesc = `${Object.entries(target || {}).map(([k, v]) => `${k}:${v}`).join(', ')} (${workers.length}명)`;
    } else {
      // 전체
      workers = await Worker.findAll({ where: { isActive: true }, attributes: ['id', 'name', 'wearableStatus'] });
      tokens = workers
        .filter((w: any) => w.wearableStatus?.includes(':'))
        .map((w: any) => w.wearableStatus.split(':')[1]);
      targetDesc = `전체 (${workers.length}명)`;
    }

    // FCM 발송
    let sentCount = 0;
    if (tokens.length > 0) {
      sentCount = await sendToMultiple(tokens, message);
    }

    // DB 기록 (Alert 테이블 활용)
    await Alert.create({
      type: `msg:${messageType}`,
      level: messageType === 'emergency' ? 'danger' : messageType === 'safety' ? 'warning' : 'info',
      message: `[${title}] ${body}`,
      workerId: targetType === 'individual' ? target : null,
      zone: target?.zone || null,
      scenario: `관제→${targetDesc}`,
    });

    // WebSocket으로도 전달 (연결된 클라이언트에)
    const { io } = require('../app');
    io.emit('message:new', {
      title, body, type: messageType,
      target: targetDesc,
      sentCount,
      fcmSent: sentCount,
      timestamp: new Date().toISOString(),
    });

    res.json({
      success: true,
      target: targetDesc,
      fcmSent: sentCount,
      totalWorkers: workers.length,
    });
  } catch (err) {
    console.error('[Messages] Send error:', err);
    res.status(500).json({ error: 'Failed to send message' });
  }
});

// 메시지 이력 조회
router.get('/history', async (req, res) => {
  try {
    const limit = parseInt(req.query.limit as string) || 50;
    const messages = await Alert.findAll({
      where: { type: { [Op.like]: 'msg:%' } },
      order: [['createdAt', 'DESC']],
      limit,
    });
    res.json(messages);
  } catch (err) {
    res.status(500).json({ error: 'Failed' });
  }
});

export default router;
