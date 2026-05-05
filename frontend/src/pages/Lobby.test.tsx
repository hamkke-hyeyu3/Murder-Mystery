import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { LAST_SESSION_KEY } from '@/types/session'
import type { LastSession } from '@/types/session'
import { useSessionStore } from '@/stores/sessionStore'
import Lobby from './Lobby'

function renderLobby(inviteCode = '012345') {
  return render(
    <MemoryRouter initialEntries={[`/lobby/${inviteCode}`]}>
      <Routes>
        <Route path="/lobby/:inviteCode" element={<Lobby />} />
      </Routes>
    </MemoryRouter>
  )
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
})
