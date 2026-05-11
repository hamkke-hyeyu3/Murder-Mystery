import { create } from 'zustand'
import type { SessionViewResponse } from '@/types/session'

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
  leftPlayerIds: string[]
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
  hydrateFromSnapshot: (snap: SessionViewResponse) => void
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
  leftPlayerIds: [],
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
  hydrateFromSnapshot: (snap) => set((state) => ({
    ...state,
    sessionId: snap.sessionId,
    inviteCode: snap.inviteCode,
    phase: snap.phase,
    state: snap.state ?? state.state,
    turnOrder: snap.turnOrder ?? state.turnOrder,
    players: snap.players.map((p) => ({ playerId: p.playerId, nickname: p.nickname, isHost: p.isHost })),
    joinedCount: snap.joinedCount,
    requiredCharacterCount: snap.requiredCharacterCount,
    ...(snap.me != null ? {
      playerId: snap.me.playerId,
      nickname: snap.me.nickname,
      isHost: snap.me.isHost,
      isHostConfirmed: true,
      myTutorialAcked: snap.me.tutorialAckedAt != null,
    } : {}),
    ...(snap.round != null ? {
      roundNumber: snap.round.roundNumber,
      roundPrompt: snap.round.prompt,
      roundCommonHint: snap.round.commonHint,
    } : {}),
  })),
  reset: () => set(initialState),
}))
