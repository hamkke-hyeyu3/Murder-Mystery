import { create } from 'zustand'
import type { OccupancyView, PrivateTalkParticipant, RevealView, ScenarioLocationView, SessionViewResponse, VoteView } from '@/types/session'

export type CurrentPrivateTalk = {
  requestId: string
  participants: PrivateTalkParticipant[]
  startedAt: number
}

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
  // turn state
  currentTurnIndex: number | null
  currentTurnPlayerId: string | null
  currentTurnCharacterId: string | null
  currentTurnDeadlineAt: number | null
  currentRoundCandidateLocationIds: string[]
  locationOccupancy: OccupancyView[]
  scenarioLocations: ScenarioLocationView[]
  currentPrivateTalk: CurrentPrivateTalk | null
  vote: VoteView | null
  reveal: RevealView | null
  myMissionChecked: boolean
  missionCheckedCount: number | null
  missionTotalCount: number | null
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
  currentTurnIndex: null,
  currentTurnPlayerId: null,
  currentTurnCharacterId: null,
  currentTurnDeadlineAt: null,
  currentRoundCandidateLocationIds: [],
  locationOccupancy: [],
  scenarioLocations: [],
  currentPrivateTalk: null,
  vote: null,
  reveal: null,
  myMissionChecked: false,
  missionCheckedCount: null,
  missionTotalCount: null,
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
    locationOccupancy: snap.locationOccupancy ?? state.locationOccupancy,
    scenarioLocations: snap.locations ?? state.scenarioLocations,
    ...(snap.currentTurn != null ? {
      currentTurnIndex: snap.currentTurn.turnIndex,
      currentTurnPlayerId: snap.currentTurn.playerId,
      currentTurnCharacterId: snap.currentTurn.characterId,
      currentTurnDeadlineAt: snap.currentTurn.deadlineAt,
      currentRoundCandidateLocationIds: snap.currentTurn.candidateLocationIds,
    } : {}),
    vote: snap.vote ?? state.vote,
    reveal: snap.reveal ?? state.reveal,
  })),
  reset: () => set(initialState),
}))
