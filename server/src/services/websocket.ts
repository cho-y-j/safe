import { Server as SocketServer } from 'socket.io';
import { latestPublicData, latestInsight } from './scheduler';

export function setupWebSocket(io: SocketServer) {
  io.on('connection', (socket) => {
    console.log(`[WS] Client connected: ${socket.id}`);

    // 연결 즉시 최신 데이터 전송
    if (latestPublicData.airQuality) {
      socket.emit('publicData:update', {
        airQuality: latestPublicData.airQuality,
        weather: latestPublicData.weather,
        flights: latestPublicData.flights,
        forecast: latestPublicData.forecast,
        timestamp: new Date().toISOString(),
      });
    }

    if (latestInsight) {
      socket.emit('ai:insight', latestInsight);
    }

    socket.on('disconnect', () => {
      console.log(`[WS] Client disconnected: ${socket.id}`);
    });
  });
}
