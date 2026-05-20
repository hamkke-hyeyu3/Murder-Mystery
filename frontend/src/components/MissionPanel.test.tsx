import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { MissionPanel } from './MissionPanel'
import { useCardStore } from '@/stores/cardStore'

beforeEach(() => {
  useCardStore.getState().reset()
})

describe('MissionPanel', () => {
  it('missions가 null이면 "미션 준비 중…" placeholder를 렌더링한다', () => {
    render(<MissionPanel />)

    expect(screen.getByTestId('mission-panel')).toBeInTheDocument()
    expect(screen.getByTestId('mission-panel').textContent).toContain('미션 준비 중')
  })

  it('missions 배열의 각 label과 description이 렌더링된다', () => {
    useCardStore.getState().setMissions([
      { label: '진범 색출', description: '범인을 찾아라' },
      { label: '증거 확보', description: '증거를 모아라' },
    ])

    render(<MissionPanel />)

    expect(screen.getByTestId('mission-panel')).toBeInTheDocument()
    expect(screen.getByTestId('mission-label-진범 색출').textContent).toContain('진범 색출')
    expect(screen.getByTestId('mission-panel').textContent).toContain('범인을 찾아라')
    expect(screen.getByTestId('mission-label-증거 확보').textContent).toContain('증거 확보')
    expect(screen.getByTestId('mission-panel').textContent).toContain('증거를 모아라')
  })
})
