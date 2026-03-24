"""
혼잡 예측 모델
- 운항 스케줄 + 여객 예고 → 구역별 혼잡도 예측
- 향후 2~3시간 예측
"""
import numpy as np


# 인천공항 주요 구역
ZONES = [
    {"id": "T1-A", "name": "제1터미널 A구역", "type": "terminal", "capacity": 50},
    {"id": "T1-B", "name": "제1터미널 B구역", "type": "terminal", "capacity": 45},
    {"id": "T1-C", "name": "제1터미널 C구역", "type": "terminal", "capacity": 45},
    {"id": "T2-S", "name": "제2터미널 보안구역", "type": "terminal", "capacity": 40},
    {"id": "CB-3", "name": "탑승동 연결통로", "type": "passage", "capacity": 30},
    {"id": "CG-1", "name": "화물터미널 1구역", "type": "cargo", "capacity": 35},
    {"id": "CT-1", "name": "기내식 센터", "type": "service", "capacity": 20},
    {"id": "AP-2", "name": "계류장 2구역", "type": "apron", "capacity": 25},
    {"id": "R2-S", "name": "제2활주로 남단", "type": "runway", "capacity": 15},
]

# 구역 유형별 운항-혼잡 연관도
ZONE_FLIGHT_WEIGHT = {
    "terminal": 0.8,
    "cargo": 0.6,
    "service": 0.5,
    "passage": 0.7,
    "apron": 0.9,
    "runway": 0.4,
}


def predict_congestion(
    hourly_flights: list[int],
    hourly_passengers: list[int],
    current_hour: int,
) -> dict:
    """
    구역별 혼잡도 예측

    Returns:
        zones: 각 구역의 현재 + 예측 혼잡도
        overallLevel: 전체 혼잡 레벨
        peakPrediction: 피크 시간대 예측
        recommendations: 인력 배치 권고
    """
    # 향후 3시간 예측
    predictions = []
    for h_offset in range(4):  # 현재 + 3시간
        hour = (current_hour + h_offset) % 24
        flights = hourly_flights[hour] if hour < len(hourly_flights) else 10
        passengers = hourly_passengers[hour] if hour < len(hourly_passengers) else 3000

        zone_data = []
        for zone in ZONES:
            weight = ZONE_FLIGHT_WEIGHT.get(zone["type"], 0.5)

            # 혼잡도 계산 (0~100)
            flight_factor = (flights / 40) * weight * 50  # 운항 기반
            passenger_factor = (passengers / 10000) * weight * 30  # 여객 기반
            time_factor = _time_weight(hour) * 20  # 시간대 보정
            noise = np.random.uniform(-3, 3)

            congestion = min(100, max(0, flight_factor + passenger_factor + time_factor + noise))

            level = "여유"
            if congestion >= 80:
                level = "매우혼잡"
            elif congestion >= 60:
                level = "혼잡"
            elif congestion >= 40:
                level = "보통"

            zone_data.append({
                "zoneId": zone["id"],
                "zoneName": zone["name"],
                "zoneType": zone["type"],
                "capacity": zone["capacity"],
                "congestion": round(congestion, 1),
                "level": level,
                "currentWorkers": max(1, int(zone["capacity"] * np.random.uniform(0.3, 0.8))),
                "recommendedMax": zone["capacity"],
            })

        predictions.append({
            "hour": hour,
            "offset": h_offset,
            "zones": zone_data,
            "flights": flights,
            "passengers": passengers,
        })

    # 전체 혼잡 레벨
    current_zones = predictions[0]["zones"]
    avg_congestion = np.mean([z["congestion"] for z in current_zones])
    max_congestion = max(z["congestion"] for z in current_zones)

    overall_level = "여유"
    if max_congestion >= 80:
        overall_level = "매우혼잡"
    elif max_congestion >= 60:
        overall_level = "혼잡"
    elif avg_congestion >= 40:
        overall_level = "보통"

    # 피크 예측
    peak_hour = max(range(4), key=lambda i: np.mean([z["congestion"] for z in predictions[i]["zones"]]))

    # 인력 재배치 권고
    recommendations = []
    overcrowded = [z for z in current_zones if z["congestion"] >= 70]
    underutilized = [z for z in current_zones if z["congestion"] < 30]

    for oz in overcrowded:
        recommendations.append({
            "type": "rebalance",
            "from": underutilized[0]["zoneName"] if underutilized else "여유 구역",
            "to": oz["zoneName"],
            "reason": f"{oz['zoneName']} 혼잡도 {oz['congestion']:.0f}% — 인력 보강 필요",
        })

    return {
        "predictions": predictions,
        "overallLevel": overall_level,
        "avgCongestion": round(avg_congestion, 1),
        "maxCongestion": round(max_congestion, 1),
        "peakPrediction": {
            "hour": predictions[peak_hour]["hour"],
            "offsetHours": peak_hour,
        },
        "recommendations": recommendations,
    }


def _time_weight(hour: int) -> float:
    """시간대별 혼잡 가중치"""
    weights = [0.1, 0.05, 0.02, 0.02, 0.05, 0.15, 0.4, 0.7, 0.85, 0.8, 0.65, 0.55,
               0.6, 0.65, 0.8, 0.9, 0.75, 0.6, 0.5, 0.4, 0.3, 0.25, 0.2, 0.15]
    return weights[hour % 24]
