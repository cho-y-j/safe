import { Router } from 'express';
import { Worker } from '../models';
import { Op } from 'sequelize';

const router = Router();

// 작업자 목록 (필터링 + 페이지네이션)
router.get('/workers', async (req, res) => {
  try {
    const { company, zone, role, shiftGroup, status, search, page = '1', limit = '50' } = req.query;
    const where: any = {};

    if (company) where.company = company;
    if (zone) where.zone = zone;
    if (role) where.role = { [Op.like]: `%${role}%` };
    if (shiftGroup) where.shiftGroup = shiftGroup;
    if (status === 'active') where.isActive = true;
    if (status === 'inactive') where.isActive = false;
    if (search) {
      where[Op.or] = [
        { name: { [Op.like]: `%${search}%` } },
        { id: { [Op.like]: `%${search}%` } },
        { zone: { [Op.like]: `%${search}%` } },
      ];
    }

    const offset = (parseInt(page as string) - 1) * parseInt(limit as string);
    const { count, rows } = await Worker.findAndCountAll({
      where,
      order: [['id', 'ASC']],
      limit: parseInt(limit as string),
      offset,
    });

    res.json({ total: count, page: parseInt(page as string), workers: rows });
  } catch (err) {
    res.status(500).json({ error: 'Failed' });
  }
});

// 작업자 등록
router.post('/workers', async (req, res) => {
  try {
    const worker = await Worker.create(req.body);
    res.json(worker);
  } catch (err) {
    res.status(500).json({ error: 'Failed to create worker' });
  }
});

// 작업자 수정
router.put('/workers/:id', async (req, res) => {
  try {
    const worker = await Worker.findByPk(req.params.id);
    if (!worker) return res.status(404).json({ error: 'Not found' });
    await worker.update(req.body);
    res.json(worker);
  } catch (err) {
    res.status(500).json({ error: 'Failed to update' });
  }
});

// 작업자 삭제
router.delete('/workers/:id', async (req, res) => {
  try {
    const worker = await Worker.findByPk(req.params.id);
    if (!worker) return res.status(404).json({ error: 'Not found' });
    await worker.destroy();
    res.json({ success: true, id: req.params.id });
  } catch (err) {
    res.status(500).json({ error: 'Failed to delete' });
  }
});

// 작업자 정지 (비활성화)
router.post('/workers/:id/deactivate', async (req, res) => {
  try {
    const worker = await Worker.findByPk(req.params.id);
    if (!worker) return res.status(404).json({ error: 'Not found' });
    await worker.update({ isActive: false });
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Failed' });
  }
});

// 작업자 활성화
router.post('/workers/:id/activate', async (req, res) => {
  try {
    const worker = await Worker.findByPk(req.params.id);
    if (!worker) return res.status(404).json({ error: 'Not found' });
    await worker.update({ isActive: true });
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Failed' });
  }
});

// 웨어러블 페어링
router.post('/workers/:id/pair', async (req, res) => {
  try {
    const worker = await Worker.findByPk(req.params.id);
    if (!worker) return res.status(404).json({ error: 'Not found' });
    const { wearableId } = req.body;
    await worker.update({ wearableId, wearableStatus: 'paired' });
    res.json({ success: true, worker });
  } catch (err) {
    res.status(500).json({ error: 'Failed to pair' });
  }
});

// 웨어러블 해제
router.post('/workers/:id/unpair', async (req, res) => {
  try {
    const worker = await Worker.findByPk(req.params.id);
    if (!worker) return res.status(404).json({ error: 'Not found' });
    await worker.update({ wearableId: null, wearableStatus: 'unpaired' });
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Failed to unpair' });
  }
});

// 통계 요약
router.get('/stats', async (_req, res) => {
  try {
    const total = await Worker.count();
    const active = await Worker.count({ where: { isActive: true } });
    const paired = await Worker.count({ where: { wearableId: { [Op.not]: null } } });
    const companies = await Worker.findAll({
      attributes: ['company', [Worker.sequelize!.fn('COUNT', '*'), 'count']],
      group: ['company'],
    });
    const zones = await Worker.findAll({
      attributes: ['zone', [Worker.sequelize!.fn('COUNT', '*'), 'count']],
      group: ['zone'],
    });
    const shifts = await Worker.findAll({
      attributes: ['shiftGroup', [Worker.sequelize!.fn('COUNT', '*'), 'count']],
      group: ['shiftGroup'],
    });

    res.json({
      total,
      active,
      paired,
      wearableRate: total > 0 ? Math.round((paired / active) * 100) : 0,
      byCompany: companies.map((c: any) => ({ company: c.company, count: c.get('count') })),
      byZone: zones.map((z: any) => ({ zone: z.zone, count: z.get('count') })),
      byShift: shifts.map((s: any) => ({ shift: s.shiftGroup, count: s.get('count') })),
    });
  } catch (err) {
    res.status(500).json({ error: 'Failed' });
  }
});

export default router;
