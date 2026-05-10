import { apiFetch, apiPost } from '@/lib/api'
import { getDeviceId } from '@/lib/deviceId'
import type { CreateSessionRequest, CreateSessionResponse, JoinSessionResponse, ResumeResponse, SessionViewResponse, TutorialAckResponse } from '@/types/session'

export function getSession(sessionId: string): Promise<SessionViewResponse> {
  return apiFetch<SessionViewResponse>(`/api/sessions/${sessionId}`)
}

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
    headers: { 'Content-Type': 'application/json', 'X-Device-Id': getDeviceId() },
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

export function postTutorialAck(sessionId: string): Promise<TutorialAckResponse> {
  return apiFetch<TutorialAckResponse>(`/api/sessions/${sessionId}/tutorial-ack`, {
    method: 'POST',
    headers: { 'X-Device-Id': getDeviceId() },
  })
}

export async function getResumeSession(): Promise<ResumeResponse | null> {
  const res = await fetch('/api/sessions/by-device', {
    headers: { 'X-Device-Id': getDeviceId() },
  })
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`API error ${res.status}`)
  return res.json() as Promise<ResumeResponse>
}
