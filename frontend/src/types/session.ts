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
