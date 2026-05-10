import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useSessionWebSocket } from '@/hooks/useSessionWebSocket'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'
import Play from './Play'

vi.mock('@/hooks/useSessionWebSocket')
vi.mock('@/lib/sessionApi', () => ({
  postTutorialAck: vi.fn().mockResolvedValue({ acked: 1, total: 3, state: 'tutorial' }),
}))

const mockUseSessionWebSocket = vi.mocked(useSessionWebSocket)

beforeEach(() => {
  mockUseSessionWebSocket.mockReturnValue({ connected: false, publishLeave: vi.fn() })
})

function renderPlay(sessionId = 'sess-001') {
  return render(
    <MemoryRouter initialEntries={[`/play/${sessionId}`]}>
      <Routes>
        <Route path="/play/:sessionId" element={<Play />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('Play', () => {
  it('캐릭터 카드가 없으면 배정 대기 placeholder를 보인다', () => {
    renderPlay()
    expect(screen.getByTestId('play-waiting-card')).toBeInTheDocument()
    expect(screen.getByText('캐릭터 배정 중…')).toBeInTheDocument()
  })

  it('캐릭터 카드가 있으면 이름과 순서를 보인다', () => {
    useCardStore.getState().setCharacterCard({
      characterId: 'char-a',
      name: 'Alice',
      turnOrderIndex: 0,
    })

    renderPlay()

    expect(screen.getByTestId('character-card')).toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('조사 순서 #1')).toBeInTheDocument()
    expect(screen.queryByTestId('play-waiting-card')).not.toBeInTheDocument()
  })

  it("state='tutorial'일 때 Tutorial 컴포넌트를 렌더한다", () => {
    useSessionStore.getState().setSession({ state: 'tutorial', players: [] })

    renderPlay()

    expect(screen.getByTestId('tutorial')).toBeInTheDocument()
    expect(screen.getByText(/전원 거짓말 가능/)).toBeInTheDocument()
    expect(screen.queryByTestId('play-waiting-card')).not.toBeInTheDocument()
  })

  it("state='round'일 때 RoundPanel을 렌더한다", () => {
    useSessionStore.getState().setSession({
      state: 'round',
      roundNumber: 1,
      roundPrompt: '한 사람씩 자기 캐릭터를 짧게 소개해 주세요.',
      roundCommonHint: null,
    })

    renderPlay()

    expect(screen.getByTestId('round-panel')).toBeInTheDocument()
    expect(screen.getByTestId('round-prompt')).toHaveTextContent('한 사람씩 자기 캐릭터를 짧게 소개해 주세요.')
    expect(screen.queryByTestId('tutorial')).not.toBeInTheDocument()
  })

  it("state='character_assignment'일 때 카드 영역을 렌더한다", () => {
    useSessionStore.getState().setSession({ state: 'character_assignment' })
    useCardStore.getState().setCharacterCard({
      characterId: 'char-a',
      name: 'Alice',
      turnOrderIndex: 0,
    })

    renderPlay()

    expect(screen.getByTestId('character-card')).toBeInTheDocument()
    expect(screen.queryByTestId('tutorial')).not.toBeInTheDocument()
  })
})
