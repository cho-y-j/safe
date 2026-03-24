"""
피로도 예측 엔진
- HRV 트렌드 분석
- 연속 작업시간 + 환경 조건 → 피로도 점수
- 특허: AI 적응형 생체 베이스라인 학습
"""
import numpy as np


def predict_fatigue(
    worker_id: str,
    hrv_history: list[float],
    hr_history: list[float],
    work_minutes: float,
    stress: float,
    temp: float = 20.0,
    pm25: float = 15.0,
) -> dict:
    """
    피로도 예측 (0~100)

    Returns:
        fatigueScore: 피로도 점수 (0~100)
        level: 피로 단계
        timeToRest: 권장 휴식까지 남은 시간 (분)
        recommendation: 권고사항
        hrvTrend: HRV 변화 추세
    """
    score = 0
    factors = []

    # 1. 연속 작업시간 기반 (최대 35점)
    work_hours = work_minutes / 60
    if work_hours >= 6:
        score += 35
        factors.append(f"연속 작업 {work_hours:.1f}시간 (6시간 초과)")
    elif work_hours >= 4:
        score += 25
        factors.append(f"연속 작업 {work_hours:.1f}시간")
    elif work_hours >= 2:
        score += 15
        factors.append(f"연속 작업 {work_hours:.1f}시간")
    else:
        score += work_hours * 5

    # 2. HRV 트렌드 분석 (최대 25점)
    hrv_trend = "stable"
    if len(hrv_history) >= 3:
        recent = np.mean(hrv_history[-3:])
        earlier = np.mean(hrv_history[:3]) if len(hrv_history) >= 6 else np.mean(hrv_history)

        if recent < earlier * 0.8:
            hrv_trend = "decreasing"
            score += 25
            factors.append(f"HRV 감소 추세 ({earlier:.0f}→{recent:.0f}ms)")
        elif recent < earlier * 0.9:
            hrv_trend = "slightly_decreasing"
            score += 15
            factors.append(f"HRV 소폭 감소 ({earlier:.0f}→{recent:.0f}ms)")
        else:
            score += 5
    else:
        score += 5

    # 3. 현재 스트레스 수준 (최대 20점)
    if stress > 80:
        score += 20
        factors.append(f"스트레스 매우 높음 ({stress:.0f})")
    elif stress > 60:
        score += 12
        factors.append(f"스트레스 높음 ({stress:.0f})")
    elif stress > 40:
        score += 6

    # 4. 환경 조건 보정 (최대 20점)
    env_penalty = 0
    if temp >= 33:
        env_penalty += 12
        factors.append(f"고온 환경 ({temp}°C)")
    elif temp >= 30:
        env_penalty += 6
    elif temp <= 0:
        env_penalty += 8
        factors.append(f"저온 환경 ({temp}°C)")

    if pm25 >= 35:
        env_penalty += 8
        factors.append(f"대기질 나쁨 (PM2.5 {pm25}㎍/㎥)")
    elif pm25 >= 15:
        env_penalty += 3

    score += min(20, env_penalty)

    # 최종 점수
    fatigue_score = min(100, max(0, score))

    # 피로 단계
    if fatigue_score >= 80:
        level = "위험"
        recommendation = "즉시 작업 중단 및 휴식 필요. 교대 인원 배치 권고."
        time_to_rest = 0
    elif fatigue_score >= 60:
        level = "높음"
        recommendation = "10분 이내 휴식 권고. 작업 강도 낮추기."
        time_to_rest = 10
    elif fatigue_score >= 40:
        level = "보통"
        recommendation = "30분 이내 휴식 권고. 수분 섭취 필요."
        time_to_rest = 30
    else:
        level = "낮음"
        recommendation = "정상 범위. 정기 휴식 시간 준수."
        time_to_rest = max(0, 60 - int(work_minutes % 60))

    return {
        "workerId": worker_id,
        "fatigueScore": round(fatigue_score, 1),
        "level": level,
        "timeToRest": time_to_rest,
        "recommendation": recommendation,
        "hrvTrend": hrv_trend,
        "factors": factors,
        "workHours": round(work_hours, 1),
    }
