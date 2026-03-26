import { Router } from 'express';
import { sequelize } from '../models';
import { DataTypes, Model } from 'sequelize';

const router = Router();

// ─── 베이스라인 모델 ───
class Baseline extends Model {}
Baseline.init({
  id: { type: DataTypes.INTEGER, autoIncrement: true, primaryKey: true },
  workerId: { type: DataTypes.STRING(10), allowNull: false },
  date: { type: DataTypes.DATEONLY, allowNull: false },
  timeSlot: { type: DataTypes.STRING(10), allowNull: false }, // morning, afternoon, evening, night
  // 안정 시
  restHrMean: { type: DataTypes.FLOAT, defaultValue: 72 },
  restHrStd: { type: DataTypes.FLOAT, defaultValue: 8 },
  restSamples: { type: DataTypes.INTEGER, defaultValue: 0 },
  // 활동 시
  activeHrMean: { type: DataTypes.FLOAT, defaultValue: 90 },
  activeHrStd: { type: DataTypes.FLOAT, defaultValue: 12 },
  activeSamples: { type: DataTypes.INTEGER, defaultValue: 0 },
  // 환경
  avgTemp: { type: DataTypes.FLOAT, allowNull: true },
  avgPm25: { type: DataTypes.FLOAT, allowNull: true },
  // 근무
  workMinutes: { type: DataTypes.INTEGER, defaultValue: 0 },
  restMinutes: { type: DataTypes.INTEGER, defaultValue: 0 },
}, { sequelize, tableName: 'baselines', timestamps: true });

// 테이블 자동 생성
Baseline.sync({ alter: true });

// 워치에서 베이스라인 업로드 (매 30분 또는 작업 종료 시)
router.post('/sync', async (req, res) => {
  try {
    const { workerId, date, timeSlot, restHrMean, restHrStd, restSamples,
            activeHrMean, activeHrStd, activeSamples, avgTemp, avgPm25,
            workMinutes, restMinutes } = req.body;

    const [baseline, created] = await Baseline.findOrCreate({
      where: { workerId, date, timeSlot },
      defaults: req.body,
    });

    if (!created) {
      // EMA 업데이트 (기존 + 새 데이터 병합)
      const alpha = 0.3;
      await baseline.update({
        restHrMean: (baseline as any).restHrMean * (1 - alpha) + restHrMean * alpha,
        restHrStd: (baseline as any).restHrStd * (1 - alpha) + restHrStd * alpha,
        restSamples: (baseline as any).restSamples + restSamples,
        activeHrMean: (baseline as any).activeHrMean * (1 - alpha) + activeHrMean * alpha,
        activeHrStd: (baseline as any).activeHrStd * (1 - alpha) + activeHrStd * alpha,
        activeSamples: (baseline as any).activeSamples + activeSamples,
        avgTemp: avgTemp ?? (baseline as any).avgTemp,
        avgPm25: avgPm25 ?? (baseline as any).avgPm25,
        workMinutes: (baseline as any).workMinutes + (workMinutes || 0),
        restMinutes: (baseline as any).restMinutes + (restMinutes || 0),
      });
    }

    res.json({ success: true, created });
  } catch (err) {
    res.status(500).json({ error: 'Failed to sync baseline' });
  }
});

// 워치가 베이스라인 복원 요청 (새 워치 또는 앱 재설치 시)
router.get('/restore/:workerId', async (req, res) => {
  try {
    // 최근 30일 데이터에서 시간대별 평균 계산
    const baselines = await Baseline.findAll({
      where: { workerId: req.params.workerId },
      order: [['date', 'DESC']],
      limit: 120, // 30일 x 4 시간대
    });

    if (baselines.length === 0) {
      return res.json({ found: false });
    }

    // 전체 평균 계산
    const allRest = baselines.map((b: any) => b.restHrMean);
    const allActive = baselines.map((b: any) => b.activeHrMean);

    const summary = {
      found: true,
      totalDays: [...new Set(baselines.map((b: any) => b.date))].length,
      totalRecords: baselines.length,
      restHrMean: allRest.reduce((a: number, b: number) => a + b, 0) / allRest.length,
      restHrStd: Math.max(5, baselines.map((b: any) => b.restHrStd).reduce((a: number, b: number) => a + b, 0) / baselines.length),
      activeHrMean: allActive.reduce((a: number, b: number) => a + b, 0) / allActive.length,
      activeHrStd: Math.max(8, baselines.map((b: any) => b.activeHrStd).reduce((a: number, b: number) => a + b, 0) / baselines.length),
      // 시간대별
      byTimeSlot: {} as any,
    };

    for (const slot of ['morning', 'afternoon', 'evening', 'night']) {
      const slotData = baselines.filter((b: any) => b.timeSlot === slot);
      if (slotData.length > 0) {
        summary.byTimeSlot[slot] = {
          restHrMean: slotData.map((b: any) => b.restHrMean).reduce((a: number, b: number) => a + b, 0) / slotData.length,
          activeHrMean: slotData.map((b: any) => b.activeHrMean).reduce((a: number, b: number) => a + b, 0) / slotData.length,
          samples: slotData.length,
        };
      }
    }

    res.json(summary);
  } catch (err) {
    res.status(500).json({ error: 'Failed to restore baseline' });
  }
});

// 작업자 베이스라인 이력 조회 (대시보드용)
router.get('/history/:workerId', async (req, res) => {
  try {
    const days = parseInt(req.query.days as string) || 30;
    const baselines = await Baseline.findAll({
      where: { workerId: req.params.workerId },
      order: [['date', 'DESC'], ['timeSlot', 'ASC']],
      limit: days * 4,
    });
    res.json(baselines);
  } catch (err) {
    res.status(500).json({ error: 'Failed' });
  }
});

export default router;
