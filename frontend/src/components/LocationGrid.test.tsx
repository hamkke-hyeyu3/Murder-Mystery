import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { LocationGrid } from './LocationGrid'
import type { ScenarioLocationView, OccupancyView } from '@/types/session'
import type { PlayerSummary } from '@/stores/sessionStore'

const locations: ScenarioLocationView[] = [
  { id: 'loc-a', name: '도서관', icon: '📚' },
  { id: 'loc-b', name: '정원', icon: '🌿' },
  { id: 'loc-c', name: '서재', icon: '🪑' },
]

const players: PlayerSummary[] = [
  { playerId: 'p1', nickname: 'Alice', isHost: true },
  { playerId: 'p2', nickname: 'Bob', isHost: false },
]

describe('LocationGrid', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('본인 차례 — 후보 셀은 활성 버튼, 비후보 셀은 비활성 버튼', () => {
    const onSelect = vi.fn()
    render(
      <LocationGrid
        locations={locations}
        candidateLocationIds={['loc-a', 'loc-b']}
        occupancy={[]}
        players={players}
        myPlayerId="p1"
        currentTurnPlayerId="p1"
        currentTurnIndex={0}
        turnDeadlineAt={Date.now() + 30_000}
        serverOffsetMs={0}
        onSelectLocation={onSelect}
      />
    )

    const cellA = screen.getByTestId('location-cell-loc-a')
    const cellC = screen.getByTestId('location-cell-loc-c')
    expect(cellA.tagName).toBe('BUTTON')
    expect(cellA).not.toBeDisabled()
    expect(cellC.tagName).toBe('BUTTON')
    expect(cellC).toBeDisabled()

    fireEvent.click(cellA)
    expect(onSelect).toHaveBeenCalledWith('loc-a')
  })

  it('본인 차례 — 이미 점유된 셀은 div로 렌더링되고 점유자 닉네임을 표시', () => {
    const occupancy: OccupancyView[] = [
      { locationId: 'loc-a', playerId: 'p2', characterId: 'char-b', autoSelected: false },
    ]
    render(
      <LocationGrid
        locations={locations}
        candidateLocationIds={['loc-a', 'loc-b']}
        occupancy={occupancy}
        players={players}
        myPlayerId="p1"
        currentTurnPlayerId="p1"
        currentTurnIndex={0}
        turnDeadlineAt={Date.now() + 30_000}
        serverOffsetMs={0}
        onSelectLocation={vi.fn()}
      />
    )

    const cellA = screen.getByTestId('location-cell-loc-a')
    expect(cellA.tagName).toBe('DIV')
    expect(cellA).toHaveAttribute('data-occupied-by', 'Bob')
    expect(cellA).toHaveTextContent('Bob')
  })

  it('타인 차례 — 모든 셀이 read-only div이고 헤더에 조사 중 표시', () => {
    render(
      <LocationGrid
        locations={locations}
        candidateLocationIds={['loc-a']}
        occupancy={[]}
        players={players}
        myPlayerId="p1"
        currentTurnPlayerId="p2"
        currentTurnIndex={1}
        turnDeadlineAt={Date.now() + 30_000}
        serverOffsetMs={0}
        onSelectLocation={vi.fn()}
      />
    )

    expect(screen.getByTestId('others-turn-label')).toHaveTextContent('Bob 조사 중')
    const cellA = screen.getByTestId('location-cell-loc-a')
    expect(cellA.tagName).toBe('DIV')
    expect(cellA).not.toHaveAttribute('role', 'button')
  })

  it('카운트다운이 5초 이하이면 경고 스타일 적용', () => {
    const now = Date.now()
    vi.setSystemTime(now)
    render(
      <LocationGrid
        locations={locations}
        candidateLocationIds={['loc-a']}
        occupancy={[]}
        players={players}
        myPlayerId="p1"
        currentTurnPlayerId="p1"
        currentTurnIndex={0}
        turnDeadlineAt={now + 3_000}
        serverOffsetMs={0}
        onSelectLocation={vi.fn()}
      />
    )

    const countdown = screen.getByTestId('turn-countdown')
    expect(countdown).toHaveClass('text-destructive')
    expect(countdown).toHaveClass('font-bold')
  })

  it('모든 차례 완료 — "이번 라운드 조사 완료" 표시', () => {
    render(
      <LocationGrid
        locations={locations}
        candidateLocationIds={[]}
        occupancy={[]}
        players={players}
        myPlayerId="p1"
        currentTurnPlayerId={null}
        currentTurnIndex={null}
        turnDeadlineAt={null}
        serverOffsetMs={0}
        onSelectLocation={vi.fn()}
      />
    )

    expect(screen.getByTestId('location-grid-complete')).toHaveTextContent('이번 라운드 조사 완료')
    expect(screen.queryByTestId('location-grid')).toBeNull()
  })

  it('마감 경과(0초) — 본인 차례 후보 버튼도 비활성화', () => {
    const now = Date.now()
    vi.setSystemTime(now)
    render(
      <LocationGrid
        locations={locations}
        candidateLocationIds={['loc-a', 'loc-b']}
        occupancy={[]}
        players={players}
        myPlayerId="p1"
        currentTurnPlayerId="p1"
        currentTurnIndex={0}
        turnDeadlineAt={now - 1_000}
        serverOffsetMs={0}
        onSelectLocation={vi.fn()}
      />
    )

    const cellA = screen.getByTestId('location-cell-loc-a')
    expect(cellA.tagName).toBe('BUTTON')
    expect(cellA).toBeDisabled()
  })
})
