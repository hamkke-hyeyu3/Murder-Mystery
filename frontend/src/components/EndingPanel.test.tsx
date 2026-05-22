import { render, screen } from '@testing-library/react'
import { beforeEach, describe, it, expect } from 'vitest'
import { EndingPanel } from './EndingPanel'
import { useSessionStore } from '@/stores/sessionStore'

beforeEach(() => {
  useSessionStore.setState({ reveal: null, vote: null } as any)
})

describe('EndingPanel', () => {
  it('shows ending heading', () => {
    render(<EndingPanel />)
    expect(screen.getByTestId('ending-panel')).toBeInTheDocument()
    expect(screen.getByText(/게임 종료/)).toBeInTheDocument()
  })

  it('shows single_winner culprit info when reveal outcome is single_winner', () => {
    useSessionStore.setState({
      reveal: { outcome: 'single_winner', culpritCharacterId: 'bob', accusedCharacterId: 'bob' },
      vote: {
        roundNo: 3, deadlineAt: 0, candidates: [
          { characterId: 'bob', name: '밥', playerNickname: '플레이어B', playerId: 'p2' }
        ],
        submittedCount: 3, totalCount: 3,
      },
    } as any)
    render(<EndingPanel />)
    expect(screen.getByText(/밥/)).toBeInTheDocument()
    expect(screen.getByText(/범인은/)).toBeInTheDocument()
  })

  it('shows failed culprit info when reveal outcome is failed', () => {
    useSessionStore.setState({
      reveal: { outcome: 'failed', culpritCharacterId: 'alice', accusedCharacterId: null },
      vote: {
        roundNo: 3, deadlineAt: 0, candidates: [
          { characterId: 'alice', name: '앨리스', playerNickname: '플레이어A', playerId: 'p1' }
        ],
        submittedCount: 3, totalCount: 3,
      },
    } as any)
    render(<EndingPanel />)
    expect(screen.getByText(/앨리스/)).toBeInTheDocument()
    expect(screen.getByText(/색출 실패/)).toBeInTheDocument()
  })

  it('shows waiting text when no reveal data', () => {
    render(<EndingPanel />)
    expect(screen.getByTestId('ending-panel')).toBeInTheDocument()
  })
})
