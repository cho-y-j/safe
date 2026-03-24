from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
from analyzers.risk_fusion import analyze_risk
from analyzers.collective import analyze_collective
from analyzers.congestion import predict_congestion
from analyzers.fatigue import predict_fatigue
from analyzers.llm_insight import generate_insight

app = FastAPI(title="SafePulse AI Engine", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ─── 요청/응답 모델 ───

class PublicDataInput(BaseModel):
    temp: float = 20.0
    feelsLike: float = 20.0
    humidity: float = 60.0
    windSpeed: float = 3.0
    pm25: float = 15.0
    pm10: float = 30.0
    o3: float = 0.03
    co: float = 0.5
    no2: float = 0.02
    so2: float = 0.003
    flightTotal: int = 15
    flightArriving: int = 8
    forecastPassengers: int = 3000


class WorkerInput(BaseModel):
    id: str
    heartRate: float
    bodyTemp: float
    spo2: float
    stress: float
    hrv: float
    zone: str
    workMinutes: float = 0
    fatigue: float = 0
    medicalHistory: Optional[str] = None


class RiskAnalysisRequest(BaseModel):
    publicData: PublicDataInput
    workers: list[WorkerInput]


class CollectiveAnalysisRequest(BaseModel):
    workers: list[WorkerInput]
    timeWindowSeconds: int = 60
    radiusMeters: float = 30.0
    minAffected: int = 3


class CongestionRequest(BaseModel):
    hourlyFlights: list[int]
    hourlyPassengers: list[int]
    currentHour: int


class FatigueRequest(BaseModel):
    workerId: str
    hrvHistory: list[float]
    heartRateHistory: list[float]
    workMinutes: float
    stress: float
    temp: float = 20.0
    pm25: float = 15.0


# ─── 엔드포인트 ───

@app.get("/health")
def health():
    return {"status": "ok", "engine": "SafePulse AI"}


@app.post("/analyze/risk")
def analyze_risk_endpoint(req: RiskAnalysisRequest):
    return analyze_risk(req.publicData, req.workers)


@app.post("/analyze/collective")
def analyze_collective_endpoint(req: CollectiveAnalysisRequest):
    return analyze_collective(
        req.workers,
        time_window=req.timeWindowSeconds,
        radius=req.radiusMeters,
        min_affected=req.minAffected,
    )


@app.post("/predict/congestion")
def predict_congestion_endpoint(req: CongestionRequest):
    return predict_congestion(
        req.hourlyFlights,
        req.hourlyPassengers,
        req.currentHour,
    )


@app.post("/predict/fatigue")
def predict_fatigue_endpoint(req: FatigueRequest):
    return predict_fatigue(
        worker_id=req.workerId,
        hrv_history=req.hrvHistory,
        hr_history=req.heartRateHistory,
        work_minutes=req.workMinutes,
        stress=req.stress,
        temp=req.temp,
        pm25=req.pm25,
    )


class InsightRequest(BaseModel):
    publicData: dict
    workers: list[dict]
    riskScore: Optional[dict] = None


@app.post("/analyze/insight")
def analyze_insight_endpoint(req: InsightRequest):
    """LLM 기반 AI 인사이트 — 공공데이터+센서 종합 자연어 분석"""
    return generate_insight(req.publicData, req.workers, req.riskScore)
