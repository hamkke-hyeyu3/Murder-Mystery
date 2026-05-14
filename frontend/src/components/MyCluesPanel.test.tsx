import { render, screen, fireEvent, act } from '@testing-library/react'
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { useCardStore } from '@/stores/cardStore'
import { MyCluesPanel } from './MyCluesPanel'
import type { ClueView } from '@/types/session'
import type { PlayerSummary } from '@/stores/sessionStore'

function makeClue(id: string, round: number): ClueView {
  return {
    id,
    itemId: `item-${id}`,
    title: `단서 ${id}`,
    originLocationId: 'loc-1',
    roundNumberDiscovered: round,
    discoveredAt: Date.now(),
    source: 'discovery',
  }
}

const defaultPlayers: PlayerSummary[] = [
  { playerId: 'player-me', nickname: 'Alice', isHost: true },
  { playerId: 'player-bob', nickname: 'Bob', isHost: false },
]

const defaultProps = {
  players: defaultPlayers,
  myPlayerId: 'player-me',
  onExchange: vi.fn(),
  onShareFull: vi.fn(),
  onSharePartial: vi.fn(),
}

beforeEach(() => {
  useCardStore.getState().reset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('MyCluesPanel', () => {
  it('shows empty state when no clues', () => {
    render(<MyCluesPanel {...defaultProps} />)
    expect(screen.getByTestId('my-clues-panel')).toBeInTheDocument()
    expect(screen.queryByTestId(/clue-section-round/)).toBeNull()
  })

  it('groups clues by round', () => {
    useCardStore.getState().setClues([
      makeClue('a', 1),
      makeClue('b', 2),
      makeClue('c', 1),
    ])
    render(<MyCluesPanel {...defaultProps} />)
    expect(screen.getByTestId('clue-section-round-1')).toBeInTheDocument()
    expect(screen.getByTestId('clue-section-round-2')).toBeInTheDocument()
    expect(screen.getByTestId('clue-item-a')).toBeInTheDocument()
    expect(screen.getByTestId('clue-item-c')).toBeInTheDocument()
    expect(screen.getByTestId('clue-item-b')).toBeInTheDocument()
  })

  it('renders rounds in ascending order', () => {
    useCardStore.getState().setClues([makeClue('x', 3), makeClue('y', 1)])
    render(<MyCluesPanel {...defaultProps} />)
    const sections = screen.getAllByTestId(/clue-section-round-/)
    expect(sections[0]).toHaveAttribute('data-testid', 'clue-section-round-1')
    expect(sections[1]).toHaveAttribute('data-testid', 'clue-section-round-3')
  })

  it('단서 long-press(500ms) 시 ClueActionSheet 가 열린다', () => {
    vi.useFakeTimers()
    useCardStore.getState().setClues([makeClue('a', 1)])
    render(<MyCluesPanel {...defaultProps} />)

    const item = screen.getByTestId('clue-item-a')
    fireEvent.pointerDown(item, { clientX: 0, clientY: 0 })

    act(() => { vi.advanceTimersByTime(500) })

    expect(screen.getByTestId('clue-action-sheet')).toBeInTheDocument()
  })

  it('시트를 닫았다가 같은 단서를 다시 열면 menu 모드로 초기화된다', () => {
    vi.useFakeTimers()
    useCardStore.getState().setClues([makeClue('a', 1)])
    render(<MyCluesPanel {...defaultProps} />)

    const item = screen.getByTestId('clue-item-a')

    // 1차 오픈 → 부분 공유 모드로 전환
    fireEvent.pointerDown(item, { clientX: 0, clientY: 0 })
    act(() => { vi.advanceTimersByTime(500) })
    expect(screen.getByTestId('clue-action-sheet')).toBeInTheDocument()
    fireEvent.click(screen.getByTestId('action-share-partial'))
    expect(screen.getByTestId('confirm-share-partial')).toBeInTheDocument()

    // 시트 닫기
    fireEvent.click(screen.getByTestId('action-cancel'))
    expect(screen.queryByTestId('clue-action-sheet')).toBeNull()

    // 2차 오픈 → menu 모드여야 한다
    fireEvent.pointerDown(item, { clientX: 0, clientY: 0 })
    act(() => { vi.advanceTimersByTime(500) })
    expect(screen.getByTestId('clue-action-sheet')).toBeInTheDocument()
    expect(screen.getByTestId('action-share-full')).toBeInTheDocument()
    expect(screen.queryByTestId('confirm-share-partial')).toBeNull()
  })

  it('단서 짧은 탭(200ms) 시 ClueActionSheet 가 열리지 않는다', () => {
    vi.useFakeTimers()
    useCardStore.getState().setClues([makeClue('a', 1)])
    render(<MyCluesPanel {...defaultProps} />)

    const item = screen.getByTestId('clue-item-a')
    act(() => {
      fireEvent.pointerDown(item, { clientX: 0, clientY: 0 })
      vi.advanceTimersByTime(200)
      fireEvent.pointerUp(item)
      vi.advanceTimersByTime(400)
    })

    expect(screen.queryByTestId('clue-action-sheet')).toBeNull()
  })
})
