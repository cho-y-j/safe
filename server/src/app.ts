import dotenv from 'dotenv';
dotenv.config({ path: '../.env' });

import express from 'express';
import cors from 'cors';
import http from 'http';
import { Server as SocketServer } from 'socket.io';
import { initDatabase } from './models';
import { initRedis } from './services/redis';
import { startSchedulers } from './services/scheduler';
import { setupWebSocket } from './services/websocket';
import { startWearableSimulator } from './services/simulator/wearable';
import { initAED } from './services/aed';
import { startAutoAlertEngine } from './services/autoAlert';
import publicDataRoutes from './routes/publicData';
import aedRoutes from './routes/aed';
import adminRoutes from './routes/admin';
import workerRoutes from './routes/workers';
import alertRoutes from './routes/alerts';
import scenarioRoutes from './routes/scenarios';
import messageRoutes from './routes/messages';

dotenv.config();

const app = express();
const server = http.createServer(app);
const io = new SocketServer(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] },
});

app.use(cors());
app.use(express.json());

// Routes
app.use('/api/public-data', publicDataRoutes);
app.use('/api/workers', workerRoutes);
app.use('/api/alerts', alertRoutes);
app.use('/api/scenarios', scenarioRoutes);
app.use('/api/aed', aedRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api/messages', messageRoutes);

// Health check
app.get('/api/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

const PORT = parseInt(process.env.PORT || '4000', 10);

async function bootstrap() {
  try {
    await initDatabase();
    console.log('[DB] PostgreSQL connected');

    await initRedis();
    console.log('[Redis] Connected');

    setupWebSocket(io);
    console.log('[WebSocket] Ready');

    startSchedulers(io);
    console.log('[Scheduler] Public data fetchers started');

    initAED();

    startWearableSimulator(io);
    console.log('[Simulator] Wearable simulator started');

    startAutoAlertEngine(io);
    console.log('[AutoAlert] AI auto-alert engine started');

    server.listen(PORT, () => {
      console.log(`[SafePulse Server] Running on port ${PORT}`);
    });
  } catch (err) {
    console.error('[Bootstrap] Failed:', err);
    process.exit(1);
  }
}

bootstrap();

export { io };
