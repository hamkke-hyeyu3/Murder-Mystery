import { apiPost } from '@/lib/api'
import type { CreateSessionRequest, CreateSessionResponse } from '@/types/session'

export function createSession(
  scenarioId: string,
  hostNickname: string
): Promise<CreateSessionResponse> {
  return apiPost<CreateSessionRequest, CreateSessionResponse>('/api/sessions', {
    scenarioId,
    hostNickname,
  })
}
