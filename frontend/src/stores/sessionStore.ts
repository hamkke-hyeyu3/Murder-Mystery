import { create } from 'zustand'

export interface PlayerSummary {
  nickname: string
  isHost: boolean
}

export interface SessionState {
  sessionId: string | null
  inviteCode: string | null
  nickname: string | null
  isHost: boolean
  playerId: string | null
  phase: string | null
  players: PlayerSummary[]
  requiredCharacterCount: number | null
  joinedCount: number | null
}

interface SessionActions {
  setSession: (patch: Partial<SessionState>) => void
  reset: () => void
}

const initialState: SessionState = {
  sessionId: null,
  inviteCode: null,
  nickname: null,
  isHost: false,
  playerId: null,
  phase: null,
  players: [],
  requiredCharacterCount: null,
  joinedCount: null,
}

export const useSessionStore = create<SessionState & SessionActions>((set) => ({
  ...initialState,
  setSession: (patch) => set((state) => ({ ...state, ...patch })),
  reset: () => set(initialState),
}))
