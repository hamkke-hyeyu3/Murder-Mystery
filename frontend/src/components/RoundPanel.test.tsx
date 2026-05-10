import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { RoundPanel } from './RoundPanel'

describe('RoundPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('라운드 번호를 표시한다', () => {
    const deadlineAt = Date.now() + 300_000
    render(
      <RoundPanel
        roundNumber={1}
        prompt="한 사람씩 자기 캐릭터를 짧게 소개해 주세요."
        commonHint={null}
        deadlineAt={deadlineAt}
        serverOffsetMs={0}
      />
    )
    expect(screen.getByTestId('round-number')).toHaveTextContent('라운드 1')
  })

  it('프롬프트를 표시한다', () => {
    const deadlineAt = Date.now() + 300_000
    render(
      <RoundPanel
        roundNumber={2}
        prompt="알리바이를 비교해 보세요."
        commonHint={null}
        deadlineAt={deadlineAt}
        serverOffsetMs={0}
      />
    )
    expect(screen.getByTestId('round-prompt')).toHaveTextContent('알리바이를 비교해 보세요.')
  })

  it('commonHint가 있으면 표시한다', () => {
    const deadlineAt = Date.now() + 300_000
    render(
      <RoundPanel
        roundNumber={2}
        prompt="알리바이를 비교해 보세요."
        commonHint="부검 결과 사망 추정 시각은 자정 전후 30분이다."
        deadlineAt={deadlineAt}
        serverOffsetMs={0}
      />
    )
    expect(screen.getByTestId('round-common-hint')).toHaveTextContent('부검 결과 사망 추정 시각은 자정 전후 30분이다.')
  })

  it('commonHint가 null이면 round-common-hint 요소가 없다', () => {
    const deadlineAt = Date.now() + 300_000
    render(
      <RoundPanel
        roundNumber={1}
        prompt="자기소개를 해주세요."
        commonHint={null}
        deadlineAt={deadlineAt}
        serverOffsetMs={0}
      />
    )
    expect(screen.queryByTestId('round-common-hint')).toBeNull()
  })

  it('카운트다운을 표시한다', () => {
    const now = Date.now()
    vi.setSystemTime(now)
    const deadlineAt = now + 300_000

    render(
      <RoundPanel
        roundNumber={1}
        prompt="자기소개를 해주세요."
        commonHint={null}
        deadlineAt={deadlineAt}
        serverOffsetMs={0}
      />
    )
    expect(screen.getByTestId('round-countdown')).toHaveTextContent('300초')
  })

  it('5초 이하이면 경고 스타일이 적용된다', () => {
    const now = Date.now()
    vi.setSystemTime(now)
    const deadlineAt = now + 3_000

    render(
      <RoundPanel
        roundNumber={1}
        prompt="자기소개를 해주세요."
        commonHint={null}
        deadlineAt={deadlineAt}
        serverOffsetMs={0}
      />
    )
    const countdown = screen.getByTestId('round-countdown')
    expect(countdown).toHaveClass('text-red-500')
    expect(countdown).toHaveAttribute('aria-live', 'assertive')
  })

  it('deadlineAt이 null이면 0초로 표시한다', () => {
    render(
      <RoundPanel
        roundNumber={1}
        prompt="자기소개를 해주세요."
        commonHint={null}
        deadlineAt={null}
        serverOffsetMs={0}
      />
    )
    expect(screen.getByTestId('round-countdown')).toHaveTextContent('0초')
  })
})
