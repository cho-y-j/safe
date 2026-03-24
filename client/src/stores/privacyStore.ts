import { create } from 'zustand';

interface PrivacyState {
  privacyMode: boolean;        // true = 개인정보 보호 (원시 데이터 숨김)
  togglePrivacy: () => void;
}

export const usePrivacyStore = create<PrivacyState>((set) => ({
  privacyMode: true,  // 기본값: 보호 모드 ON
  togglePrivacy: () => set((s) => ({ privacyMode: !s.privacyMode })),
}));
