import { Router } from 'express';
import { getAllAEDs, findNearestAED } from '../services/aed';

const router = Router();

// 전체 AED 목록
router.get('/', (_req, res) => {
  res.json(getAllAEDs());
});

// 특정 구역에서 가장 가까운 AED 찾기
router.get('/nearest/:zone', (req, res) => {
  const result = findNearestAED(req.params.zone);
  if (result) {
    res.json(result);
  } else {
    res.json({ aed: null, distance: '데이터 없음', priority: 'none' });
  }
});

export default router;
