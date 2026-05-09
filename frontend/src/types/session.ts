export type CreateSessionRequest = {
  scenarioId: string
  hostNickname: string
}

export type CreateSessionResponse = {
  sessionId: string
  inviteCode: string
  scenarioId: string
  hostNickname: string
  phase: string
  playerId: string
}

export type LastSession = {
  sessionId: string
  inviteCode: string
  nickname: string
  isHost: boolean
  playerId: string
  scenarioId: string
  savedAt: number
}

export const LAST_SESSION_KEY = 'mm:lastSession'

export type PlayerSummaryDto = {
  playerId: string
  nickname: string
  isHost: boolean
}

export type JoinSessionResponse = {
  sessionId: string
  inviteCode: string
  scenarioId: string
  phase: string
  nickname: string
  playerId: string
  players: PlayerSummaryDto[]
}

export type SessionViewResponse = {
  sessionId: string
  inviteCode: string
  scenarioId: string
  phase: string
  requiredCharacterCount: number
  joinedCount: number
  players: PlayerSummaryDto[]
}

export type ResumeResponse = {
  sessionId: string
  inviteCode: string
  scenarioId: string
  phase: string
  nickname: string
  playerId: string
  isHost: boolean
  players: PlayerSummaryDto[]
}

export type SessionEvent =
  | {
      type: 'PLAYER_JOINED'
      sessionId: string
      occurredAt: string
      payload: { playerId: string; nickname: string; isHost: boolean }
    }
  | {
      type: 'PLAYER_LEFT'
      sessionId: string
      occurredAt: string
      payload: { playerId: string; nickname: string }
    }
  | {
      type: 'LOBBY_COUNT_CHANGED'
      sessionId: string
      occurredAt: string
      payload: { joined: number; required: number }
    }
