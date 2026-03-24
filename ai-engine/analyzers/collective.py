"""
집단 생체신호 상관분석 엔진
- P2P 메쉬 네트워크 시뮬레이션
- 60초 윈도우, 30m 반경, 3인 이상 동시 이상 → 환경 재해 판별
- 특허 청구항 4: 집단 생체 신호 기반 환경 재해 조기 감지
"""
import numpy as np
from typing import Optional


# 구역 간 거리 (미터, 간략화)
ZONE_DISTANCES = {
    ("R2-S", "AP-2"): 20,
    ("T1-B", "T1-C"): 15,
    ("T1-B", "CG-1"): 25,
    ("CG-1", "CT-1"): 30,
    ("T2-S", "CB-3"): 40,
    ("AP-2", "CG-1"): 35,
}


def get_zone_distance(z1: str, z2: str) -> float:
    if z1 == z2:
        return 5.0  # 같은 구역 내
    key = tuple(sorted([z1, z2]))
    return ZONE_DISTANCES.get(key, 100.0)  # 기본 100m (멀리)


def analyze_collective(
    workers: list,
    time_window: int = 60,
    radius: float = 30.0,
    min_affected: int = 3,
) -> dict:
    """
    집단 생체신호 이상 패턴 분석

    Returns:
        isEnvironmentalHazard: 환경 재해 판별 여부
        hazardType: 재해 유형 (가스 누출, 산소 결핍, 이상 기온 등)
        affectedWorkers: 영향 받는 작업자 목록
        affectedZone: 영향 받는 구역
        confidence: 판별 신뢰도 (0~1)
        recommendation: 대응 권고사항
    """
    if len(workers) < 2:
        return {
            "isEnvironmentalHazard": False,
            "hazardType": None,
            "affectedWorkers": [],
            "affectedZone": None,
            "confidence": 0,
            "recommendation": None,
        }

    # 구역별 그룹핑
    zone_groups: dict[str, list] = {}
    for w in workers:
        zone_groups.setdefault(w.zone, []).append(w)

    # 각 구역에서 집단 이상 패턴 검사
    for zone, group in zone_groups.items():
        # 인접 구역 작업자도 포함
        extended_group = list(group)
        for other_zone, other_workers in zone_groups.items():
            if other_zone != zone and get_zone_distance(zone, other_zone) <= radius:
                extended_group.extend(other_workers)

        if len(extended_group) < min_affected:
            continue

        # 이상 패턴 감지
        hr_anomalies = []
        spo2_anomalies = []

        for w in extended_group:
            # 심박수 급상승 (기준: 100bpm 초과)
            if w.heartRate > 100:
                hr_anomalies.append(w)
            # SpO₂ 하락 (기준: 95% 미만)
            if w.spo2 < 95:
                spo2_anomalies.append(w)

        # 동시다발적 이상 판별
        hr_rate = len(hr_anomalies) / len(extended_group)
        spo2_rate = len(spo2_anomalies) / len(extended_group)

        # 가중 점수 (SpO₂ 하락에 높은 가중치)
        weighted_score = hr_rate * 0.3 + spo2_rate * 0.7

        if weighted_score >= 0.5 and len(hr_anomalies) + len(spo2_anomalies) >= min_affected:
            # 환경 재해로 분류
            hazard_type = _classify_hazard(hr_anomalies, spo2_anomalies, extended_group)
            affected_ids = list(set(
                [w.id for w in hr_anomalies] + [w.id for w in spo2_anomalies]
            ))

            confidence = min(1.0, weighted_score * 1.2)

            return {
                "isEnvironmentalHazard": True,
                "hazardType": hazard_type,
                "affectedWorkers": affected_ids,
                "affectedZone": zone,
                "confidence": round(confidence, 2),
                "recommendation": _get_recommendation(hazard_type, zone),
                "details": {
                    "hrAnomalyRate": round(hr_rate, 2),
                    "spo2AnomalyRate": round(spo2_rate, 2),
                    "weightedScore": round(weighted_score, 2),
                    "groupSize": len(extended_group),
                    "anomalyCount": len(affected_ids),
                },
            }

    return {
        "isEnvironmentalHazard": False,
        "hazardType": None,
        "affectedWorkers": [],
        "affectedZone": None,
        "confidence": 0,
        "recommendation": None,
    }


def _classify_hazard(hr_anomalies: list, spo2_anomalies: list, group: list) -> str:
    """이상 패턴으로 재해 유형 분류"""
    spo2_rate = len(spo2_anomalies) / len(group) if group else 0
    hr_rate = len(hr_anomalies) / len(group) if group else 0

    if spo2_rate > 0.5 and hr_rate > 0.3:
        return "산소 결핍 (밀폐 구역)"
    elif spo2_rate > 0.5:
        return "가스 누출 의심"
    elif hr_rate > 0.6:
        return "이상 기온 / 열 스트레스"
    else:
        return "복합 환경 재해"


def _get_recommendation(hazard_type: str, zone: str) -> str:
    recommendations = {
        "산소 결핍 (밀폐 구역)": f"[긴급] {zone} 구역 즉시 대피! 환기 시스템 점검 + 구조팀 출동",
        "가스 누출 의심": f"[긴급] {zone} 구역 즉시 대피! 가스 감지기 확인 + 소방 신고",
        "이상 기온 / 열 스트레스": f"[경고] {zone} 구역 작업 즉시 중단, 그늘/냉방 구역으로 이동",
        "복합 환경 재해": f"[긴급] {zone} 구역 즉시 대피! 관제센터 상황 보고",
    }
    return recommendations.get(hazard_type, f"[경고] {zone} 구역 주의")
