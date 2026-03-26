import { Server as SocketServer } from 'socket.io';
import { Worker, SensorData } from '../../models';
import { latestPublicData } from '../scheduler';

// 인천공항 구역별 좌표 (Leaflet용 lat/lng)
const ZONE_COORDS: Record<string, { lat: number; lng: number }> = {
  'R2-S': { lat: 37.4490, lng: 126.4510 }, // 제2활주로 남단
  'T1-B': { lat: 37.4492, lng: 126.4505 }, // 제1터미널 B구역
  'CB-3': { lat: 37.4600, lng: 126.4430 }, // 탑승동 연결통로
  'CG-1': { lat: 37.4520, lng: 126.4600 }, // 화물터미널
  'T2-S': { lat: 37.4608, lng: 126.4260 }, // 제2터미널 보안
  'AP-2': { lat: 37.4560, lng: 126.4480 }, // 계류장 2구역
  'CT-1': { lat: 37.4530, lng: 126.4380 }, // 기내식 센터
  'T1-C': { lat: 37.4495, lng: 126.4515 }, // 제1터미널 C구역
};

// 작업자별 개인 베이스라인 (TinyML 시뮬레이션)
interface WorkerBaseline {
  restHR: number;
  activeHR: number;
  normalTemp: number;
  normalSPO2: number;
  fatigueAccum: number;
  workMinutes: number;
}

const baselines: Record<string, WorkerBaseline> = {};

// 시나리오 트리거 상태
export interface ScenarioState {
  heatwave: boolean;
  airPollution: boolean;
  peakOverwork: boolean;
  collectiveAnomaly: boolean;
  collectiveZone: string;
}

export const scenarioState: ScenarioState = {
  heatwave: false,
  airPollution: false,
  peakOverwork: false,
  collectiveAnomaly: false,
  collectiveZone: 'CG-1',
};

function initBaseline(workerId: string): WorkerBaseline {
  return {
    restHR: 65 + Math.random() * 15, // 65~80
    activeHR: 85 + Math.random() * 20, // 85~105
    normalTemp: 36.3 + Math.random() * 0.4, // 36.3~36.7
    normalSPO2: 97 + Math.random() * 2, // 97~99
    fatigueAccum: 0,
    workMinutes: 0,
  };
}

function generateSensorData(workerId: string, zone: string): {
  heartRate: number;
  bodyTemp: number;
  spo2: number;
  stress: number;
  hrv: number;
  lat: number;
  lng: number;
  status: string;
  alerts: string[];
} {
  if (!baselines[workerId]) baselines[workerId] = initBaseline(workerId);
  const bl = baselines[workerId];
  bl.workMinutes += 0.083; // ~5초 간격
  bl.fatigueAccum = Math.min(100, bl.fatigueAccum + 0.01 + Math.random() * 0.02);

  const weather = latestPublicData.weather;
  const airQuality = latestPublicData.airQuality;
  const flights = latestPublicData.flights;

  const temp = weather?.temp ?? 20;
  const pm25 = airQuality?.pm25 ?? 15;
  const flightTotal = flights?.total ?? 15;

  let hrMod = 0;
  let tempMod = 0;
  let spo2Mod = 0;
  let stressMod = 0;
  const alerts: string[] = [];

  // 환경 영향
  if (temp > 30) hrMod += (temp - 30) * 2;
  if (temp > 33) { tempMod += 0.3; stressMod += 15; }
  if (pm25 > 35) { spo2Mod -= 1; stressMod += 10; }
  if (flightTotal > 25) { hrMod += 5; stressMod += 10; }

  // 시나리오 오버라이드
  if (scenarioState.heatwave && ['R2-S', 'AP-2', 'CG-1'].includes(zone)) {
    hrMod += 25;
    tempMod += 0.8;
    stressMod += 30;
    alerts.push('폭염 경고: 야외 작업 구역 고위험');
  }

  if (scenarioState.airPollution && ['R2-S', 'AP-2', 'CG-1', 'CT-1'].includes(zone)) {
    spo2Mod -= 2;
    stressMod += 15;
    alerts.push('대기질 악화: 마스크 착용 필요');
  }

  if (scenarioState.peakOverwork && ['T1-B', 'CG-1', 'CT-1'].includes(zone)) {
    hrMod += 15;
    bl.fatigueAccum = Math.min(100, bl.fatigueAccum + 0.5);
    stressMod += 20;
    alerts.push('피크 시간대: 작업 강도 높음');
  }

  if (scenarioState.collectiveAnomaly && zone === scenarioState.collectiveZone) {
    hrMod += 30;
    spo2Mod -= 5;
    stressMod += 40;
    alerts.push('집단 이상 감지: 즉시 대피 필요');
  }

  // 피로 영향
  if (bl.fatigueAccum > 60) { hrMod += 8; stressMod += 15; }
  if (bl.fatigueAccum > 80) { alerts.push('피로도 위험: 즉시 휴식 권고'); }

  // 최종 값 계산
  const heartRate = Math.round(
    bl.restHR + (bl.activeHR - bl.restHR) * 0.5 + hrMod + (Math.random() - 0.5) * 6
  );
  const bodyTemp = parseFloat(
    (bl.normalTemp + tempMod + (Math.random() - 0.5) * 0.2).toFixed(1)
  );
  const spo2 = Math.max(88, Math.min(100, Math.round(
    bl.normalSPO2 + spo2Mod + (Math.random() - 0.5) * 1.5
  )));
  const stress = Math.max(0, Math.min(100, Math.round(
    20 + bl.fatigueAccum * 0.3 + stressMod + (Math.random() - 0.5) * 8
  )));
  const hrv = Math.max(15, Math.round(
    65 - bl.fatigueAccum * 0.3 - stressMod * 0.2 + (Math.random() - 0.5) * 10
  ));

  // 위치에 약간의 움직임 추가
  const base = ZONE_COORDS[zone] || { lat: 37.4602, lng: 126.4407 };
  const lat = base.lat + (Math.random() - 0.5) * 0.001;
  const lng = base.lng + (Math.random() - 0.5) * 0.001;

  // 상태 판정
  let status = 'normal';
  if (heartRate > 110 || bodyTemp > 37.5 || spo2 < 94 || stress > 70) status = 'caution';
  if (heartRate > 125 || bodyTemp > 38.0 || spo2 < 92 || stress > 85) status = 'danger';

  if (heartRate > 100 && !alerts.length) alerts.push(`심박수 ${heartRate}bpm 상승 — 피로도 누적 주의`);
  if (bodyTemp > 37.3 && !alerts.some(a => a.includes('체온'))) alerts.push(`체온 ${bodyTemp}°C — 열사병 주의`);
  if (spo2 < 95 && !alerts.some(a => a.includes('산소'))) alerts.push(`SpO₂ ${spo2}% — 산소포화도 저하`);

  return { heartRate, bodyTemp, spo2, stress, hrv, lat, lng, status, alerts };
}

// 실제 워치 데이터 저장소 (워치에서 POST /api/workers/:id/sensor로 전송한 데이터)
export const realWatchData: Record<string, any> = {};

export function startWearableSimulator(io: SocketServer) {
  // 5초 간격으로 전체 작업자 데이터 갱신 + 브로드캐스트
  setInterval(async () => {
    try {
      const workers = await Worker.findAll({ where: { isActive: true } });
      const workerDataList = [];

      for (const worker of workers) {
        // 실제 워치 연결된 작업자는 시뮬레이션 스킵
        if (realWatchData[worker.id]) {
          const real = realWatchData[worker.id];
          workerDataList.push({
            id: worker.id,
            name: worker.name,
            role: worker.role,
            zone: worker.zone,
            location: worker.location,
            floor: worker.floor,
            medicalHistory: worker.medicalHistory,
            heartRate: real.heartRate || 0,
            bodyTemp: real.bodyTemp || 36.5,
            spo2: real.spo2 || 98,
            stress: real.stress || 0,
            hrv: real.hrv || 50,
            lat: real.latitude || 37.4602,
            lng: real.longitude || 126.4407,
            status: real.heartRate > 130 ? 'danger' : real.heartRate > 100 ? 'caution' : 'normal',
            alerts: [],
            fatigue: real.stress || 0,
            workMinutes: 0,
          });
          continue;
        }

        const data = generateSensorData(worker.id, worker.zone);

        // DB 저장 (10회에 1번만 — 저장 빈도 조절)
        if (Math.random() < 0.1) {
          await SensorData.create({
            workerId: worker.id,
            heartRate: data.heartRate,
            bodyTemp: data.bodyTemp,
            spo2: data.spo2,
            stress: data.stress,
            hrv: data.hrv,
            latitude: data.lat,
            longitude: data.lng,
          });
        }

        workerDataList.push({
          id: worker.id,
          name: worker.name,
          role: worker.role,
          zone: worker.zone,
          location: worker.location,
          floor: worker.floor,
          medicalHistory: worker.medicalHistory,
          ...data,
          fatigue: baselines[worker.id]?.fatigueAccum ?? 0,
          workMinutes: baselines[worker.id]?.workMinutes ?? 0,
        });
      }

      io.emit('workers:update', {
        workers: workerDataList,
        timestamp: new Date().toISOString(),
      });
    } catch (err) {
      console.error('[Simulator] Error:', err);
    }
  }, 5000);
}

// 시나리오 트리거 함수 (API에서 호출)
export function triggerScenario(scenario: keyof ScenarioState, active: boolean, options?: { zone?: string }) {
  scenarioState[scenario] = active;
  if (options?.zone && scenario === 'collectiveAnomaly') {
    scenarioState.collectiveZone = options.zone;
  }

  // 시나리오 비활성화 시 피로도 리셋
  if (!active) {
    Object.values(baselines).forEach((bl) => {
      bl.fatigueAccum = Math.max(0, bl.fatigueAccum - 20);
    });
  }
}
