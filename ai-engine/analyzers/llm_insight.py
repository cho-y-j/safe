"""
LLM 기반 AI 인사이트 엔진
- DeepSeek API (OpenAI 호환) → 나중에 Claude/GPT로 교체 가능
- 공공데이터 + 센서 데이터를 분석하여 한국어 자연어 인사이트 생성
"""
import os
import json
from openai import OpenAI

# DeepSeek (OpenAI 호환) — base_url만 바꾸면 Claude/GPT 전환 가능
LLM_PROVIDER = os.getenv("LLM_PROVIDER", "deepseek")  # deepseek / openai / anthropic
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat")

def get_client():
    if LLM_PROVIDER == "deepseek":
        return OpenAI(api_key=LLM_API_KEY, base_url="https://api.deepseek.com")
    elif LLM_PROVIDER == "openai":
        return OpenAI(api_key=LLM_API_KEY)
    else:
        return OpenAI(api_key=LLM_API_KEY, base_url="https://api.deepseek.com")


SYSTEM_PROMPT = """당신은 인천국제공항 AI 안전관리 시스템 'SafePulse'의 AI 분석관입니다.

## 역할
- 공공데이터(대기질, 기상, 운항정보)와 작업자 웨어러블 센서 데이터를 실시간으로 분석합니다.
- 복합적인 위험 상황을 판단하고, 관제센터에 구체적인 대응 권고를 제공합니다.
- 한국어로 간결하고 명확하게 답변합니다.

## 분석 원칙
1. 공공데이터와 센서 데이터의 상관관계를 분석하세요
2. 단순 수치 나열이 아닌, 종합적 판단과 구체적 행동 지침을 제공하세요
3. 위험도가 높을수록 더 긴급하고 구체적인 권고를 제공하세요
4. 작업자의 의료 이력이 있다면 반드시 고려하세요
5. 시간대별 운항 패턴과 여객 예고를 고려한 예측을 제공하세요

## 응답 형식
JSON으로 응답하세요:
{
  "summary": "한 줄 종합 판단 (30자 이내)",
  "riskLevel": "안전|주의|경고|위험",
  "analysis": "2~3문장 상세 분석",
  "recommendations": ["구체적 행동 권고 1", "권고 2", "권고 3"],
  "warnings": ["즉시 주의가 필요한 경고 메시지"],
  "prediction": "향후 1~3시간 예측"
}"""


def generate_insight(public_data: dict, workers: list, risk_score: dict = None) -> dict:
    """
    공공데이터 + 작업자 데이터 → LLM 분석 → 자연어 인사이트
    """
    if not LLM_API_KEY:
        return _fallback_insight(public_data, workers, risk_score)

    try:
        client = get_client()

        # 데이터를 프롬프트로 변환
        user_prompt = _build_prompt(public_data, workers, risk_score)

        response = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.3,
            max_tokens=800,
            response_format={"type": "json_object"},
        )

        content = response.choices[0].message.content
        result = json.loads(content)

        # 필수 필드 검증
        result.setdefault("summary", "분석 중...")
        result.setdefault("riskLevel", "주의")
        result.setdefault("analysis", "")
        result.setdefault("recommendations", [])
        result.setdefault("warnings", [])
        result.setdefault("prediction", "")
        result["source"] = "ai"
        result["model"] = LLM_MODEL

        return result

    except Exception as e:
        print(f"[LLM] Error: {e}")
        return _fallback_insight(public_data, workers, risk_score)


def _build_prompt(public_data: dict, workers: list, risk_score: dict = None) -> str:
    from datetime import datetime
    now = datetime.now()

    prompt = f"""## 현재 시각: {now.strftime('%Y년 %m월 %d일 %H:%M')}

## 인천공항 공공데이터 (실시간)
- 대기질: PM2.5 {public_data.get('pm25', '?')}㎍/㎥, PM10 {public_data.get('pm10', '?')}㎍/㎥, O₃ {public_data.get('o3', '?')}ppm, CO {public_data.get('co', '?')}ppm, NO₂ {public_data.get('no2', '?')}ppm, SO₂ {public_data.get('so2', '?')}ppm
- 기상: 기온 {public_data.get('temp', '?')}°C, 체감온도 {public_data.get('feelsLike', '?')}°C, 습도 {public_data.get('humidity', '?')}%, 풍속 {public_data.get('windSpeed', '?')}m/s
- 운항: 현재 시간 도착 {public_data.get('flightArriving', '?')}편, 출발 {public_data.get('flightDeparting', '?')}편, 합계 {public_data.get('flightTotal', '?')}편
- 예상 여객: {public_data.get('forecastPassengers', '?')}명

## 작업자 현황 (웨어러블 센서, {len(workers)}명)
"""
    for w in workers:
        status_str = "정상" if w.get("status") == "normal" else "주의" if w.get("status") == "caution" else "위험"
        medical = f" [의료이력: {w.get('medicalHistory')}]" if w.get("medicalHistory") else ""
        prompt += f"- {w.get('name', '?')} ({w.get('role', '?')}, {w.get('zone', '?')}): 심박 {w.get('heartRate', '?')}bpm, 체온 {w.get('bodyTemp', '?')}°C, SpO₂ {w.get('spo2', '?')}%, 스트레스 {w.get('stress', '?')}, 피로도 {w.get('fatigue', 0):.0f}%, 상태={status_str}{medical}\n"

    if risk_score:
        prompt += f"""
## 현재 AI 위험도 분석
- 종합 점수: {risk_score.get('totalScore', '?')}/100 ({risk_score.get('level', '?')})
- 기상 위험: {risk_score.get('breakdown', {}).get('weather', {}).get('score', '?')}점
- 대기질 위험: {risk_score.get('breakdown', {}).get('airQuality', {}).get('score', '?')}점
- 작업강도: {risk_score.get('breakdown', {}).get('flight', {}).get('score', '?')}점
- 작업자 건강: {risk_score.get('breakdown', {}).get('bio', {}).get('score', '?')}점
"""

    prompt += "\n위 데이터를 종합 분석하여 JSON 형식으로 응답해주세요."
    return prompt


def _fallback_insight(public_data: dict, workers: list, risk_score: dict = None) -> dict:
    """LLM 미연결 시 규칙 기반 폴백"""
    warnings = []
    recommendations = []

    temp = public_data.get("temp", 20)
    pm25 = public_data.get("pm25", 15)
    flights = public_data.get("flightTotal", 15)

    if temp >= 33:
        warnings.append(f"폭염 경고: 기온 {temp}°C — 야외 작업자 열사병 위험")
        recommendations.append("야외 작업 구역 교대주기 60분→30분 단축")
    if pm25 >= 35:
        warnings.append(f"대기질 나쁨: PM2.5 {pm25}㎍/㎥ — 호흡기 위험")
        recommendations.append("전체 야외 작업자 마스크 착용 필수")
    if flights >= 30:
        warnings.append(f"피크 시간대: 운항 {flights}편 — 과로 위험")
        recommendations.append("수하물/화물 구역 인력 보강 필요")

    danger_workers = [w for w in workers if w.get("status") == "danger"]
    if danger_workers:
        for w in danger_workers:
            warnings.append(f"{w.get('name')} ({w.get('role')}) 위험 상태 — 즉시 조치 필요")

    level = "안전"
    if len(warnings) >= 3: level = "위험"
    elif len(warnings) >= 2: level = "경고"
    elif len(warnings) >= 1: level = "주의"

    return {
        "summary": f"종합 {level} — 위험요소 {len(warnings)}건 감지",
        "riskLevel": level,
        "analysis": f"현재 기온 {temp}°C, PM2.5 {pm25}㎍/㎥, 운항 {flights}편 상황에서 작업자 {len(workers)}명을 모니터링 중입니다.",
        "recommendations": recommendations or ["현재 정상 운영 중입니다. 정기 모니터링을 유지하세요."],
        "warnings": warnings,
        "prediction": "향후 1시간 내 큰 변화 없을 것으로 예상됩니다.",
        "source": "rule-based",
        "model": "fallback",
    }
