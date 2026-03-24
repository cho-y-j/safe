"""
공공데이터 + 센서 데이터 융합 위험도 분석 엔진
- 기상 위험도 (기온/체감온도/풍속)
- 대기질 위험도 (PM2.5, O₃ 등)
- 운항 위험도 (운항편수 → 작업강도)
- 생체 위험도 (심박/SpO₂/피로도)
→ 총합 0~100, 4단계: 안전/주의/경고/위험
"""


def calc_weather_score(temp: float, feels_like: float, wind_speed: float) -> dict:
    """기상 데이터 → 위험도 점수 (0~40)"""
    score = 0
    factors = []

    # 기온 기반
    if temp >= 35:
        score += 35
        factors.append(f"극심한 폭염 ({temp}°C)")
    elif temp >= 33:
        score += 28
        factors.append(f"폭염 경고 ({temp}°C)")
    elif temp >= 30:
        score += 18
        factors.append(f"고온 주의 ({temp}°C)")
    elif temp <= -10:
        score += 35
        factors.append(f"극심한 혹한 ({temp}°C)")
    elif temp <= -5:
        score += 28
        factors.append(f"혹한 경고 ({temp}°C)")
    elif temp <= 0:
        score += 18
        factors.append(f"저온 주의 ({temp}°C)")
    else:
        score += 3

    # 체감온도 보정
    diff = abs(feels_like - temp)
    if diff > 5:
        score += 5
        factors.append(f"체감온도 차이 {diff:.1f}°C")

    # 풍속
    if wind_speed > 15:
        score += 8
        factors.append(f"강풍 ({wind_speed}m/s)")
    elif wind_speed > 10:
        score += 4
        factors.append(f"강한 바람 ({wind_speed}m/s)")

    return {"score": min(40, score), "factors": factors, "category": "기상"}


def calc_air_quality_score(pm25: float, pm10: float, o3: float, co: float, no2: float, so2: float) -> dict:
    """대기질 데이터 → 위험도 점수 (0~35)"""
    score = 0
    factors = []

    # PM2.5 (가장 중요)
    if pm25 >= 75:
        score += 30
        factors.append(f"PM2.5 매우나쁨 ({pm25}㎍/㎥)")
    elif pm25 >= 35:
        score += 20
        factors.append(f"PM2.5 나쁨 ({pm25}㎍/㎥)")
    elif pm25 >= 15:
        score += 8
        factors.append(f"PM2.5 보통 ({pm25}㎍/㎥)")
    else:
        score += 2

    # O₃ 오존
    if o3 >= 0.15:
        score += 8
        factors.append(f"오존 위험 ({o3}ppm)")
    elif o3 >= 0.09:
        score += 5
        factors.append(f"오존 나쁨 ({o3}ppm)")

    # CO
    if co >= 9:
        score += 5
        factors.append(f"CO 높음 ({co}ppm)")

    return {"score": min(35, score), "factors": factors, "category": "대기질"}


def calc_flight_score(flight_total: int, forecast_passengers: int) -> dict:
    """운항 + 여객 데이터 → 작업강도 점수 (0~25)"""
    score = 0
    factors = []

    if flight_total >= 40:
        score += 20
        factors.append(f"운항 극심 피크 ({flight_total}편)")
    elif flight_total >= 30:
        score += 15
        factors.append(f"운항 피크 ({flight_total}편)")
    elif flight_total >= 20:
        score += 10
        factors.append(f"운항 바쁨 ({flight_total}편)")
    else:
        score += 3

    if forecast_passengers >= 8000:
        score += 8
        factors.append(f"여객 매우혼잡 ({forecast_passengers:,}명)")
    elif forecast_passengers >= 5000:
        score += 5
        factors.append(f"여객 혼잡 ({forecast_passengers:,}명)")

    return {"score": min(25, score), "factors": factors, "category": "작업강도"}


def calc_bio_score(workers: list) -> dict:
    """작업자 생체 데이터 → 위험도 점수 (0~30)"""
    score = 0
    factors = []
    abnormal_count = 0

    if not workers:
        return {"score": 0, "factors": ["작업자 데이터 없음"], "category": "작업자 건강"}

    max_hr = max(w.heartRate for w in workers)
    min_spo2 = min(w.spo2 for w in workers)
    max_stress = max(w.stress for w in workers)
    avg_fatigue = sum(w.fatigue for w in workers) / len(workers)

    for w in workers:
        if w.heartRate > 110 or w.spo2 < 94 or w.stress > 70:
            abnormal_count += 1

    # 심박수
    if max_hr >= 130:
        score += 15
        factors.append(f"최고 심박 위험 ({max_hr:.0f}bpm)")
    elif max_hr >= 110:
        score += 10
        factors.append(f"최고 심박 주의 ({max_hr:.0f}bpm)")
    elif max_hr >= 95:
        score += 5

    # SpO₂
    if min_spo2 < 90:
        score += 15
        factors.append(f"산소포화도 위험 ({min_spo2:.0f}%)")
    elif min_spo2 < 94:
        score += 10
        factors.append(f"산소포화도 저하 ({min_spo2:.0f}%)")

    # 이상자 비율
    if abnormal_count >= 2:
        score += 5
        factors.append(f"이상 작업자 {abnormal_count}명 감지")

    # 피로도
    if avg_fatigue > 70:
        score += 5
        factors.append(f"전체 평균 피로도 높음 ({avg_fatigue:.0f}%)")

    return {"score": min(30, score), "factors": factors, "category": "작업자 건강", "abnormalCount": abnormal_count}


def determine_level(total_score: int) -> dict:
    if total_score >= 70:
        return {"level": "위험", "color": "#EF5350", "action": "즉시 대응 필요"}
    elif total_score >= 50:
        return {"level": "경고", "color": "#FF9800", "action": "주의 강화 및 선제 조치"}
    elif total_score >= 30:
        return {"level": "주의", "color": "#42A5F5", "action": "모니터링 강화"}
    else:
        return {"level": "안전", "color": "#66BB6A", "action": "정상 운영"}


def analyze_risk(public_data, workers: list) -> dict:
    weather = calc_weather_score(
        public_data.temp, public_data.feelsLike, public_data.windSpeed
    )
    air = calc_air_quality_score(
        public_data.pm25, public_data.pm10, public_data.o3,
        public_data.co, public_data.no2, public_data.so2,
    )
    flight = calc_flight_score(public_data.flightTotal, public_data.forecastPassengers)
    bio = calc_bio_score(workers)

    total_score = min(100, weather["score"] + air["score"] + flight["score"] + bio["score"])
    level_info = determine_level(total_score)

    # 시나리오 매칭
    scenarios = []
    if public_data.temp >= 33 and any(w.heartRate > 100 for w in workers):
        scenarios.append({
            "name": "폭염 + 작업자 과열",
            "recommendation": "야외 작업자 교대주기 60분→30분 단축, 즉시 휴식 권고",
        })
    if public_data.pm25 >= 35:
        scenarios.append({
            "name": "대기질 악화",
            "recommendation": "전체 야외 작업자 마스크 착용, 호흡기 질환 이력자 SpO₂ 강화 모니터링",
        })
    if public_data.flightTotal >= 30:
        scenarios.append({
            "name": "피크 시간대 과로 위험",
            "recommendation": "수하물/화물 구역 인력 재배치, 교대주기 자동 조정",
        })

    return {
        "totalScore": total_score,
        **level_info,
        "breakdown": {
            "weather": weather,
            "airQuality": air,
            "flight": flight,
            "bio": bio,
        },
        "scenarios": scenarios,
        "allFactors": weather["factors"] + air["factors"] + flight["factors"] + bio["factors"],
    }
