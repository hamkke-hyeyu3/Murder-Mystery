import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { VotePanel } from './VotePanel'
import { useSessionStore } from '@/stores/sessionStore'

const baseCandidates = [
  { characterId: 'alice', name: '알리스', playerNickname: 'player1', playerId: 'pid-1' },
  { characterId: 'bob', name: '밥', playerNickname: 'player2', playerId: 'pid-2' },
]

const baseVote = {
  roundNo: 0,
  deadlineAt: Date.now() + 60_000,
  candidates: baseCandidates,
  submittedCount: 0,
  totalCount: 2,
  myVote: null,
  outcome: null,
  winnerCharacterId: null,
  tiedCharacterIds: null,
  tally: null,
}

beforeEach(() => {
  useSessionStore.getState().reset()
})

describe('VotePanel', () => {
  it('후보 카드가 렌더링된다', () => {
    useSessionStore.getState().setSession({ vote: baseVote })
    render(<VotePanel onSubmit={vi.fn()} />)

    expect(screen.getByTestId('vote-candidate-alice')).toBeInTheDocument()
    expect(screen.getByTestId('vote-candidate-bob')).toBeInTheDocument()
  })

  it('본인 후보에 (나) 라벨이 표시된다', () => {
    useSessionStore.getState().setSession({ vote: baseVote, playerId: 'pid-1' })
    render(<VotePanel onSubmit={vi.fn()} />)

    expect(screen.getByTestId('vote-candidate-alice').textContent).toContain('(나)')
  })

  it('후보 선택 후 제출 버튼 클릭 시 onSubmit이 호출된다', () => {
    useSessionStore.getState().setSession({ vote: baseVote })
    const onSubmit = vi.fn()
    render(<VotePanel onSubmit={onSubmit} />)

    fireEvent.click(screen.getByTestId('vote-candidate-alice'))
    fireEvent.click(screen.getByTestId('vote-submit-btn'))

    expect(onSubmit).toHaveBeenCalledWith('alice', 0)
  })

  it('후보 미선택 시 제출 버튼이 disabled이다', () => {
    useSessionStore.getState().setSession({ vote: baseVote })
    render(<VotePanel onSubmit={vi.fn()} />)

    expect(screen.getByTestId('vote-submit-btn')).toBeDisabled()
  })

  it('이미 제출한 경우 버튼 라벨이 "다시 제출"이다', () => {
    useSessionStore.getState().setSession({ vote: { ...baseVote, myVote: 'alice' } })
    render(<VotePanel onSubmit={vi.fn()} />)

    expect(screen.getByTestId('vote-submit-btn').textContent).toContain('다시 제출')
  })

  it('제출 카운트가 표시된다', () => {
    useSessionStore.getState().setSession({ vote: { ...baseVote, submittedCount: 1, totalCount: 2 } })
    render(<VotePanel onSubmit={vi.fn()} />)

    expect(screen.getByTestId('vote-count').textContent).toContain('1 / 2')
  })

  it('재투표 배너가 표시된다 (roundNo=1)', () => {
    useSessionStore.getState().setSession({ vote: { ...baseVote, roundNo: 1 } })
    render(<VotePanel onSubmit={vi.fn()} />)

    expect(screen.getByTestId('vote-runoff-banner')).toBeInTheDocument()
  })

  it('단독 승자 결과 화면을 렌더링한다', () => {
    useSessionStore.getState().setSession({
      vote: {
        ...baseVote,
        outcome: 'single_winner',
        winnerCharacterId: 'alice',
        tally: [
          { characterId: 'alice', count: 2 },
          { characterId: 'bob', count: 0 },
        ],
      },
    })
    render(<VotePanel onSubmit={vi.fn()} />)

    expect(screen.getByTestId('vote-result-winner')).toBeInTheDocument()
    expect(screen.getByTestId('vote-result-winner').textContent).toContain('알리스')
  })

  it('색출 실패 결과 화면을 렌더링한다', () => {
    useSessionStore.getState().setSession({
      vote: {
        ...baseVote,
        outcome: 'failed',
        tiedCharacterIds: ['alice', 'bob'],
      },
    })
    render(<VotePanel onSubmit={vi.fn()} />)

    expect(screen.getByTestId('vote-result-failed')).toBeInTheDocument()
    expect(screen.getByTestId('vote-result-failed').textContent).toContain('색출 실패')
  })

  it('vote가 null이면 아무것도 렌더링하지 않는다', () => {
    useSessionStore.getState().setSession({ vote: null })
    const { container } = render(<VotePanel onSubmit={vi.fn()} />)

    expect(container.firstChild).toBeNull()
  })
})
