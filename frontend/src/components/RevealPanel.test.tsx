import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { RevealPanel } from './RevealPanel'
import { useSessionStore } from '@/stores/sessionStore'

const candidates = [
  { characterId: 'alice', name: '앨리스', playerNickname: 'player1', playerId: 'pid-1' },
  { characterId: 'bob', name: '밥', playerNickname: 'player2', playerId: 'pid-2' },
]

const baseVote = {
  roundNo: 0,
  deadlineAt: Date.now() + 60_000,
  candidates,
  submittedCount: 2,
  totalCount: 2,
  myVote: null,
  tally: [
    { characterId: 'alice', count: 2 },
    { characterId: 'bob', count: 0 },
  ],
  tiedCharacterIds: null,
}

beforeEach(() => {
  useSessionStore.getState().reset()
})

describe('RevealPanel', () => {
  it('reveal이 null이면 아무것도 렌더링하지 않는다', () => {
    const { container } = render(<RevealPanel />)
    expect(container.firstChild).toBeNull()
  })

  it('single_winner — culpritCharacterId가 후보 이름으로 렌더링된다', () => {
    useSessionStore.getState().setSession({
      reveal: { outcome: 'single_winner', culpritCharacterId: 'alice', accusedCharacterId: 'alice' },
      vote: { ...baseVote, outcome: 'single_winner', winnerCharacterId: 'alice' },
    })

    render(<RevealPanel />)

    expect(screen.getByTestId('reveal-result-winner')).toBeInTheDocument()
    expect(screen.getByTestId('reveal-result-winner').textContent).toContain('앨리스')
  })

  it('failed — 색출 실패 + trueCulprit 캐릭터 이름이 렌더링된다', () => {
    useSessionStore.getState().setSession({
      reveal: { outcome: 'failed', culpritCharacterId: 'bob', accusedCharacterId: null },
      vote: {
        ...baseVote,
        outcome: 'failed',
        winnerCharacterId: null,
        tally: [
          { characterId: 'alice', count: 1 },
          { characterId: 'bob', count: 1 },
        ],
        tiedCharacterIds: ['alice', 'bob'],
      },
    })

    render(<RevealPanel />)

    expect(screen.getByTestId('reveal-result-failed')).toBeInTheDocument()
    expect(screen.getByTestId('reveal-result-failed').textContent).toContain('색출 실패')
    expect(screen.getByTestId('reveal-result-failed').textContent).toContain('밥')
  })
})
