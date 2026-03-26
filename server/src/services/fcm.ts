import admin from 'firebase-admin';
import path from 'path';
import fs from 'fs';

// Firebase Admin 초기화
const keyPath = path.resolve(__dirname, '../../firebase-admin-key.json');
let fcmReady = false;

if (fs.existsSync(keyPath)) {
  try {
    admin.initializeApp({
      credential: admin.credential.cert(keyPath),
    });
    fcmReady = true;
    console.log('[FCM] Firebase Admin initialized');
  } catch (err) {
    console.error('[FCM] Init error:', err);
  }
} else {
  console.warn('[FCM] firebase-admin-key.json not found');
}

export interface FCMMessage {
  title: string;
  body: string;
  type: string;  // rest, shift, safety, weather, zone, emergency, notice
  data?: Record<string, string>;
}

/** 개인에게 푸시 (FCM 토큰으로) */
export async function sendToDevice(token: string, message: FCMMessage): Promise<boolean> {
  if (!fcmReady || !token) return false;
  try {
    await admin.messaging().send({
      token,
      notification: { title: message.title, body: message.body },
      data: { type: message.type, ...message.data },
      android: {
        priority: message.type === 'emergency' ? 'high' : 'normal',
        notification: {
          channelId: message.type === 'emergency' ? 'safepulse_alert' : 'safepulse_push',
        },
      },
    });
    return true;
  } catch (err) {
    console.error('[FCM] Send error:', err);
    return false;
  }
}

/** 토픽에 푸시 (그룹 전송) */
export async function sendToTopic(topic: string, message: FCMMessage): Promise<boolean> {
  if (!fcmReady) return false;
  try {
    await admin.messaging().send({
      topic,
      notification: { title: message.title, body: message.body },
      data: { type: message.type, ...message.data },
    });
    return true;
  } catch (err) {
    console.error('[FCM] Topic send error:', err);
    return false;
  }
}

/** 여러 토큰에 동시 발송 */
export async function sendToMultiple(tokens: string[], message: FCMMessage): Promise<number> {
  if (!fcmReady || tokens.length === 0) return 0;
  try {
    const response = await admin.messaging().sendEachForMulticast({
      tokens,
      notification: { title: message.title, body: message.body },
      data: { type: message.type, ...message.data },
    });
    return response.successCount;
  } catch (err) {
    console.error('[FCM] Multi send error:', err);
    return 0;
  }
}

/** 토큰을 토픽에 구독 */
export async function subscribeToTopic(tokens: string[], topic: string): Promise<void> {
  if (!fcmReady) return;
  try {
    await admin.messaging().subscribeToTopic(tokens, topic);
  } catch (err) {
    console.error('[FCM] Subscribe error:', err);
  }
}

export { fcmReady };
