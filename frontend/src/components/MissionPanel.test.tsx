import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { MissionPanel } from './MissionPanel'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'

beforeEach(() => {
  useCardStore.getState().reset()
  useSessionStore.getState().reset()
})

describe('MissionPanel', () => {
  it('missions가 null이면 "미션 준비 중…" placeholder를 렌더링한다', () => {
    render(<MissionPanel onCheckComplete={vi.fn()} onForceProgress={vi.fn()} />)

    expect(screen.getByTestId('mission-panel')).toBeInTheDocument()
    expect(screen.getByTestId('mission-panel').textContent).toContain('미션 준비 중')
  })

  it('missions 배열의 각 label과 description이 렌더링된다', () => {
    useCardStore.getState().setMissions([
      { label: '진범 색출', description: '범인을 찾아라' },
      { label: '증거 확보', description: '증거를 모아라' },
    ])

    render(<MissionPanel onCheckComplete={vi.fn()} onForceProgress={vi.fn()} />)

    expect(screen.getByTestId('mission-panel')).toBeInTheDocument()
    expect(screen.getByTestId('mission-label-진범 색출').textContent).toContain('진범 색출')
    expect(screen.getByTestId('mission-panel').textContent).toContain('범인을 찾아라')
    expect(screen.getByTestId('mission-label-증거 확보').textContent).toContain('증거 확보')
    expect(screen.getByTestId('mission-panel').textContent).toContain('증거를 모아라')
  })

  it('missions가 있으면 "체크 완료" 버튼이 활성화 상태로 렌더링된다', () => {
    useCardStore.getState().setMissions([{ label: '미션', description: '설명' }])

    render(<MissionPanel onCheckComplete={vi.fn()} onForceProgress={vi.fn()} />)

    const button = screen.getByTestId('mission-check-button')
    expect(button).not.toBeDisabled()
    expect(button.textContent).toContain('체크 완료')
  })

  it('myMissionChecked가 true이면 버튼이 비활성화되고 ✓ 표시가 붙는다', () => {
    useCardStore.getState().setMissions([{ label: '미션', description: '설명' }])
    useSessionStore.getState().setSession({ myMissionChecked: true })

    render(<MissionPanel onCheckComplete={vi.fn()} onForceProgress={vi.fn()} />)

    const button = screen.getByTestId('mission-check-button')
    expect(button).toBeDisabled()
    expect(button.textContent).toContain('✓')
  })

  it('버튼 클릭 시 onCheckComplete가 호출된다', () => {
    useCardStore.getState().setMissions([{ label: '미션', description: '설명' }])
    const onCheckComplete = vi.fn()

    render(<MissionPanel onCheckComplete={onCheckComplete} onForceProgress={vi.fn()} />)
    fireEvent.click(screen.getByTestId('mission-check-button'))

    expect(onCheckComplete).toHaveBeenCalledOnce()
  })

  it('missionCheckedCount와 missionTotalCount가 있으면 "X / N 완료" 카운트를 표시한다', () => {
    useCardStore.getState().setMissions([{ label: '미션', description: '설명' }])
    useSessionStore.getState().setSession({ missionCheckedCount: 2, missionTotalCount: 3 })

    render(<MissionPanel onCheckComplete={vi.fn()} onForceProgress={vi.fn()} />)

    expect(screen.getByTestId('mission-check-count').textContent).toContain('2 / 3 완료')
  })

  // ── 강제 진행 노출/숨김 ────────────────────────────────────────────────────

  it('forceProgressAvailable이 null이면 강제 진행 버튼이 없다', () => {
    useCardStore.getState().setMissions([{ label: '미션', description: '설명' }])
    useSessionStore.getState().setSession({ forceProgressAvailable: null })

    render(<MissionPanel onCheckComplete={vi.fn()} onForceProgress={vi.fn()} />)

    expect(screen.queryByTestId('force-progress-button')).toBeNull()
  })

  it('scope=host + isHost=true이면 강제 진행 버튼이 노출된다', () => {
    useCardStore.getState().setMissions([{ label: '미션', description: '설명' }])
    useSessionStore.getState().setSession({ isHost: true, forceProgressAvailable: { scope: 'host' } })

    render(<MissionPanel onCheckComplete={vi.fn()} onForceProgress={vi.fn()} />)

    expect(screen.getByTestId('force-progress-button')).toBeInTheDocument()
  })

  it('scope=host + isHost=false이면 강제 진행 버튼이 숨겨진다', () => {
    useCardStore.getState().setMissions([{ label: '미션', description: '설명' }])
    useSessionStore.getState().setSession({ isHost: false, forceProgressAvailable: { scope: 'host' } })

    render(<MissionPanel onCheckComplete={vi.fn()} onForceProgress={vi.fn()} />)

    expect(screen.queryByTestId('force-progress-button')).toBeNull()
  })

  it('scope=all + isHost=false이면 강제 진행 버튼이 노출된다 (NB3)', () => {
    useCardStore.getState().setMissions([{ label: '미션', description: '설명' }])
    useSessionStore.getState().setSession({ isHost: false, forceProgressAvailable: { scope: 'all' } })

    render(<MissionPanel onCheckComplete={vi.fn()} onForceProgress={vi.fn()} />)

    expect(screen.getByTestId('force-progress-button')).toBeInTheDocument()
  })
})
