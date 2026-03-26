import { Server as SocketServer } from 'socket.io';
import { Alert, Worker } from '../models';
import { latestPublicData } from './scheduler';
import { sendToMultiple, type FCMMessage } from './fcm';

/**
 * AI 자동 알림 엔진
 * - 법정 휴식, 폭염, 대기질, 혼잡, 피로도 등
 * - 1분마다 체크하여 조건 충족 시 자동 발송
 */

interface AutoAlertConfig {
  legalRest: boolean;
  fatigue: boolean;
  overwork: boolean;
  heatwave: boolean;
  airQuality: boolean;
  congestion: boolean;
  respiratory: boolean;
  hourlySummary: boolean;
}

// 기본 설정 (전부 ON)
const config: AutoAlertConfig = {
  legalRest: true,
  fatigue: true,
  overwork: true,
  heatwave: true,
  airQuality: true,
  congestion: true,
  respiratory: true,
  hourlySummary: true,
};

// 중복 발송 방지 (키: alertType_workerId, 값: 마지막 발송 시각)
const sentCache: Record<string, number> = {};
const COOLDOWN_MS = 30 * 60 * 1000; // 같은 알림 30분 간격

function canSend(key: string): boolean {
  const last = sentCache[key];
  if (!last || Date.now() - last > COOLDOWN_MS) {
    sentCache[key] = Date.now();
    return true;
  }
  return false;
}

async function createAndBroadcast(io: SocketServer, type: string, level: string, message: string, workerId?: string, zone?: string) {
  await Alert.create({ type: `auto:${type}`, level, message, workerId: workerId || null, zone: zone || null, scenario: 'AI 자동' });
  io.emit('alert:auto', { type, level, message, workerId, zone, timestamp: new Date().toISOString() });

  // FCM도 시도 (토큰이 있으면)
  if (workerId) {
    const worker = await Worker.findByPk(workerId);
    if (worker?.wearableStatus?.includes(':')) {
      const token = worker.wearableStatus.split(':')[1];
      await sendToMultiple([token], { title: 'SafePulse', body: message, type: level });
    }
  }
}

export function startAutoAlertEngine(io: SocketServer) {
  console.log('[AutoAlert] Engine started');

  // 1분마다 체크
  setInterval(async () => {
    try {
      const { airQuality, weather, flights, forecast } = latestPublicData;
      const workers = await Worker.findAll({ where: { isActive: true } });

      // ═══ 폭염 경고 (기상 33°C↑) ═══
      if (config.heatwave && weather?.temp >= 33) {
        const outdoorZones = ['R2-S', 'AP-2', 'CG-1', 'CT-1'];
        const outdoorWorkers = workers.filter((w: any) => outdoorZones.includes(w.zone));
        if (outdoorWorkers.length > 0 && canSend('heatwave_all')) {
          const msg = `🌡 폭염 경고: 기온 ${weather.temp}°C (체감 ${weather.feelsLike}°C). 야외 작업자 교대주기 단축, 매시간 10~15분 그늘 휴식 필수`;
          await createAndBroadcast(io, 'heatwave', 'warning', msg);
        }
      }

      // ═══ 대기질 악화 (PM2.5 ≥ 35) ═══
      if (config.airQuality && airQuality?.pm25 >= 35) {
        if (canSend('airQuality_all')) {
          const msg = `🌫 대기질 나쁨: PM2.5 ${airQuality.pm25}㎍/㎥. 전체 야외 작업자 마스크 착용 필수`;
          await createAndBroadcast(io, 'airQuality', 'warning', msg);
        }

        // 호흡기 이력자 특별 관리
        if (config.respiratory) {
          const respiratoryWorkers = workers.filter((w: any) => w.medicalHistory?.includes('호흡기'));
          for (const w of respiratoryWorkers) {
            if (canSend(`respiratory_${w.id}`)) {
              const msg = `🏥 ${w.name}님: 호흡기 질환 이력 + PM2.5 나쁨. 실내 배치 전환 권고. SpO₂ 집중 모니터링`;
              await createAndBroadcast(io, 'respiratory', 'warning', msg, w.id, w.zone);
            }
          }
        }
      }

      // ═══ 혼잡 예측 (운항 30편↑) ═══
      if (config.congestion && flights?.total >= 30) {
        if (canSend('congestion_all')) {
          const peakHour = flights.peakHours?.[0];
          const msg = `✈ 혼잡 예상: 현재 운항 ${flights.total}편. ${peakHour ? `${peakHour}시 피크` : '피크 시간대'}. 수하물/화물 구역 인력 보강 필요`;
          await createAndBroadcast(io, 'congestion', 'info', msg);
        }
      }

      // ═══ 매시 정각 현황 요약 ═══
      if (config.hourlySummary) {
        const now = new Date();
        if (now.getMinutes() === 0 && canSend(`hourly_${now.getHours()}`)) {
          const activeCount = workers.length;
          const msg = `📊 ${now.getHours()}시 현황: 근무 ${activeCount}명 | 기온 ${weather?.temp ?? '-'}°C | PM2.5 ${airQuality?.pm25 ?? '-'} | 운항 ${flights?.total ?? '-'}편`;
          await createAndBroadcast(io, 'hourlySummary', 'info', msg);
        }
      }

    } catch (err) {
      console.error('[AutoAlert] Error:', err);
    }
  }, 60000); // 1분마다
}
