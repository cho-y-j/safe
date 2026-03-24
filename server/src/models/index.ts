import { Sequelize, DataTypes, Model } from 'sequelize';

const sequelize = new Sequelize({
  dialect: 'postgres',
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_NAME || 'safepulse',
  username: process.env.DB_USER || 'safepulse',
  password: process.env.DB_PASSWORD || 'safepulse123',
  logging: false,
});

// ─── Worker ───
export class Worker extends Model {
  declare id: string;
  declare name: string;
  declare role: string;
  declare zone: string;
  declare location: string;
  declare floor: string;
  declare company: string;         // 소속사
  declare department: string;      // 부서
  declare shiftGroup: string;      // 근무조 (A/B/C조)
  declare employeeType: string;    // 고용형태 (정규직/계약직/일용직)
  declare wearableId: string | null; // 페어링된 웨어러블 기기 ID
  declare wearableStatus: string;  // 기기 상태 (paired/unpaired/charging/offline)
  declare photo: string | null;
  declare medicalHistory: string | null;
  declare emergencyContact: string | null;
  declare isActive: boolean;
  declare lastCheckIn: Date | null;
}

Worker.init(
  {
    id: { type: DataTypes.STRING(10), primaryKey: true },
    name: { type: DataTypes.STRING(50), allowNull: false },
    role: { type: DataTypes.STRING(50), allowNull: false },
    zone: { type: DataTypes.STRING(20), allowNull: false },
    location: { type: DataTypes.STRING(100), allowNull: false },
    floor: { type: DataTypes.STRING(10), defaultValue: '1F' },
    company: { type: DataTypes.STRING(50), defaultValue: '인천국제공항공사' },
    department: { type: DataTypes.STRING(50), defaultValue: '시설관리팀' },
    shiftGroup: { type: DataTypes.STRING(10), defaultValue: 'A조' },
    employeeType: { type: DataTypes.STRING(20), defaultValue: '정규직' },
    wearableId: { type: DataTypes.STRING(20), allowNull: true },
    wearableStatus: { type: DataTypes.STRING(20), defaultValue: 'paired' },
    photo: { type: DataTypes.STRING(255), allowNull: true },
    medicalHistory: { type: DataTypes.TEXT, allowNull: true },
    emergencyContact: { type: DataTypes.STRING(20), allowNull: true },
    isActive: { type: DataTypes.BOOLEAN, defaultValue: true },
    lastCheckIn: { type: DataTypes.DATE, allowNull: true },
  },
  { sequelize, tableName: 'workers', timestamps: true }
);

// ─── SensorData ───
export class SensorData extends Model {
  declare id: number;
  declare workerId: string;
  declare heartRate: number;
  declare bodyTemp: number;
  declare spo2: number;
  declare stress: number;
  declare hrv: number;
  declare latitude: number;
  declare longitude: number;
}

SensorData.init(
  {
    id: { type: DataTypes.INTEGER, autoIncrement: true, primaryKey: true },
    workerId: { type: DataTypes.STRING(10), allowNull: false, references: { model: Worker, key: 'id' } },
    heartRate: { type: DataTypes.FLOAT, allowNull: false },
    bodyTemp: { type: DataTypes.FLOAT, allowNull: false },
    spo2: { type: DataTypes.FLOAT, allowNull: false },
    stress: { type: DataTypes.FLOAT, defaultValue: 0 },
    hrv: { type: DataTypes.FLOAT, defaultValue: 50 },
    latitude: { type: DataTypes.DOUBLE, defaultValue: 37.4602 },
    longitude: { type: DataTypes.DOUBLE, defaultValue: 126.4407 },
  },
  { sequelize, tableName: 'sensor_data', timestamps: true }
);

// ─── Alert ───
export class Alert extends Model {
  declare id: number;
  declare type: string;
  declare level: string;
  declare message: string;
  declare workerId: string | null;
  declare zone: string | null;
  declare scenario: string | null;
  declare resolved: boolean;
}

Alert.init(
  {
    id: { type: DataTypes.INTEGER, autoIncrement: true, primaryKey: true },
    type: { type: DataTypes.STRING(30), allowNull: false },
    level: { type: DataTypes.ENUM('info', 'caution', 'warning', 'danger'), allowNull: false },
    message: { type: DataTypes.TEXT, allowNull: false },
    workerId: { type: DataTypes.STRING(10), allowNull: true },
    zone: { type: DataTypes.STRING(20), allowNull: true },
    scenario: { type: DataTypes.STRING(30), allowNull: true },
    resolved: { type: DataTypes.BOOLEAN, defaultValue: false },
  },
  { sequelize, tableName: 'alerts', timestamps: true }
);

// ─── PublicDataCache ───
export class PublicDataCache extends Model {
  declare id: number;
  declare dataType: string;
  declare data: object;
  declare fetchedAt: Date;
}

PublicDataCache.init(
  {
    id: { type: DataTypes.INTEGER, autoIncrement: true, primaryKey: true },
    dataType: { type: DataTypes.STRING(30), allowNull: false },
    data: { type: DataTypes.JSONB, allowNull: false },
    fetchedAt: { type: DataTypes.DATE, defaultValue: DataTypes.NOW },
  },
  { sequelize, tableName: 'public_data_cache', timestamps: true }
);

// ─── Relations ───
Worker.hasMany(SensorData, { foreignKey: 'workerId' });
SensorData.belongsTo(Worker, { foreignKey: 'workerId' });
Worker.hasMany(Alert, { foreignKey: 'workerId' });
Alert.belongsTo(Worker, { foreignKey: 'workerId' });

export async function initDatabase() {
  await sequelize.authenticate();
  await sequelize.sync({ alter: true });
  await seedWorkers();
}

async function seedWorkers() {
  const count = await Worker.count();
  if (count > 0) return;

  const workers = [
    { id: 'W-001', name: '김민수', role: '활주로 정비', zone: 'R2-S', location: '제2활주로 남단', floor: 'GF', company: '한국공항', department: '지상운영팀', shiftGroup: 'A조', employeeType: '정규직', wearableId: 'GW4-001', wearableStatus: 'paired', medicalHistory: null, emergencyContact: '010-1234-5678' },
    { id: 'W-002', name: '이서준', role: '수하물 처리', zone: 'T1-B', location: '제1터미널 B구역', floor: 'B1', company: '아시아나에어포트', department: '수하물처리팀', shiftGroup: 'A조', employeeType: '정규직', wearableId: 'GW4-002', wearableStatus: 'paired', medicalHistory: null, emergencyContact: '010-2345-6789' },
    { id: 'W-003', name: '박지훈', role: '시설 관리', zone: 'CB-3', location: '탑승동 연결통로', floor: '3F', company: '인천국제공항공사', department: '시설관리팀', shiftGroup: 'B조', employeeType: '정규직', wearableId: 'GW4-003', wearableStatus: 'paired', medicalHistory: null, emergencyContact: '010-3456-7890' },
    { id: 'W-004', name: '최영호', role: '화물 처리', zone: 'CG-1', location: '화물터미널 1구역', floor: '1F', company: '대한항공 지상조업', department: '화물팀', shiftGroup: 'A조', employeeType: '계약직', wearableId: 'GW4-004', wearableStatus: 'paired', medicalHistory: '호흡기 질환 이력', emergencyContact: '010-4567-8901' },
    { id: 'W-005', name: '정수빈', role: '보안 검색', zone: 'T2-S', location: '제2터미널 보안구역', floor: '3F', company: '인천공항보안', department: '보안검색팀', shiftGroup: 'B조', employeeType: '정규직', wearableId: 'GW4-005', wearableStatus: 'paired', medicalHistory: null, emergencyContact: '010-5678-9012' },
    { id: 'W-006', name: '강현우', role: '계류장 유도', zone: 'AP-2', location: '계류장 2구역', floor: 'GF', company: '한국공항', department: '지상운영팀', shiftGroup: 'A조', employeeType: '정규직', wearableId: 'GW4-006', wearableStatus: 'paired', medicalHistory: null, emergencyContact: '010-6789-0123' },
    { id: 'W-007', name: '윤재현', role: '기내식 운반', zone: 'CT-1', location: '기내식 센터', floor: '1F', company: 'LSG스카이셰프', department: '기내식팀', shiftGroup: 'C조', employeeType: '계약직', wearableId: 'GW4-007', wearableStatus: 'paired', medicalHistory: null, emergencyContact: '010-7890-1234' },
    { id: 'W-008', name: '한미래', role: '청소 관리', zone: 'T1-C', location: '제1터미널 C구역', floor: '2F', company: '클린에어', department: '환경미화팀', shiftGroup: 'B조', employeeType: '일용직', wearableId: 'GW4-008', wearableStatus: 'paired', medicalHistory: '고혈압', emergencyContact: '010-8901-2345' },
  ];

  await Worker.bulkCreate(workers);
  console.log('[Seed] 8 workers created');
}

export { sequelize };
