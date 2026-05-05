import { apiPost } from '@/lib/api'
import type { CreateSessionRequest, CreateSessionResponse, JoinSessionResponse } from '@/types/session'

export function createSession(
  scenarioId: string,
  hostNickname: string
): Promise<CreateSessionResponse> {
  return apiPost<CreateSessionRequest, CreateSessionResponse>('/api/sessions', {
    scenarioId,
    hostNickname,
  })
}

export async function joinSession(
  inviteCode: string,
  nickname: string
): Promise<JoinSessionResponse> {
  const res = await fetch(`/api/sessions/${inviteCode}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname }),
  })
  if (!res.ok) {
    let detail = ''
    try {
      detail = ((await res.json()) as { detail?: string }).detail ?? ''
    } catch {
      // body not parseable
    }
    throw Object.assign(new Error(`API error ${res.status}`), { status: res.status, detail })
  }
  return res.json() as Promise<JoinSessionResponse>
}
