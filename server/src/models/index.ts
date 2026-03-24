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
  declare photo: string | null;
  declare medicalHistory: string | null;
  declare isActive: boolean;
}

Worker.init(
  {
    id: { type: DataTypes.STRING(10), primaryKey: true },
    name: { type: DataTypes.STRING(50), allowNull: false },
    role: { type: DataTypes.STRING(50), allowNull: false },
    zone: { type: DataTypes.STRING(20), allowNull: false },
    location: { type: DataTypes.STRING(100), allowNull: false },
    floor: { type: DataTypes.STRING(10), defaultValue: '1F' },
    photo: { type: DataTypes.STRING(255), allowNull: true },
    medicalHistory: { type: DataTypes.TEXT, allowNull: true },
    isActive: { type: DataTypes.BOOLEAN, defaultValue: true },
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
    { id: 'W-001', name: '김민수', role: '활주로 정비', zone: 'R2-S', location: '제2활주로 남단', floor: 'GF', medicalHistory: null },
    { id: 'W-002', name: '이서준', role: '수하물 처리', zone: 'T1-B', location: '제1터미널 B구역', floor: 'B1', medicalHistory: null },
    { id: 'W-003', name: '박지훈', role: '시설 관리', zone: 'CB-3', location: '탑승동 연결통로', floor: '3F', medicalHistory: null },
    { id: 'W-004', name: '최영호', role: '화물 처리', zone: 'CG-1', location: '화물터미널 1구역', floor: '1F', medicalHistory: '호흡기 질환 이력' },
    { id: 'W-005', name: '정수빈', role: '보안 검색', zone: 'T2-S', location: '제2터미널 보안구역', floor: '3F', medicalHistory: null },
    { id: 'W-006', name: '강현우', role: '계류장 유도', zone: 'AP-2', location: '계류장 2구역', floor: 'GF', medicalHistory: null },
    { id: 'W-007', name: '윤재현', role: '기내식 운반', zone: 'CT-1', location: '기내식 센터', floor: '1F', medicalHistory: null },
    { id: 'W-008', name: '한미래', role: '청소 관리', zone: 'T1-C', location: '제1터미널 C구역', floor: '2F', medicalHistory: '고혈압' },
  ];

  await Worker.bulkCreate(workers);
  console.log('[Seed] 8 workers created');
}

export { sequelize };
