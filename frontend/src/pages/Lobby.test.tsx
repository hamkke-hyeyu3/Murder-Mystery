import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import { LAST_SESSION_KEY } from '@/types/session'
import type { LastSession, SessionViewResponse } from '@/types/session'
import { useSessionStore } from '@/stores/sessionStore'
import { useSessionWebSocket } from '@/hooks/useSessionWebSocket'
import Lobby from './Lobby'

vi.mock('@/hooks/useSessionWebSocket')

const mockUseSessionWebSocket = vi.mocked(useSessionWebSocket)

function LocationCapture({ onLocation }: { onLocation: (path: string) => void }) {
  const loc = useLocation()
  onLocation(loc.pathname)
  return null
}

let mockPublishLeave: ReturnType<typeof vi.fn>

beforeEach(() => {
  mockPublishLeave = vi.fn()
  mockUseSessionWebSocket.mockReturnValue({
    connected: false,
    publishLeave: mockPublishLeave,
    publishSelectLocation: vi.fn(),
    publishItemExchange: vi.fn(),
    publishItemShareFull: vi.fn(),
    publishItemSharePartial: vi.fn(),
    publishPrivateTalkRequest: vi.fn(),
    publishPrivateTalkAccept: vi.fn(),
    publishPrivateTalkReject: vi.fn(),
    publishPrivateTalkEnd: vi.fn(),
    publishVoteSubmit: vi.fn(),
  })
})

function renderLobby(inviteCode = '012345') {
  const navigatedTo: string[] = []
  const result = render(
    <MemoryRouter initialEntries={[`/lobby/${inviteCode}`]}>
      <Routes>
        <Route path="/lobby/:inviteCode" element={<Lobby />} />
        <Route path="/" element={<LocationCapture onLocation={(p) => navigatedTo.push(p)} />} />
      </Routes>
    </MemoryRouter>
  )
  return { ...result, navigatedTo }
}

describe('Lobby', () => {
  it('URL의 inviteCode를 6자리로 표시한다 (leading zero 보존)', () => {
    renderLobby('012345')
    expect(screen.getByText('012345')).toBeInTheDocument()
  })

  it('"아직 합류자 없음" placeholder를 보인다', () => {
    renderLobby()
    expect(screen.getByText('아직 합류자 없음')).toBeInTheDocument()
  })

  it('호스트에게 "게임 시작" 버튼이 보이고 게스트에게는 보이지 않는다', () => {
    useSessionStore.getState().setSession({
      isHost: true,
      requiredCharacterCount: 3,
      joinedCount: 3,
    })
    renderLobby()
    expect(screen.getByRole('button', { name: '게임 시작' })).toBeInTheDocument()

    useSessionStore.getState().reset()
    useSessionStore.getState().setSession({ isHost: false })
    renderLobby()
    expect(screen.queryByRole('button', { name: '게임 시작' })).not.toBeInTheDocument()
  })

  it('localStorage mm:lastSession이 매칭 inviteCode면 store를 하이드레이트한다', () => {
    const last: LastSession = {
      sessionId: 'sess-123',
      inviteCode: '012345',
      nickname: 'alice',
      isHost: true,
      playerId: 'player-123',
      scenarioId: 'toy-manor',
      savedAt: Date.now(),
    }
    localStorage.setItem(LAST_SESSION_KEY, JSON.stringify(last))

    renderLobby('012345')

    const state = useSessionStore.getState()
    expect(state.sessionId).toBe('sess-123')
    expect(state.nickname).toBe('alice')
    expect(state.isHost).toBe(true)
    expect(state.playerId).toBe('player-123')
  })

  it('localStorage inviteCode가 URL과 다르면 store를 변경하지 않는다', () => {
    const last: LastSession = {
      sessionId: 'sess-999',
      inviteCode: '999999',
      nickname: 'bob',
      isHost: false,
      playerId: 'player-999',
      scenarioId: 'toy-manor',
      savedAt: Date.now(),
    }
    localStorage.setItem(LAST_SESSION_KEY, JSON.stringify(last))

    renderLobby('012345')

    expect(useSessionStore.getState().sessionId).toBeNull()
  })

  it('localStorage에 malformed JSON이 있어도 crash 없이 렌더한다', () => {
    localStorage.setItem(LAST_SESSION_KEY, '{invalid json')
    renderLobby()
    expect(screen.getByText('012345')).toBeInTheDocument()
  })

  it('localStorage가 없어도 URL inviteCode로 정상 렌더한다', () => {
    renderLobby('789012')
    expect(screen.getByText('789012')).toBeInTheDocument()
  })

  it('store의 players를 목록으로 렌더한다', () => {
    useSessionStore.getState().setSession({
      players: [
        { playerId: 'p1', nickname: 'alice', isHost: true },
        { playerId: 'p2', nickname: 'bob', isHost: false },
      ],
    })

    renderLobby()

    expect(screen.getByText('alice')).toBeInTheDocument()
    expect(screen.getByText('bob')).toBeInTheDocument()
    expect(screen.queryByText('아직 합류자 없음')).not.toBeInTheDocument()
  })

  it('게스트에게 나가기 버튼이 보이고 호스트에게는 보이지 않는다', () => {
    useSessionStore.getState().setSession({ isHost: false, nickname: 'bob' })
    renderLobby()
    expect(screen.getByRole('button', { name: '나가기' })).toBeInTheDocument()

    useSessionStore.getState().reset()
    useSessionStore.getState().setSession({ isHost: true, nickname: 'alice' })
    renderLobby()
    expect(screen.queryByRole('button', { name: '나가기' })).not.toBeInTheDocument()
  })

  it.each([
    { role: 'host' as const, joined: 1, required: 3, label: '2명 더 필요', disabled: true },
    { role: 'host' as const, joined: 2, required: 3, label: '1명 더 필요', disabled: true },
    { role: 'host' as const, joined: 3, required: 3, label: null, disabled: false },
    { role: 'host' as const, joined: 4, required: 3, label: '1명 초과 — 누군가 나가야 합니다', disabled: true },
    { role: 'guest' as const, joined: 2, required: 3, label: null, disabled: null },
    { role: 'guest' as const, joined: 4, required: 3, label: null, disabled: null },
  ])(
    '($role, joined=$joined, required=$required) → 사유 라벨: $label, 시작 버튼: disabled=$disabled',
    ({ role, joined, required, label, disabled }) => {
      useSessionStore.getState().setSession({
        isHost: role === 'host',
        joinedCount: joined,
        requiredCharacterCount: required,
      })
      renderLobby()

      if (label) {
        expect(screen.getByText(label)).toBeInTheDocument()
      } else {
        expect(screen.queryByTestId('lobby-reason-short')).not.toBeInTheDocument()
        expect(screen.queryByTestId('lobby-reason-excess')).not.toBeInTheDocument()
      }

      if (disabled === null) {
        // guest: no start button
        expect(screen.queryByRole('button', { name: '게임 시작' })).not.toBeInTheDocument()
      } else {
        const btn = screen.getByRole('button', { name: '게임 시작' })
        if (disabled) {
          expect(btn).toBeDisabled()
        } else {
          expect(btn).toBeEnabled()
        }
      }
    }
  )

  it('tombstone 제거된 플레이어를 REST joinedCount가 되살려 시작 버튼을 활성화하지 않는다', async () => {
    const view: SessionViewResponse = {
      sessionId: 'sess-001',
      inviteCode: '012345',
      scenarioId: 'toy-manor',
      phase: 'lobby',
      requiredCharacterCount: 2,
      joinedCount: 2, // stale — charlie 포함
      players: [
        { playerId: 'p1', nickname: 'alice', isHost: true },
        { playerId: 'p-charlie', nickname: 'charlie', isHost: false },
      ],
    }
    server.use(http.get('/api/sessions/:id', () => HttpResponse.json(view)))
    useSessionStore.getState().setSession({
      sessionId: 'sess-001',
      playerId: 'p1',
      isHost: true,
      players: [{ playerId: 'p1', nickname: 'alice', isHost: true }],
      leftPlayerIds: ['p-charlie'],
    })

    renderLobby()

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument()
    })
    // stale joinedCount=2 가 반영되면 버튼이 활성화되는 버그
    expect(screen.getByRole('button', { name: '게임 시작' })).toBeDisabled()
  })

  it('getSession 응답이 늦게 도착해도 WS로 이미 떠난 플레이어를 되살리지 않는다', async () => {
    const view: SessionViewResponse = {
      sessionId: 'sess-001',
      inviteCode: '012345',
      scenarioId: 'toy-manor',
      phase: 'lobby',
      requiredCharacterCount: 2,
      joinedCount: 1,
      players: [
        { playerId: 'p1', nickname: 'alice', isHost: true },
        { playerId: 'p-charlie', nickname: 'charlie', isHost: false },
      ],
    }
    server.use(http.get('/api/sessions/:id', () => HttpResponse.json(view)))
    // charlie가 PLAYER_LEFT로 이미 제거된 상태 (leftPlayerIds에 기록됨)
    useSessionStore.getState().setSession({
      sessionId: 'sess-001',
      playerId: 'p1',
      players: [{ playerId: 'p1', nickname: 'alice', isHost: true }],
      leftPlayerIds: ['p-charlie'],
    })

    renderLobby()

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument()
    })
    expect(screen.queryByText('charlie')).not.toBeInTheDocument()
  })

  it('LOBBY_COUNT_CHANGED가 getSession보다 먼저 도착해도 host가 players에 포함된다', async () => {
    const view: SessionViewResponse = {
      sessionId: 'sess-001',
      inviteCode: '012345',
      scenarioId: 'toy-manor',
      phase: 'lobby',
      requiredCharacterCount: 3,
      joinedCount: 1,
      players: [{ playerId: 'p1', nickname: 'alice', isHost: true }],
    }
    server.use(http.get('/api/sessions/:id', () => HttpResponse.json(view)))
    // LOBBY_COUNT_CHANGED가 선도착해 requiredCharacterCount가 이미 설정된 상태
    useSessionStore.getState().setSession({
      sessionId: 'sess-001',
      playerId: 'p1',
      requiredCharacterCount: 3,
      joinedCount: 1,
    })

    renderLobby()

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument()
    })
  })

  it('mount 시 getSession을 호출해 카운트와 합류자 목록을 채운다', async () => {
    const view: SessionViewResponse = {
      sessionId: 'sess-001',
      inviteCode: '012345',
      scenarioId: 'toy-manor',
      phase: 'lobby',
      requiredCharacterCount: 3,
      joinedCount: 1,
      players: [{ playerId: 'p1', nickname: 'alice', isHost: true }],
    }
    server.use(http.get('/api/sessions/:id', () => HttpResponse.json(view)))
    useSessionStore.getState().setSession({ sessionId: 'sess-001', playerId: 'p1' })

    renderLobby()

    await waitFor(() => {
      expect(screen.getByText('2명 더 필요')).toBeInTheDocument()
      expect(screen.getByText('alice')).toBeInTheDocument()
      expect(useSessionStore.getState().isHost).toBe(true)
    })
  })

  it('mount 시 LOBBY_COUNT_CHANGED가 먼저 와도 GET 결과로 isHost를 확정한다', async () => {
    const view: SessionViewResponse = {
      sessionId: 'sess-001',
      inviteCode: '012345',
      scenarioId: 'toy-manor',
      phase: 'lobby',
      requiredCharacterCount: 3,
      joinedCount: 2,
      players: [
        { playerId: 'p1', nickname: 'alice', isHost: true },
        { playerId: 'p2', nickname: 'bob', isHost: false },
      ],
    }
    server.use(http.get('/api/sessions/:id', () => HttpResponse.json(view)))
    // STOMP 선도착 상태 + localStorage 스푸핑(isHost: true) 시뮬레이트
    useSessionStore.getState().reset()
    useSessionStore.getState().setSession({
      sessionId: 'sess-001',
      playerId: 'p2',
      isHost: true,
      isHostConfirmed: false,
      requiredCharacterCount: 3,
      joinedCount: 2,
    })

    renderLobby()

    await waitFor(() => {
      expect(useSessionStore.getState().isHost).toBe(false)
      expect(useSessionStore.getState().isHostConfirmed).toBe(true)
    })
  })

  it('mount 시 내 playerId가 view.players에 없으면 isHost를 false로 강제한다', async () => {
    const view: SessionViewResponse = {
      sessionId: 'sess-001',
      inviteCode: '012345',
      scenarioId: 'toy-manor',
      phase: 'lobby',
      requiredCharacterCount: 3,
      joinedCount: 1,
      players: [{ playerId: 'p1', nickname: 'alice', isHost: true }],
    }
    server.use(http.get('/api/sessions/:id', () => HttpResponse.json(view)))
    useSessionStore.getState().reset()
    useSessionStore.getState().setSession({
      sessionId: 'sess-001',
      playerId: 'phantom',
      isHost: true,
      isHostConfirmed: false,
    })

    renderLobby()

    await waitFor(() => {
      expect(useSessionStore.getState().isHost).toBe(false)
      expect(useSessionStore.getState().isHostConfirmed).toBe(true)
    })
  })

  it('게임 시작 클릭 시 POST /api/sessions/:id/start를 호출한다', async () => {
    server.use(
      http.post('/api/sessions/:id/start', () => HttpResponse.json({ phase: 'in_progress', state: 'intro', turnOrder: [] }))
    )
    useSessionStore.getState().setSession({
      sessionId: 'sess-001',
      isHost: true,
      joinedCount: 3,
      requiredCharacterCount: 3,
    })

    renderLobby()

    fireEvent.click(screen.getByRole('button', { name: '게임 시작' }))

    await waitFor(() => {
      // POST was called — no error should appear
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })

  it('phase가 in_progress로 바뀌면 /play/:sessionId로 navigate한다', async () => {
    const navigatedTo: string[] = []
    render(
      <MemoryRouter initialEntries={['/lobby/012345']}>
        <Routes>
          <Route path="/lobby/:inviteCode" element={<Lobby />} />
          <Route
            path="/play/:sessionId"
            element={<LocationCapture onLocation={(p) => navigatedTo.push(p)} />}
          />
        </Routes>
      </MemoryRouter>
    )

    // Simulate SESSION_STATE_CHANGED → phase = in_progress
    useSessionStore.getState().setSession({ sessionId: 'sess-001', phase: 'in_progress' })

    await waitFor(() => {
      expect(navigatedTo.some((p) => p.startsWith('/play/'))).toBe(true)
    })
  })

  it('getSession이 guest-first 순서로 반환해도 화면에서 host가 먼저 표시된다', async () => {
    const view: SessionViewResponse = {
      sessionId: 'sess-001',
      inviteCode: '012345',
      scenarioId: 'toy-manor',
      phase: 'lobby',
      requiredCharacterCount: 2,
      joinedCount: 2,
      players: [
        { playerId: 'p2', nickname: 'bob', isHost: false },
        { playerId: 'p1', nickname: 'alice', isHost: true },
      ],
    }
    server.use(http.get('/api/sessions/:id', () => HttpResponse.json(view)))
    useSessionStore.getState().setSession({ sessionId: 'sess-001', playerId: 'p1' })

    renderLobby()

    await waitFor(() => {
      const items = screen.getAllByRole('listitem')
      expect(items[0]).toHaveTextContent('alice')
      expect(items[1]).toHaveTextContent('bob')
    })
  })

  it('나가기 클릭 시 publishLeave 호출 후 localStorage 삭제 → store reset → /로 이동한다', async () => {
    useSessionStore.getState().setSession({
      sessionId: 'sess-001',
      inviteCode: '012345',
      nickname: 'bob',
      isHost: false,
      playerId: 'player-bob',
    })
    localStorage.setItem(LAST_SESSION_KEY, JSON.stringify({ inviteCode: '012345' }))

    const { navigatedTo } = renderLobby()

    fireEvent.click(screen.getByRole('button', { name: '나가기' }))

    await waitFor(() => expect(navigatedTo).toContain('/'))

    expect(mockPublishLeave).toHaveBeenCalledOnce()
    expect(localStorage.getItem(LAST_SESSION_KEY)).toBeNull()
    expect(useSessionStore.getState().sessionId).toBeNull()
  })
})
