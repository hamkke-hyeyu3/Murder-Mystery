import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { useCardStore } from '@/stores/cardStore'
import { MyCluesPanel } from './MyCluesPanel'
import type { ClueView } from '@/types/session'

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

beforeEach(() => {
  useCardStore.getState().reset()
})

describe('MyCluesPanel', () => {
  it('shows empty state when no clues', () => {
    render(<MyCluesPanel />)
    expect(screen.getByTestId('my-clues-panel')).toBeInTheDocument()
    expect(screen.queryByTestId(/clue-section-round/)).toBeNull()
  })

  it('groups clues by round', () => {
    useCardStore.getState().setClues([
      makeClue('a', 1),
      makeClue('b', 2),
      makeClue('c', 1),
    ])
    render(<MyCluesPanel />)
    expect(screen.getByTestId('clue-section-round-1')).toBeInTheDocument()
    expect(screen.getByTestId('clue-section-round-2')).toBeInTheDocument()
    expect(screen.getByTestId('clue-item-a')).toBeInTheDocument()
    expect(screen.getByTestId('clue-item-c')).toBeInTheDocument()
    expect(screen.getByTestId('clue-item-b')).toBeInTheDocument()
  })

  it('renders rounds in ascending order', () => {
    useCardStore.getState().setClues([makeClue('x', 3), makeClue('y', 1)])
    render(<MyCluesPanel />)
    const sections = screen.getAllByTestId(/clue-section-round-/)
    expect(sections[0]).toHaveAttribute('data-testid', 'clue-section-round-1')
    expect(sections[1]).toHaveAttribute('data-testid', 'clue-section-round-3')
  })
})
