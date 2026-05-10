import { create } from 'zustand'

export interface PlayerSummary {
  playerId: string
  nickname: string
  isHost: boolean
}

export interface SessionState {
  sessionId: string | null
  inviteCode: string | null
  nickname: string | null
  isHost: boolean
  isHostConfirmed: boolean
  playerId: string | null
  phase: string | null
  state: string | null
  turnOrder: string[] | null
  players: PlayerSummary[]
  requiredCharacterCount: number | null
  joinedCount: number | null
  tutorialAckedCount: number | null
  tutorialTotalCount: number | null
  myTutorialAcked: boolean
  roundNumber: number | null
  roundPrompt: string | null
  roundCommonHint: string | null
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
  isHostConfirmed: false,
  playerId: null,
  phase: null,
  state: null,
  turnOrder: null,
  players: [],
  requiredCharacterCount: null,
  joinedCount: null,
  tutorialAckedCount: null,
  tutorialTotalCount: null,
  myTutorialAcked: false,
  roundNumber: null,
  roundPrompt: null,
  roundCommonHint: null,
}

export const useSessionStore = create<SessionState & SessionActions>((set) => ({
  ...initialState,
  setSession: (patch) => set((state) => ({ ...state, ...patch })),
  reset: () => set(initialState),
}))
