import { renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import { createElement, type ReactNode } from 'react'
import { server } from '@/mocks/server'
import { useSessionStore } from '@/stores/sessionStore'
import { LAST_SESSION_KEY } from '@/types/session'
import type { LastSession, ResumeResponse, SessionViewResponse } from '@/types/session'
import { useResumeSession } from './useResumeSession'

function makeWrapper(onLocation?: (path: string) => void) {
  function LocationCapture() {
    const loc = useLocation()
    onLocation?.(loc.pathname)
    return null
  }
  return ({ children }: { children: ReactNode }) =>
    createElement(MemoryRouter, { initialEntries: ['/'] }, children, createElement(LocationCapture))
}

const validResume: ResumeResponse = {
  sessionId: 'sess-001',
  inviteCode: '012345',
  scenarioId: 'toy-manor',
  phase: 'lobby',
  nickname: 'alice',
  playerId: 'player-001',
  isHost: true,
  players: [{ playerId: 'player-001', nickname: 'alice', isHost: true }],
}

const validLastSession: LastSession = {
  sessionId: 'sess-001',
  inviteCode: '012345',
  nickname: 'alice',
  isHost: true,
  playerId: 'player-001',
  scenarioId: 'toy-manor',
  savedAt: 0,
}

const matchingView: SessionViewResponse = {
  sessionId: 'sess-001',
  inviteCode: '012345',
  scenarioId: 'toy-manor',
  phase: 'lobby',
  requiredCharacterCount: 3,
  joinedCount: 1,
  players: [{ playerId: 'player-001', nickname: 'alice', isHost: true }],
  state: null,
  currentRoundNumber: null,
  turnOrder: null,
  round: null,
  me: null,
}

describe('useResumeSession', () => {
  it('skip=true 일 때는 store도 라우팅도 건드리지 않는다', async () => {
    const paths: string[] = []
    server.use(
      http.get('/api/sessions/by-device', () => HttpResponse.json(validResume)),
    )

    renderHook(() => useResumeSession({ skip: true }), { wrapper: makeWrapper((p) => paths.push(p)) })

    // 100ms: enough for async chain to complete even in slow CI; skip means no fetch fires at all
    await new Promise((r) => setTimeout(r, 100))
    expect(useSessionStore.getState().sessionId).toBeNull()
    expect(paths.every((p) => p === '/')).toBe(true)
  })

  it('by-device 성공 시 store hydrate + /lobby/{inviteCode}로 navigate', async () => {
    const paths: string[] = []
    server.use(
      http.get('/api/sessions/by-device', () => HttpResponse.json(validResume)),
    )

    renderHook(() => useResumeSession(), { wrapper: makeWrapper((p) => paths.push(p)) })

    await waitFor(() => expect(paths).toContain('/lobby/012345'))
    const state = useSessionStore.getState()
    expect(state.sessionId).toBe('sess-001')
    expect(state.inviteCode).toBe('012345')
    expect(state.nickname).toBe('alice')
    expect(state.isHost).toBe(true)
    expect(state.isHostConfirmed).toBe(true)
    expect(state.playerId).toBe('player-001')
    expect(state.phase).toBe('lobby')
  })

  it('by-device 404 + localStorage 비어있으면 무동작', async () => {
    const paths: string[] = []
    // default handler already returns 404 for by-device

    renderHook(() => useResumeSession(), { wrapper: makeWrapper((p) => paths.push(p)) })

    // 100ms: one fast fetch (404) + microtask chain must fully settle before we assert nothing happened
    await new Promise((r) => setTimeout(r, 100))
    expect(useSessionStore.getState().sessionId).toBeNull()
    expect(paths.every((p) => p === '/')).toBe(true)
  })

  it('by-device 404 + localStorage 유효 + view 일치 → /lobby로 이동', async () => {
    const paths: string[] = []
    localStorage.setItem(LAST_SESSION_KEY, JSON.stringify(validLastSession))
    server.use(
      http.get('/api/sessions/sess-001', () => HttpResponse.json(matchingView)),
    )

    renderHook(() => useResumeSession(), { wrapper: makeWrapper((p) => paths.push(p)) })

    await waitFor(() => expect(paths).toContain('/lobby/012345'))
    expect(useSessionStore.getState().sessionId).toBe('sess-001')
  })

  it('by-device 404 + localStorage playerId가 view에 없으면 무동작 (stale 방어)', async () => {
    const paths: string[] = []
    localStorage.setItem(LAST_SESSION_KEY, JSON.stringify(validLastSession))
    const viewWithoutMe: SessionViewResponse = {
      ...matchingView,
      players: [{ playerId: 'someone-else', nickname: 'eve', isHost: true }],
    }
    server.use(
      http.get('/api/sessions/sess-001', () => HttpResponse.json(viewWithoutMe)),
    )

    renderHook(() => useResumeSession(), { wrapper: makeWrapper((p) => paths.push(p)) })

    // 150ms: two fetches (404 + getSession) must both settle before we assert nothing happened
    await new Promise((r) => setTimeout(r, 150))
    expect(useSessionStore.getState().sessionId).toBeNull()
    expect(paths.every((p) => p === '/')).toBe(true)
  })

  it('by-device 404 + localStorage inviteCode가 view와 다르면 무동작', async () => {
    const paths: string[] = []
    localStorage.setItem(LAST_SESSION_KEY, JSON.stringify(validLastSession))
    const viewWithDifferentInviteCode: SessionViewResponse = {
      ...matchingView,
      inviteCode: '999999',
    }
    server.use(
      http.get('/api/sessions/sess-001', () => HttpResponse.json(viewWithDifferentInviteCode)),
    )

    renderHook(() => useResumeSession(), { wrapper: makeWrapper((p) => paths.push(p)) })

    // 150ms: two fetches (404 + getSession) must both settle before we assert nothing happened
    await new Promise((r) => setTimeout(r, 150))
    expect(useSessionStore.getState().sessionId).toBeNull()
    expect(paths.every((p) => p === '/')).toBe(true)
  })

  it('unmount 후 응답 도착 시 store/navigate 둘 다 건드리지 않는다 (cancelled flag)', async () => {
    const paths: string[] = []
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let resolveResume: ((res: HttpResponse<any>) => void) | null = null
    const handlerCalled = new Promise<void>((notifyCalled) => {
      server.use(
        http.get('/api/sessions/by-device', () =>
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          new Promise<HttpResponse<any>>((resolve) => {
            resolveResume = resolve
            notifyCalled()
          })
        ),
      )
    })

    const { unmount } = renderHook(
      () => useResumeSession(),
      { wrapper: makeWrapper((p) => paths.push(p)) }
    )

    await handlerCalled  // ensures fetch is in-flight before we unmount
    unmount()
    resolveResume!(HttpResponse.json(validResume))
    // 150ms: after resolveResume, MSW delivers the response via microtasks which flush well
    // before this timer fires. If cancelled flag is removed, setSession/navigate WOULD run and fail.
    await new Promise((r) => setTimeout(r, 150))

    expect(useSessionStore.getState().sessionId).toBeNull()
    expect(paths).not.toContain('/lobby/012345')
  })

  it('phase=in_progress이면 /play/:sessionId로 navigate한다 (by-device)', async () => {
    const paths: string[] = []
    const inProgressResume: ResumeResponse = {
      ...validResume,
      phase: 'in_progress',
    }
    server.use(
      http.get('/api/sessions/by-device', () => HttpResponse.json(inProgressResume)),
    )

    renderHook(() => useResumeSession(), { wrapper: makeWrapper((p) => paths.push(p)) })

    await waitFor(() => expect(paths).toContain('/play/sess-001'))
    expect(useSessionStore.getState().phase).toBe('in_progress')
  })

  it('phase=in_progress이면 /play/:sessionId로 navigate한다 (localStorage fallback)', async () => {
    const paths: string[] = []
    localStorage.setItem(LAST_SESSION_KEY, JSON.stringify(validLastSession))
    const inProgressView: SessionViewResponse = {
      ...matchingView,
      phase: 'in_progress',
    }
    server.use(
      http.get('/api/sessions/sess-001', () => HttpResponse.json(inProgressView)),
    )

    renderHook(() => useResumeSession(), { wrapper: makeWrapper((p) => paths.push(p)) })

    await waitFor(() => expect(paths).toContain('/play/sess-001'))
  })
})
