import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { LAST_SESSION_KEY } from '@/types/session'
import type { LastSession } from '@/types/session'
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
  mockUseSessionWebSocket.mockReturnValue({ connected: false, publishLeave: mockPublishLeave })
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

  it('"게임 시작" 버튼이 비활성이다', () => {
    renderLobby()
    expect(screen.getByRole('button', { name: '게임 시작' })).toBeDisabled()
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
        { nickname: 'alice', isHost: true },
        { nickname: 'bob', isHost: false },
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
