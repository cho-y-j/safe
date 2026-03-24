/**
 * AED(제세동기) 위치 서비스
 * - 78개 AED 위치 데이터 (T1 + T2 + 탑승동)
 * - 작업자 구역 기반 가장 가까운 AED 매칭
 */

interface AEDLocation {
  id: string;
  location: string;
  terminal: string;
  floor: string;
  area: string;
}

// AED 번호에서 층/구역 파싱: T1-B2-A1 → terminal=T1, floor=B2, area=A1
function parseAedId(id: string): { terminal: string; floor: string; area: string } {
  const parts = id.split('-');
  return {
    terminal: parts[0] || '',
    floor: parts[1] || '',
    area: parts[2] || '',
  };
}

// 작업자 구역 → 터미널/층 매핑
const ZONE_TO_TERMINAL: Record<string, { terminal: string; floor: string }> = {
  'R2-S': { terminal: 'T1', floor: 'GF' },   // 활주로 → T1 지상
  'T1-B': { terminal: 'T1', floor: 'B1' },   // 제1터미널 B구역
  'T1-C': { terminal: 'T1', floor: 'F2' },   // 제1터미널 C구역 2층
  'CB-3': { terminal: 'CA', floor: 'F3' },   // 탑승동
  'CG-1': { terminal: 'T1', floor: 'F1' },   // 화물터미널
  'T2-S': { terminal: 'T2', floor: 'F3' },   // 제2터미널 보안
  'AP-2': { terminal: 'T1', floor: 'GF' },   // 계류장
  'CT-1': { terminal: 'T1', floor: 'F1' },   // 기내식 센터
};

// AED 원본 데이터 (서버 시작 시 로드)
let aedLocations: AEDLocation[] = [];

export function loadAEDData(data: Array<{ id: string; location: string; terminal: string }>) {
  aedLocations = data.map((d) => {
    const parsed = parseAedId(d.id);
    return {
      ...d,
      floor: parsed.floor,
      area: parsed.area,
    };
  });
  console.log(`[AED] ${aedLocations.length} locations loaded`);
}

/**
 * 작업자 구역 기준으로 가장 가까운 AED 찾기
 * 우선순위: 같은 터미널 + 같은 층 > 같은 터미널 + 다른 층 > 다른 터미널
 */
export function findNearestAED(workerZone: string): { aed: AEDLocation; distance: string; priority: string } | null {
  if (aedLocations.length === 0) return null;

  const zoneInfo = ZONE_TO_TERMINAL[workerZone];
  if (!zoneInfo) {
    // 매핑 없으면 첫 번째 AED 반환
    return { aed: aedLocations[0], distance: '확인 필요', priority: 'low' };
  }

  // 1순위: 같은 터미널 + 같은 층
  const sameFloor = aedLocations.filter((a) => {
    const termMatch = (zoneInfo.terminal === 'T1' && a.terminal === 'T1') ||
                      (zoneInfo.terminal === 'T2' && a.terminal === 'T2') ||
                      (zoneInfo.terminal === 'CA' && a.terminal === '탑승동');
    const floorMap: Record<string, string[]> = {
      'GF': ['F1', 'B1'],
      'B1': ['B1'],
      'B2': ['B2'],
      'F1': ['F1', 'L1'],
      'F2': ['F2'],
      'F3': ['F3'],
    };
    const floors = floorMap[zoneInfo.floor] || [zoneInfo.floor];
    return termMatch && floors.some((f) => a.floor.includes(f) || a.id.includes(f));
  });

  if (sameFloor.length > 0) {
    return { aed: sameFloor[0], distance: '약 30~50m', priority: 'high' };
  }

  // 2순위: 같은 터미널
  const sameTerminal = aedLocations.filter((a) => {
    return (zoneInfo.terminal === 'T1' && a.terminal === 'T1') ||
           (zoneInfo.terminal === 'T2' && a.terminal === 'T2') ||
           (zoneInfo.terminal === 'CA' && a.terminal === '탑승동');
  });

  if (sameTerminal.length > 0) {
    return { aed: sameTerminal[0], distance: '약 100~200m', priority: 'medium' };
  }

  // 3순위: 아무거나
  return { aed: aedLocations[0], distance: '약 300m+', priority: 'low' };
}

/** 전체 AED 목록 */
export function getAllAEDs(): AEDLocation[] {
  return aedLocations;
}

// 초기 데이터 로드 (하드코딩 일부 — 실제로는 JSON 파일에서 로드)
export function initAED() {
  // JSON 파일은 client/public/assets에 있고, 서버에서도 사용
  try {
    const fs = require('fs');
    const path = require('path');
    const jsonPath = path.resolve(__dirname, '../../client/public/assets/aed-locations.json');
    if (fs.existsSync(jsonPath)) {
      const data = JSON.parse(fs.readFileSync(jsonPath, 'utf-8'));
      loadAEDData(data);
    } else {
      console.log('[AED] JSON file not found, using sample data');
      loadSampleData();
    }
  } catch {
    loadSampleData();
  }
}

function loadSampleData() {
  loadAEDData([
    { id: 'T1-B1-L1', location: '제1여객터미널 지하1층 중앙홀', terminal: 'T1' },
    { id: 'T1-F1-A1', location: '제1여객터미널 1층 동측 도착장', terminal: 'T1' },
    { id: 'T1-F3-A1', location: '제1여객터미널 3층 동측 출발장', terminal: 'T1' },
    { id: 'T2-B1-L1', location: '제2여객터미널 지하1층 대합실', terminal: 'T2' },
    { id: 'T2-F1-A1', location: '제2여객터미널 1층 도착장', terminal: 'T2' },
    { id: 'CA-B1-A1', location: '탑승동 지하1층 셔틀트레인 플랫폼', terminal: '탑승동' },
    { id: 'CA-F2-A1', location: '탑승동 2층 탑승교', terminal: '탑승동' },
  ]);
}
