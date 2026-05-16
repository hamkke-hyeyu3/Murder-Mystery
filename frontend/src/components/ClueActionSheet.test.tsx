import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useCardStore } from '@/stores/cardStore'
import { ClueActionSheet } from './ClueActionSheet'
import type { ClueView, OwnedClueView } from '@/types/session'
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

function makeOwnedClue(id: string, ownerPlayerId: string): OwnedClueView {
  return { id, itemId: `item-${id}`, title: `단서 ${id}`, ownerPlayerId, roundNumberDiscovered: 1 }
}

const players: PlayerSummary[] = [
  { playerId: 'player-me', nickname: 'Alice', isHost: true },
  { playerId: 'player-bob', nickname: 'Bob', isHost: false },
  { playerId: 'player-charlie', nickname: 'Charlie', isHost: false },
]

const noOwnedClues: OwnedClueView[] = []

beforeEach(() => {
  useCardStore.getState().reset()
})

function renderSheet(overrides: Partial<Parameters<typeof ClueActionSheet>[0]> = {}) {
  return render(
    <ClueActionSheet
      clue={makeClue()}
      players={players}
      myPlayerId="player-me"
      ownedClues={noOwnedClues}
      onClose={vi.fn()}
      onExchange={vi.fn()}
      onShareFull={vi.fn()}
      onSharePartial={vi.fn()}
      {...overrides}
    />
  )
}

describe('ClueActionSheet', () => {
  it('clue 있으면 3개 행위 버튼이 모두 노출된다', () => {
    renderSheet()
    expect(screen.getByTestId('clue-action-sheet')).toBeInTheDocument()
    expect(screen.getByTestId('action-share-full')).toBeInTheDocument()
    expect(screen.getByTestId('action-share-partial')).toBeInTheDocument()
    expect(screen.getByTestId('action-exchange')).toBeInTheDocument()
  })

  it('[교환] 버튼은 내 소유 단서가 있으면 활성화 상태다', () => {
    renderSheet({ ownedClues: [makeOwnedClue('my-c1', 'player-me')] })
    expect(screen.getByTestId('action-exchange')).not.toBeDisabled()
  })

  it('[교환] 버튼은 내 소유 단서가 없으면 disabled 다', () => {
    renderSheet({ ownedClues: [] })
    expect(screen.getByTestId('action-exchange')).toBeDisabled()
  })

  it('[전체 공유] 클릭 시 onShareFull(clueId) 와 onClose 가 호출된다', () => {
    const onShareFull = vi.fn()
    const onClose = vi.fn()
    const clue = makeClue('clue-abc')
    renderSheet({ clue, onClose, onShareFull })
    fireEvent.click(screen.getByTestId('action-share-full'))
    expect(onShareFull).toHaveBeenCalledWith('clue-abc')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('[부분 공유] 클릭 시 본인 제외 player 체크박스 목록이 나타난다', () => {
    renderSheet()
    fireEvent.click(screen.getByTestId('action-share-partial'))
    // Bob, Charlie 노출 (본인 Alice 제외)
    expect(screen.getByTestId('recipient-player-bob')).toBeInTheDocument()
    expect(screen.getByTestId('recipient-player-charlie')).toBeInTheDocument()
    expect(screen.queryByTestId('recipient-player-me')).toBeNull()
  })

  it('체크박스 선택 전 "확정" 버튼은 disabled 다', () => {
    renderSheet()
    fireEvent.click(screen.getByTestId('action-share-partial'))
    expect(screen.getByTestId('confirm-share-partial')).toBeDisabled()
  })

  it('체크박스 1명 이상 선택 후 "확정" 클릭 시 onSharePartial(clueId, [...]) 가 호출된다', () => {
    const onSharePartial = vi.fn()
    const onClose = vi.fn()
    const clue = makeClue('clue-xyz')
    renderSheet({ clue, onClose, onSharePartial })
    fireEvent.click(screen.getByTestId('action-share-partial'))
    fireEvent.click(screen.getByTestId('recipient-player-bob'))
    expect(screen.getByTestId('confirm-share-partial')).not.toBeDisabled()
    fireEvent.click(screen.getByTestId('confirm-share-partial'))
    expect(onSharePartial).toHaveBeenCalledWith('clue-xyz', ['player-bob'])
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('취소 버튼 클릭 시 onClose 가 호출된다', () => {
    const onClose = vi.fn()
    renderSheet({ onClose })
    fireEvent.click(screen.getByTestId('action-cancel'))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  // exchange flow
  it('[교환] 클릭 시 partner picker 가 노출된다', () => {
    renderSheet({ ownedClues: [makeOwnedClue('my-c1', 'player-me')] })
    fireEvent.click(screen.getByTestId('action-exchange'))
    expect(screen.getByTestId('exchange-partner-player-bob')).toBeInTheDocument()
    expect(screen.getByTestId('exchange-partner-player-charlie')).toBeInTheDocument()
    expect(screen.queryByTestId('exchange-partner-player-me')).toBeNull()
  })

  it('[교환] exchange-partner에서 뒤로 클릭 시 menu 로 돌아간다', () => {
    renderSheet({ ownedClues: [makeOwnedClue('my-c1', 'player-me')] })
    fireEvent.click(screen.getByTestId('action-exchange'))
    expect(screen.getByTestId('exchange-partner-player-bob')).toBeInTheDocument()
    fireEvent.click(screen.getByTestId('exchange-back'))
    expect(screen.getByTestId('action-share-full')).toBeInTheDocument()
    expect(screen.queryByTestId('exchange-partner-player-bob')).toBeNull()
  })

  it('[교환] exchange-clue에서 뒤로 클릭 시 exchange-partner 로 돌아간다', () => {
    const ownedClues: OwnedClueView[] = [
      makeOwnedClue('my-c1', 'player-me'),
      makeOwnedClue('bob-c1', 'player-bob'),
    ]
    renderSheet({ ownedClues })
    fireEvent.click(screen.getByTestId('action-exchange'))
    fireEvent.click(screen.getByTestId('exchange-partner-player-bob'))
    expect(screen.getByTestId('exchange-my-clue-my-c1')).toBeInTheDocument()
    fireEvent.click(screen.getByTestId('exchange-clue-back'))
    expect(screen.getByTestId('exchange-partner-player-bob')).toBeInTheDocument()
    expect(screen.queryByTestId('exchange-my-clue-my-c1')).toBeNull()
  })

  it('partner 가 단서를 소유하지 않은 경우 해당 버튼이 disabled 다', () => {
    const ownedClues: OwnedClueView[] = [makeOwnedClue('c1', 'player-me')]
    renderSheet({ ownedClues })
    fireEvent.click(screen.getByTestId('action-exchange'))
    expect(screen.getByTestId('exchange-partner-player-bob')).toBeDisabled()
    expect(screen.getByTestId('exchange-partner-player-charlie')).toBeDisabled()
  })

  it('partner 선택 후 내 단서 + 상대 단서 picker 가 노출된다', () => {
    const ownedClues: OwnedClueView[] = [
      makeOwnedClue('my-c1', 'player-me'),
      makeOwnedClue('bob-c1', 'player-bob'),
    ]
    renderSheet({ ownedClues })
    fireEvent.click(screen.getByTestId('action-exchange'))
    fireEvent.click(screen.getByTestId('exchange-partner-player-bob'))
    expect(screen.getByTestId('exchange-my-clue-my-c1')).toBeInTheDocument()
    expect(screen.getByTestId('exchange-partner-clue-bob-c1')).toBeInTheDocument()
  })

  it('양쪽 선택 전 교환 확정 버튼은 disabled 다', () => {
    const ownedClues: OwnedClueView[] = [
      makeOwnedClue('my-c1', 'player-me'),
      makeOwnedClue('bob-c1', 'player-bob'),
    ]
    renderSheet({ ownedClues })
    fireEvent.click(screen.getByTestId('action-exchange'))
    fireEvent.click(screen.getByTestId('exchange-partner-player-bob'))
    expect(screen.getByTestId('confirm-exchange')).toBeDisabled()
  })

  it('양쪽 단서 선택 후 확정 시 onExchange(partnerPlayerId, requesterClueId, partnerClueId) 호출', () => {
    const onExchange = vi.fn()
    const onClose = vi.fn()
    const ownedClues: OwnedClueView[] = [
      makeOwnedClue('my-c1', 'player-me'),
      makeOwnedClue('bob-c1', 'player-bob'),
    ]
    renderSheet({ ownedClues, onExchange, onClose })
    fireEvent.click(screen.getByTestId('action-exchange'))
    fireEvent.click(screen.getByTestId('exchange-partner-player-bob'))
    fireEvent.click(screen.getByTestId('exchange-my-clue-my-c1'))
    fireEvent.click(screen.getByTestId('exchange-partner-clue-bob-c1'))
    expect(screen.getByTestId('confirm-exchange')).not.toBeDisabled()
    fireEvent.click(screen.getByTestId('confirm-exchange'))
    expect(onExchange).toHaveBeenCalledWith('player-bob', 'my-c1', 'bob-c1')
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
