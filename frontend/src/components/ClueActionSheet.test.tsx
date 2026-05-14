import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useCardStore } from '@/stores/cardStore'
import { ClueActionSheet } from './ClueActionSheet'
import type { ClueView } from '@/types/session'
import type { PlayerSummary } from '@/stores/sessionStore'

function makeClue(id = 'clue-1'): ClueView {
  return {
    id,
    itemId: `item-${id}`,
    title: `단서 ${id}`,
    originLocationId: 'loc-1',
    roundNumberDiscovered: 1,
    discoveredAt: Date.now(),
    source: 'discovery',
  }
}

const players: PlayerSummary[] = [
  { playerId: 'player-me', nickname: 'Alice', isHost: true },
  { playerId: 'player-bob', nickname: 'Bob', isHost: false },
  { playerId: 'player-charlie', nickname: 'Charlie', isHost: false },
]

beforeEach(() => {
  useCardStore.getState().reset()
})

describe('ClueActionSheet', () => {
  it('clue 있으면 3개 행위 버튼이 모두 노출된다', () => {
    render(
      <ClueActionSheet
        clue={makeClue()}
        players={players}
        myPlayerId="player-me"
        onClose={vi.fn()}
        onShareFull={vi.fn()}
        onSharePartial={vi.fn()}
      />
    )
    expect(screen.getByTestId('clue-action-sheet')).toBeInTheDocument()
    expect(screen.getByTestId('action-share-full')).toBeInTheDocument()
    expect(screen.getByTestId('action-share-partial')).toBeInTheDocument()
    expect(screen.getByTestId('action-exchange')).toBeInTheDocument()
  })

  it('[교환] 버튼은 disabled 다', () => {
    render(
      <ClueActionSheet
        clue={makeClue()}
        players={players}
        myPlayerId="player-me"
        onClose={vi.fn()}
        onShareFull={vi.fn()}
        onSharePartial={vi.fn()}
      />
    )
    expect(screen.getByTestId('action-exchange')).toBeDisabled()
  })

  it('[전체 공유] 클릭 시 onShareFull(clueId) 와 onClose 가 호출된다', () => {
    const onShareFull = vi.fn()
    const onClose = vi.fn()
    const clue = makeClue('clue-abc')
    render(
      <ClueActionSheet
        clue={clue}
        players={players}
        myPlayerId="player-me"
        onClose={onClose}
        onShareFull={onShareFull}
        onSharePartial={vi.fn()}
      />
    )
    fireEvent.click(screen.getByTestId('action-share-full'))
    expect(onShareFull).toHaveBeenCalledWith('clue-abc')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('[부분 공유] 클릭 시 본인 제외 player 체크박스 목록이 나타난다', () => {
    render(
      <ClueActionSheet
        clue={makeClue()}
        players={players}
        myPlayerId="player-me"
        onClose={vi.fn()}
        onShareFull={vi.fn()}
        onSharePartial={vi.fn()}
      />
    )
    fireEvent.click(screen.getByTestId('action-share-partial'))
    // Bob, Charlie 노출 (본인 Alice 제외)
    expect(screen.getByTestId('recipient-player-bob')).toBeInTheDocument()
    expect(screen.getByTestId('recipient-player-charlie')).toBeInTheDocument()
    expect(screen.queryByTestId('recipient-player-me')).toBeNull()
  })

  it('체크박스 선택 전 "확정" 버튼은 disabled 다', () => {
    render(
      <ClueActionSheet
        clue={makeClue()}
        players={players}
        myPlayerId="player-me"
        onClose={vi.fn()}
        onShareFull={vi.fn()}
        onSharePartial={vi.fn()}
      />
    )
    fireEvent.click(screen.getByTestId('action-share-partial'))
    expect(screen.getByTestId('confirm-share-partial')).toBeDisabled()
  })

  it('체크박스 1명 이상 선택 후 "확정" 클릭 시 onSharePartial(clueId, [...]) 가 호출된다', () => {
    const onSharePartial = vi.fn()
    const onClose = vi.fn()
    const clue = makeClue('clue-xyz')
    render(
      <ClueActionSheet
        clue={clue}
        players={players}
        myPlayerId="player-me"
        onClose={onClose}
        onShareFull={vi.fn()}
        onSharePartial={onSharePartial}
      />
    )
    fireEvent.click(screen.getByTestId('action-share-partial'))
    fireEvent.click(screen.getByTestId('recipient-player-bob'))
    expect(screen.getByTestId('confirm-share-partial')).not.toBeDisabled()
    fireEvent.click(screen.getByTestId('confirm-share-partial'))
    expect(onSharePartial).toHaveBeenCalledWith('clue-xyz', ['player-bob'])
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('취소 버튼 클릭 시 onClose 가 호출된다', () => {
    const onClose = vi.fn()
    render(
      <ClueActionSheet
        clue={makeClue()}
        players={players}
        myPlayerId="player-me"
        onClose={onClose}
        onShareFull={vi.fn()}
        onSharePartial={vi.fn()}
      />
    )
    fireEvent.click(screen.getByTestId('action-cancel'))
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
