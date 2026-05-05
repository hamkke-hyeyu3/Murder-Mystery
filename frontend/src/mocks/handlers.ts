import { http, HttpResponse } from 'msw'
import type { ScenarioSummary } from '@/types/scenario'
import type { CreateSessionResponse } from '@/types/session'

export const defaultScenario: ScenarioSummary = {
  id: 'toy-manor',
  title: 'Toy Manor 살인 사건',
  icon: '🏚️',
  summary: '고요한 귀족 저택에서 하룻밤 사이 일어난 살인. 셋 중 범인은 누구인가?',
  playerCount: 3,
  estimatedMinutes: 60,
}

export const defaultSessionResponse: CreateSessionResponse = {
  sessionId: 'sess-default',
  inviteCode: '000001',
  scenarioId: 'toy-manor',
  hostNickname: 'alice',
  phase: 'lobby',
  playerId: 'player-default',
}

export const handlers = [
  http.get('/api/scenarios', () => HttpResponse.json([defaultScenario])),
  http.post('/api/sessions', () => HttpResponse.json(defaultSessionResponse)),
]
