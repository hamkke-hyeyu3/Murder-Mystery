import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { EndScreen } from './EndScreen'
import { useCardStore } from '@/stores/cardStore'
import { useSessionStore } from '@/stores/sessionStore'

beforeEach(() => {
  useCardStore.getState().reset()
  useSessionStore.getState().reset()
})

describe('EndScreen', () => {
  it('두 슬라이더가 분리 렌더링된다 (platform, work 각각)', () => {
    render(<EndScreen onSurveySubmit={vi.fn()} />)

    expect(screen.getByTestId('platform-score-slider')).toBeInTheDocument()
    expect(screen.getByTestId('work-score-slider')).toBeInTheDocument()
    // 두 슬라이더는 별개 DOM 요소여야 한다 (합쳐지면 안 됨 — 스펙 가드)
    expect(screen.getByTestId('platform-score-slider')).not.toBe(
      screen.getByTestId('work-score-slider')
    )
  })

  it('textarea는 maxLength=80이다', () => {
    render(<EndScreen onSurveySubmit={vi.fn()} />)

    const textarea = screen.getByTestId('free-text-input') as HTMLTextAreaElement
    expect(textarea.maxLength).toBe(80)
  })

  it('응답하기 클릭 시 onSurveySubmit 호출 후 SurveyCard가 사라진다', () => {
    const onSurveySubmit = vi.fn()
    render(<EndScreen onSurveySubmit={onSurveySubmit} />)

    fireEvent.click(screen.getByTestId('survey-respond-button'))

    expect(onSurveySubmit).toHaveBeenCalledTimes(1)
    expect(screen.queryByTestId('survey-card')).not.toBeInTheDocument()
    expect(screen.getByTestId('end-message')).toBeInTheDocument()
  })

  it('건너뛰기 클릭 시 onSurveySubmit(null, null, null) 호출 후 SurveyCard가 사라진다', () => {
    const onSurveySubmit = vi.fn()
    render(<EndScreen onSurveySubmit={onSurveySubmit} />)

    fireEvent.click(screen.getByTestId('survey-skip-button'))

    expect(onSurveySubmit).toHaveBeenCalledWith(null, null, null)
    expect(screen.queryByTestId('survey-card')).not.toBeInTheDocument()
  })

  it('mySurveyResponded=true이면 SurveyCard 미렌더 (재합류 케이스)', () => {
    useSessionStore.getState().setSession({ mySurveyResponded: true })

    render(<EndScreen onSurveySubmit={vi.fn()} />)

    expect(screen.queryByTestId('survey-card')).not.toBeInTheDocument()
    expect(screen.getByTestId('end-message')).toBeInTheDocument()
  })

  it('체크박스 미선택 상태에서 응답하기 클릭 시 score는 null로 전달된다', () => {
    const onSurveySubmit = vi.fn()
    render(<EndScreen onSurveySubmit={onSurveySubmit} />)

    // 체크박스 미선택 상태로 응답
    fireEvent.click(screen.getByTestId('survey-respond-button'))

    const [p, w] = onSurveySubmit.mock.calls[0] as [number | null, number | null, string | null]
    expect(p).toBeNull()
    expect(w).toBeNull()
  })

  it('platform 체크박스 선택 후 응답하기 클릭 시 platformScore 값 전달된다', () => {
    const onSurveySubmit = vi.fn()
    render(<EndScreen onSurveySubmit={onSurveySubmit} />)

    fireEvent.click(screen.getByTestId('platform-score-check'))
    fireEvent.click(screen.getByTestId('survey-respond-button'))

    const [p] = onSurveySubmit.mock.calls[0] as [number | null, number | null, string | null]
    expect(p).toBeGreaterThanOrEqual(1)
    expect(p).toBeLessThanOrEqual(5)
  })

  it('EndMessage가 항상 렌더링된다', () => {
    render(<EndScreen onSurveySubmit={vi.fn()} />)
    expect(screen.getByTestId('end-message')).toBeInTheDocument()
  })

  it('missions가 있으면 미션 한 줄 요약을 보여준다', () => {
    useCardStore.getState().setMissions([{ label: '진범 색출', description: '범인을 찾아라' }])

    render(<EndScreen onSurveySubmit={vi.fn()} />)

    expect(screen.getByTestId('mission-result-진범 색출')).toBeInTheDocument()
  })
})
