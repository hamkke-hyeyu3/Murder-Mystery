import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useSessionWebSocket } from '@/hooks/useSessionWebSocket'
import { useCardStore } from '@/stores/cardStore'
import Play from './Play'

vi.mock('@/hooks/useSessionWebSocket')

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
})
